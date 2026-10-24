package com.cleanpilot.service;

import com.cleanpilot.ui.FormatUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.awt.TrayIcon;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time storage sentinel that monitors dynamic directories (%TEMP%, Downloads, CrashDumps)
 * using Java NIO WatchService to detect runaway disk bloat or giant dumps before the drive fills up.
 */
@Service
public class StorageSentinelService {

    private static final Logger log = LoggerFactory.getLogger(StorageSentinelService.class);

    private final SystemTrayManagerService trayManagerService;
    private final UserPreferencesService preferencesService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private WatchService watchService;

    // Rolling 15-minute ingestion buffer: list of (epochMillis, bytesAdded)
    private record IngestionSample(long timestamp, long bytes) {}
    private final ConcurrentLinkedDeque<IngestionSample> rollingIngestion = new ConcurrentLinkedDeque<>();
    private final AtomicLong lastAlertTime = new AtomicLong(0);

    // 5 GB threshold within 15 minutes, or single file > 3 GB
    private static final long RAPID_BLOAT_THRESHOLD = 5L * 1024 * 1024 * 1024;
    private static final long SINGLE_LARGE_FILE_THRESHOLD = 3L * 1024 * 1024 * 1024;
    private static final long ALERT_COOLDOWN_MS = 20L * 60 * 1000; // 20 minutes

    public StorageSentinelService(@Lazy SystemTrayManagerService trayManagerService, UserPreferencesService preferencesService) {
        this.trayManagerService = trayManagerService;
        this.preferencesService = preferencesService;
    }

    @PostConstruct
    public void startSentinel() {
        if (preferencesService == null || !preferencesService.isStorageSentinelEnabled()) {
            log.info("StorageSentinelService real-time file watcher is DISABLED by default to protect system resources.");
            return;
        }
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.running.set(true);

            List<Path> watchedPaths = resolveWatchedDirectories();
            int registeredCount = 0;

            for (Path dir : watchedPaths) {
                if (Files.exists(dir) && Files.isDirectory(dir)) {
                    try {
                        dir.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY
                        );
                        registeredCount++;
                    } catch (Exception ex) {
                        log.debug("Could not register WatchService for {}: {}", dir, ex.getMessage());
                    }
                }
            }

            if (registeredCount > 0) {
                // Launch background virtual thread listener
                Thread.ofVirtual().name("cleanpilot-storage-sentinel").start(this::watchLoop);
                log.info("StorageSentinelService started successfully watching {} dynamic directories.", registeredCount);
            }
        } catch (Exception ex) {
            log.warn("StorageSentinelService could not initialize: {}", ex.getMessage());
        }
    }

    private List<Path> resolveWatchedDirectories() {
        List<Path> paths = new ArrayList<>();

        // 1. System Temp
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null) paths.add(Paths.get(tmp));

        // 2. User Downloads
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            paths.add(Paths.get(userHome, "Downloads"));
        }

        // 3. Local AppData CrashDumps
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            paths.add(Paths.get(localAppData, "CrashDumps"));
        }

        return paths;
    }

    private void watchLoop() {
        while (running.get() && watchService != null) {
            try {
                WatchKey key = watchService.poll(5, TimeUnit.SECONDS);
                if (key == null) {
                    pruneOldSamples();
                    continue;
                }

                Path dir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path fullPath = dir.resolve(filename);

                    if (Files.exists(fullPath) && Files.isRegularFile(fullPath)) {
                        try {
                            long size = Files.size(fullPath);
                            long now = System.currentTimeMillis();

                            // Track in rolling buffer
                            rollingIngestion.add(new IngestionSample(now, size));

                            // Check 1: Giant single file burst (>3 GB)
                            if (size >= SINGLE_LARGE_FILE_THRESHOLD) {
                                triggerAlert("Giant File Written",
                                        "A large file (" + FormatUtils.formatBytes(size) + ") was just created: " + filename + ". Check CleanPilot to verify disk headroom.");
                            }
                        } catch (Exception ignored) {}
                    }
                }

                pruneOldSamples();
                checkRollingThreshold();

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (Exception ex) {
                log.debug("Sentinel watch loop exception: {}", ex.getMessage());
            }
        }
    }

    private void pruneOldSamples() {
        long cutoff = System.currentTimeMillis() - (15 * 60 * 1000); // 15 mins
        while (!rollingIngestion.isEmpty() && rollingIngestion.peek().timestamp() < cutoff) {
            rollingIngestion.poll();
        }
    }

    private void checkRollingThreshold() {
        long now = System.currentTimeMillis();
        long totalRecentBytes = 0;
        for (IngestionSample sample : rollingIngestion) {
            totalRecentBytes += sample.bytes();
        }

        if (totalRecentBytes >= RAPID_BLOAT_THRESHOLD && (now - lastAlertTime.get() > ALERT_COOLDOWN_MS)) {
            triggerAlert("Rapid Disk Ingestion Alert",
                    "Over " + FormatUtils.formatBytes(totalRecentBytes) + " written to Temp/Downloads in the last 15 minutes! Open CleanPilot to inspect potential runaway processes.");
        }
    }

    private void triggerAlert(String title, String message) {
        lastAlertTime.set(System.currentTimeMillis());
        log.warn("Storage Sentinel Alert: {} - {}", title, message);
        if (trayManagerService != null) {
            trayManagerService.showNotification(title, message, TrayIcon.MessageType.WARNING);
        }
    }

    @PreDestroy
    public void stopSentinel() {
        running.set(false);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
    }
}
