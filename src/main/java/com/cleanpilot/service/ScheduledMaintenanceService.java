package com.cleanpilot.service;

import com.cleanpilot.model.SmartCleanResult;
import com.cleanpilot.ui.FormatUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.awt.TrayIcon;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * Automates background maintenance, executing Smart Clean, quarantine auto-purge,
 * and memory optimization based on user scheduling preferences.
 */
@Service
public class ScheduledMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledMaintenanceService.class);

    private final SmartCleanerService smartCleanerService;
    private final QuarantineService quarantineService;
    private final UserPreferencesService preferencesService;
    private final SystemTrayManagerService trayManagerService;

    private static final String PREF_LAST_RUN_TIMESTAMP = "cleanpilot.last_maintenance_run";
    private final Preferences prefs = Preferences.userNodeForPackage(ScheduledMaintenanceService.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cleanpilot-maintenance-scheduler");
        t.setDaemon(true);
        return t;
    });

    public ScheduledMaintenanceService(
            @Lazy SmartCleanerService smartCleanerService,
            @Lazy QuarantineService quarantineService,
            UserPreferencesService preferencesService,
            @Lazy SystemTrayManagerService trayManagerService
    ) {
        this.smartCleanerService = smartCleanerService;
        this.quarantineService = quarantineService;
        this.preferencesService = preferencesService;
        this.trayManagerService = trayManagerService;
    }

    @PostConstruct
    public void startScheduler() {
        String schedule = preferencesService != null ? preferencesService.getAutoMaintenanceSchedule() : "DISABLED";
        if ("DISABLED".equalsIgnoreCase(schedule)) {
            log.info("ScheduledMaintenanceService is DISABLED by default to protect system resources.");
            return;
        }
        // Check every 4 hours if maintenance is due
        scheduler.scheduleWithFixedDelay(this::checkAndRunScheduledMaintenance, 60, 4 * 3600, TimeUnit.SECONDS);
        log.info("ScheduledMaintenanceService initialized with schedule: {}", schedule);
    }

    private void checkAndRunScheduledMaintenance() {
        String schedule = preferencesService.getAutoMaintenanceSchedule();
        if ("DISABLED".equalsIgnoreCase(schedule)) return;

        long lastRun = prefs.getLong(PREF_LAST_RUN_TIMESTAMP, 0);
        long now = System.currentTimeMillis();

        long intervalMillis = "DAILY".equalsIgnoreCase(schedule)
                ? 24L * 3600 * 1000
                : 7L * 24L * 3600 * 1000; // WEEKLY default

        if (now - lastRun >= intervalMillis) {
            log.info("Executing scheduled background system maintenance (schedule: {})...", schedule);
            executeMaintenance(true);
            prefs.putLong(PREF_LAST_RUN_TIMESTAMP, now);
        }
    }

    /**
     * Executes maintenance immediately (callable from UI or scheduled background task).
     */
    public SmartCleanResult executeMaintenance(boolean silent) {
        SmartCleanResult result = smartCleanerService.performSmartClean((task, pct) -> {});

        int purgedQuarantine = 0;
        if (preferencesService.isAutoPurgeQuarantine()) {
            purgedQuarantine = quarantineService.purgeExpired();
        }

        prefs.putLong(PREF_LAST_RUN_TIMESTAMP, System.currentTimeMillis());

        if (silent && preferencesService.isMaintenanceNotifications() && trayManagerService != null) {
            String msg = String.format(
                    "Reclaimed %s across %d files and purged %d expired quarantine items.",
                    FormatUtils.formatBytes(result.totalBytesReclaimed()),
                    result.totalFilesCleaned(),
                    purgedQuarantine
            );
            trayManagerService.showNotification("Automated Maintenance Complete", msg, TrayIcon.MessageType.INFO);
        }

        return result;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
