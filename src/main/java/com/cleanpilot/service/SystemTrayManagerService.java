package com.cleanpilot.service;

import com.cleanpilot.model.LaptopDiagnosticsReport;
import com.cleanpilot.model.StorageVolumeHealthInfo;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the native Windows System Tray integration, background monitoring,
 * minimize-to-tray handling, and proactive hardware/storage alerts.
 */
@Service
public class SystemTrayManagerService {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManagerService.class);

    private final LaptopDiagnosticsService diagnosticsService;
    private final SmartCleanerService smartCleanerService;

    private TrayIcon trayIcon;
    private Stage primaryStage;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cleanpilot-tray-monitor");
        t.setDaemon(true);
        return t;
    });

    private final AtomicLong lastStorageAlertTime = new AtomicLong(0);
    private final AtomicLong lastDriverAlertTime = new AtomicLong(0);

    public SystemTrayManagerService(
            LaptopDiagnosticsService diagnosticsService,
            @Lazy SmartCleanerService smartCleanerService
    ) {
        this.diagnosticsService = diagnosticsService;
        this.smartCleanerService = smartCleanerService;
    }

    /**
     * Initializes the native System Tray icon, menu, and background alert monitor.
     */
    public void setupTray(Stage stage) {
        this.primaryStage = stage;

        if (!SystemTray.isSupported()) {
            log.info("SystemTray is not supported on this operating system.");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image iconImage = createTrayIconImage();

            PopupMenu popup = new PopupMenu();

            MenuItem openItem = new MenuItem("Open CleanPilot");
            openItem.addActionListener(e -> Platform.runLater(this::showMainWindow));

            MenuItem cleanItem = new MenuItem("⚡ Run 1-Click Smart Clean");
            cleanItem.addActionListener(e -> Platform.runLater(() -> {
                showMainWindow();
                smartCleanerService.performSmartClean((task, pct) -> {});
            }));

            MenuItem diagItem = new MenuItem("💻 System Health Diagnostics");
            diagItem.addActionListener(e -> Platform.runLater(this::showMainWindow));

            MenuItem exitItem = new MenuItem("Exit CleanPilot");
            exitItem.addActionListener(e -> {
                Platform.exit();
                System.exit(0);
            });

            popup.add(openItem);
            popup.add(cleanItem);
            popup.add(diagItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon = new TrayIcon(iconImage, "CleanPilot - Disk Analyzer & System Hub", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(this::showMainWindow));

            tray.add(trayIcon);
            log.info("System Tray icon registered successfully.");

            // Start background alert monitoring every 60 seconds
            startBackgroundMonitoring();

        } catch (Exception e) {
            log.warn("Failed to initialize system tray: {}", e.getMessage());
        }
    }

    private void showMainWindow() {
        if (primaryStage != null) {
            primaryStage.show();
            primaryStage.setIconified(false);
            primaryStage.toFront();
        }
    }

    private Image createTrayIconImage() {
        try {
            java.io.InputStream stream = getClass().getResourceAsStream("/icons/cleanpilot-16x16.png");
            if (stream != null) {
                try (stream) {
                    return javax.imageio.ImageIO.read(stream);
                }
            }
        } catch (Exception e) {
            log.debug("Falling back to programmatically drawn tray icon: {}", e.getMessage());
        }

        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Circular background with CleanPilot theme blue
        g2.setColor(new Color(31, 111, 235));
        g2.fillOval(1, 1, 14, 14);

        // White "C" monogram
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.drawString("C", 4, 12);
        g2.dispose();
        return image;
    }

    private void startBackgroundMonitoring() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                LaptopDiagnosticsReport report = diagnosticsService.generateReport();
                long now = Instant.now().toEpochMilli();

                // Alert 1: Low Volume Space (<15%) - at most once every 30 minutes
                for (StorageVolumeHealthInfo vol : report.storageVolumes()) {
                    if (vol.isLowSpaceAlert() && (now - lastStorageAlertTime.get() > 30 * 60 * 1000L)) {
                        lastStorageAlertTime.set(now);
                        showNotification(
                                "Low Storage Warning",
                                "Partition " + vol.mountPoint() + " is running low on space (" + vol.formattedFree() + "). Run CleanPilot to free disk space.",
                                TrayIcon.MessageType.WARNING
                        );
                        break;
                    }
                }

                // Alert 2: Driver Kernel Non-Paged Pool Leak (>1.2GB) - at most once every 30 minutes
                if (report.advancedMemory() != null && report.advancedMemory().isNonPagedPoolLeak()
                        && (now - lastDriverAlertTime.get() > 30 * 60 * 1000L)) {
                    lastDriverAlertTime.set(now);
                    showNotification(
                            "Driver Memory Leak Alert",
                            "Kernel Non-Paged pool is unusually high (" + report.advancedMemory().formattedNonPagedPool() + "). A hardware driver may be leaking RAM.",
                            TrayIcon.MessageType.WARNING
                    );
                }

            } catch (Exception ex) {
                log.debug("Tray monitoring cycle exception: {}", ex.getMessage());
            }
        }, 30, 60, TimeUnit.SECONDS);
    }

    public void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
    }
}
