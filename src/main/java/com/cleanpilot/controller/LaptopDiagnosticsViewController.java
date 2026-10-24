package com.cleanpilot.controller;

import com.cleanpilot.model.*;
import com.cleanpilot.service.LaptopDiagnosticsService;
import com.cleanpilot.ui.FormatUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import jakarta.annotation.PreDestroy;

/**
 * Controller for the Laptop & System Diagnostics Health Hub view.
 */
@Component
public class LaptopDiagnosticsViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(LaptopDiagnosticsViewController.class);

    @FXML private CheckBox autoRefreshCheckBox;
    @FXML private Label overallScoreLabel;
    @FXML private Label gradeBadgeLabel;
    @FXML private VBox issuesContainer;

    // Battery
    @FXML private Label batteryStatusBadge;
    @FXML private Label batteryPercentLabel;
    @FXML private ProgressBar batteryProgressBar;
    @FXML private Label batteryRuntimeLabel;
    @FXML private Label batteryHealthLabel;
    @FXML private Label batteryCyclesLabel;
    @FXML private Label batteryPowerRateLabel;
    @FXML private Label batteryVoltageLabel;

    // Thermals
    @FXML private Label thermalStatusBadge;
    @FXML private Label cpuTempLabel;
    @FXML private ProgressBar cpuTempBar;
    @FXML private Label throttlingRiskLabel;
    @FXML private Label cpuVoltageLabel;
    @FXML private Label fanSpeedsLabel;
    @FXML private Label coolingAdviceLabel;

    // Storage
    @FXML private Label storageStatusBadge;
    @FXML private VBox storageListContainer;
    @FXML private VBox storageVolumesContainer;

    // Memory
    @FXML private Label memoryStatusBadge;
    @FXML private Label ramUsageLabel;
    @FXML private ProgressBar ramProgressBar;
    @FXML private Label commitUsageLabel;
    @FXML private ProgressBar commitProgressBar;
    @FXML private Label swapUsageLabel;
    @FXML private ProgressBar swapProgressBar;
    @FXML private Label kernelPoolsLabel;
    @FXML private Label kernelPoolBadge;
    @FXML private Label thrashingAlertLabel;

    // Operating System Stability
    @FXML private Label stabilityBadge;
    @FXML private Label uptimeLabel;
    @FXML private VBox crashesContainer;

    // Recommendations
    @FXML private VBox recommendationsContainer;

    private final LaptopDiagnosticsService diagnosticsService;
    private final ExecutorService executorService;
    private final MainViewController mainViewController;
    private Timeline autoRefreshTimeline;

    public LaptopDiagnosticsViewController(
            LaptopDiagnosticsService diagnosticsService,
            ExecutorService executorService,
            @Lazy MainViewController mainViewController
    ) {
        this.diagnosticsService = diagnosticsService;
        this.executorService = executorService;
        this.mainViewController = mainViewController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }

        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            if (autoRefreshCheckBox != null && autoRefreshCheckBox.isSelected()) {
                // Only poll diagnostics when view is attached to an active, showing window
                if (issuesContainer != null && issuesContainer.getScene() != null
                        && issuesContainer.getScene().getWindow() != null
                        && issuesContainer.getScene().getWindow().isShowing()) {
                    refreshDiagnostics();
                }
            }
        }));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);

        if (autoRefreshCheckBox != null) {
            autoRefreshCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
                if (newV) {
                    refreshDiagnostics();
                    if (autoRefreshTimeline != null) autoRefreshTimeline.play();
                } else {
                    if (autoRefreshTimeline != null) autoRefreshTimeline.stop();
                }
            });
            if (autoRefreshCheckBox.isSelected()) {
                autoRefreshTimeline.play();
            }
        }
    }

    @FXML
    public void refreshDiagnostics() {
        submitAsync(() -> {
            try {
                LaptopDiagnosticsReport report = diagnosticsService.generateReport();
                Platform.runLater(() -> renderReport(report));
            } catch (Exception e) {
                log.error("Failed to generate laptop diagnostics: {}", e.getMessage(), e);
            }
        });
    }

    @PreDestroy
    public void cleanup() {
        stopAutoRefresh();
    }

    public void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
    }

    private void submitAsync(Runnable task) {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            stopAutoRefresh();
            return;
        }
        try {
            executorService.submit(task);
        } catch (RejectedExecutionException ex) {
            stopAutoRefresh();
            log.debug("Diagnostics task rejected: executor is shut down.");
        }
    }

    private void renderReport(LaptopDiagnosticsReport report) {
        // Overall Health Score
        if (overallScoreLabel != null) {
            overallScoreLabel.setText(report.formattedScore());
            if (report.overallHealthScore() >= 85) {
                overallScoreLabel.getStyleClass().setAll("title-1", "text-success");
                gradeBadgeLabel.getStyleClass().setAll("badge", "success");
            } else if (report.overallHealthScore() >= 65) {
                overallScoreLabel.getStyleClass().setAll("title-1", "text-warning");
                gradeBadgeLabel.getStyleClass().setAll("badge", "warning");
            } else {
                overallScoreLabel.getStyleClass().setAll("title-1", "text-danger");
                gradeBadgeLabel.getStyleClass().setAll("badge", "danger");
            }
            gradeBadgeLabel.setText(report.scoreGrade());
        }

        // Issues Container
        if (issuesContainer != null) {
            issuesContainer.getChildren().clear();
            if (report.detectedIssues().isEmpty()) {
                Label fineLabel = new Label("✔ All battery, cooling, volume capacity, memory pools, and OS subsystems operating nominally.");
                fineLabel.getStyleClass().addAll("text-small", "text-success");
                issuesContainer.getChildren().add(fineLabel);
            } else {
                for (String issue : report.detectedIssues()) {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    Label bullet = new Label("•");
                    bullet.getStyleClass().addAll("text-bold", "text-warning");
                    Label txt = new Label(issue);
                    txt.getStyleClass().add("text-small");
                    row.getChildren().addAll(bullet, txt);
                    issuesContainer.getChildren().add(row);
                }
            }
        }

        // Subsystem 1: Battery
        BatteryHealthInfo bat = report.battery();
        if (batteryPercentLabel != null) {
            batteryPercentLabel.setText(bat.formattedRemaining());
            batteryProgressBar.setProgress(Math.max(0.01, bat.remainingPercent() / 100.0));
            batteryRuntimeLabel.setText(bat.formattedTimeRemaining());
            batteryHealthLabel.setText(bat.formattedHealth() + " (" + bat.healthStatus() + ")");
            batteryCyclesLabel.setText(bat.formattedCycleCount());
            batteryPowerRateLabel.setText(bat.formattedPowerRate() + (bat.isCharging() ? " (Charging)" : (bat.isDischarging() ? " (Discharging)" : "")));
            batteryVoltageLabel.setText(bat.formattedVoltage() + " • " + bat.chemistry());

            batteryStatusBadge.setText(bat.isPluggedIn() ? (bat.isCharging() ? "Charging" : "AC Connected") : "On Battery");
            batteryStatusBadge.getStyleClass().setAll("badge", bat.isPluggedIn() ? "success" : "badge-subtle");
        }

        // Subsystem 2: Thermals
        ThermalMetricsInfo th = report.thermals();
        if (cpuTempLabel != null) {
            cpuTempLabel.setText(th.formattedCpuTemp());
            double tempProgress = th.cpuTemperatureCelsius() > 0 ? Math.min(1.0, th.cpuTemperatureCelsius() / 100.0) : 0.45;
            cpuTempBar.setProgress(tempProgress);

            if (th.isThermalThrottlingRisk()) {
                cpuTempBar.getStyleClass().setAll("progress-bar", "danger");
                thermalStatusBadge.getStyleClass().setAll("badge", "danger");
                throttlingRiskLabel.setText("⚠ Thermal throttling detected! CPU clock downclocked to protect silicon.");
                throttlingRiskLabel.getStyleClass().setAll("text-caption", "text-danger");
                coolingAdviceLabel.setText("Clean dust vents or elevate laptop immediately");
            } else if (th.cpuTemperatureCelsius() >= 75.0) {
                cpuTempBar.getStyleClass().setAll("progress-bar", "warning");
                thermalStatusBadge.getStyleClass().setAll("badge", "warning");
                throttlingRiskLabel.setText("Elevated thermal load under continuous operation.");
                throttlingRiskLabel.getStyleClass().setAll("text-caption", "text-warning");
                coolingAdviceLabel.setText("Ensure adequate surface ventilation");
            } else {
                cpuTempBar.getStyleClass().setAll("progress-bar", "success");
                thermalStatusBadge.getStyleClass().setAll("badge", "success");
                throttlingRiskLabel.setText("✔ No thermal downclocking / throttling detected.");
                throttlingRiskLabel.getStyleClass().setAll("text-caption", "text-success");
                coolingAdviceLabel.setText("Air vents clear, operating normally");
            }
            thermalStatusBadge.setText(th.thermalStatus());
            cpuVoltageLabel.setText(th.formattedVoltage());
            fanSpeedsLabel.setText(th.formattedFanSpeeds());
        }

        // Subsystem 3: Storage Volumes & Physical Drives
        if (storageVolumesContainer != null) {
            storageVolumesContainer.getChildren().clear();
            if (report.storageVolumes().isEmpty()) {
                Label noVols = new Label("No logical partitions found");
                noVols.getStyleClass().add("text-subtle");
                storageVolumesContainer.getChildren().add(noVols);
            } else {
                for (StorageVolumeHealthInfo vol : report.storageVolumes()) {
                    VBox vBox = new VBox(4);
                    vBox.setPadding(new Insets(6, 10, 6, 10));
                    vBox.getStyleClass().addAll("card");

                    HBox line1 = new HBox(8);
                    line1.setAlignment(Pos.CENTER_LEFT);
                    Label vName = new Label(vol.mountPoint() + " [" + vol.fileSystemType() + "] " + vol.volumeLabel());
                    vName.getStyleClass().add("text-bold");
                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    Label vBadge = new Label(vol.statusMessage());
                    if (vol.isCriticalSpaceAlert()) {
                        vBadge.getStyleClass().addAll("badge", "danger");
                    } else if (vol.isLowSpaceAlert()) {
                        vBadge.getStyleClass().addAll("badge", "warning");
                    } else {
                        vBadge.getStyleClass().addAll("badge", "success");
                    }
                    line1.getChildren().addAll(vName, sp, vBadge);

                    ProgressBar pBar = new ProgressBar(Math.max(0.01, vol.usedPercent() / 100.0));
                    pBar.setMaxWidth(Double.MAX_VALUE);
                    if (vol.isCriticalSpaceAlert()) {
                        pBar.getStyleClass().add("danger");
                    } else if (vol.isLowSpaceAlert()) {
                        pBar.getStyleClass().add("warning");
                    } else {
                        pBar.getStyleClass().add("success");
                    }

                    Label line2 = new Label("Used: " + vol.formattedUsed() + " | Free: " + vol.formattedFree());
                    line2.getStyleClass().add("text-small");

                    vBox.getChildren().addAll(line1, pBar, line2);
                    storageVolumesContainer.getChildren().add(vBox);
                }
            }
        }

        if (storageListContainer != null) {
            storageListContainer.getChildren().clear();
            if (report.storageDrives().isEmpty()) {
                Label noDrives = new Label("No physical drives detected");
                noDrives.getStyleClass().add("text-subtle");
                storageListContainer.getChildren().add(noDrives);
            } else {
                for (StorageHealthInfo d : report.storageDrives()) {
                    VBox driveBox = new VBox(4);
                    driveBox.setPadding(new Insets(6, 10, 6, 10));
                    driveBox.getStyleClass().addAll("card");

                    HBox line1 = new HBox(8);
                    line1.setAlignment(Pos.CENTER_LEFT);
                    Label dName = new Label(d.model() + " (" + d.formattedSize() + ")");
                    dName.getStyleClass().add("text-bold");
                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);
                    Label badge = new Label(d.healthStatus());
                    badge.getStyleClass().addAll("badge", d.isQueueBottleneck() ? "warning" : "success");
                    line1.getChildren().addAll(dName, sp, badge);

                    HBox line2 = new HBox(12);
                    Label transfer = new Label("Read: " + d.formattedBytesRead() + " | Written: " + d.formattedBytesWritten());
                    transfer.getStyleClass().add("text-subtle");
                    Label queue = new Label("Queue Depth: " + d.currentQueueLength());
                    queue.getStyleClass().addAll("text-small", d.isQueueBottleneck() ? "text-warning" : "text-subtle");
                    line2.getChildren().addAll(transfer, queue);

                    driveBox.getChildren().addAll(line1, line2);
                    storageListContainer.getChildren().add(driveBox);
                }
            }
        }

        // Subsystem 4: Advanced Memory & Kernel Pools
        AdvancedMemoryHealthInfo advMem = report.advancedMemory();
        if (ramUsageLabel != null) {
            ramUsageLabel.setText(advMem.formattedPhysicalUsed() + " (" + advMem.formattedPhysicalPercent() + ")");
            ramProgressBar.setProgress(Math.max(0.01, advMem.physicalUsedPercent() / 100.0));

            if (commitUsageLabel != null) {
                commitUsageLabel.setText(advMem.formattedCommit());
                commitProgressBar.setProgress(Math.max(0.01, advMem.commitUsedPercent() / 100.0));
                if (advMem.isCommitSaturationRisk()) {
                    commitProgressBar.getStyleClass().setAll("progress-bar", "danger");
                } else if (advMem.commitUsedPercent() >= 75.0) {
                    commitProgressBar.getStyleClass().setAll("progress-bar", "warning");
                } else {
                    commitProgressBar.getStyleClass().setAll("progress-bar", "success");
                }
            }

            if (swapUsageLabel != null) {
                swapUsageLabel.setText(FormatUtils.formatBytes(advMem.swapUsedBytes()) + " / " + FormatUtils.formatBytes(advMem.swapTotalBytes()));
                swapProgressBar.setProgress(Math.max(0.01, advMem.swapUsedPercent() / 100.0));
            }

            if (kernelPoolsLabel != null) {
                kernelPoolsLabel.setText("Non-Paged: " + advMem.formattedNonPagedPool() + " | Paged: " + advMem.formattedPagedPool());
            }

            if (kernelPoolBadge != null) {
                if (advMem.isNonPagedPoolLeak()) {
                    kernelPoolBadge.setText("⚠ Driver Memory Leak (>1.2GB)");
                    kernelPoolBadge.getStyleClass().setAll("badge", "danger");
                } else {
                    kernelPoolBadge.setText("Driver Pools Healthy");
                    kernelPoolBadge.getStyleClass().setAll("badge", "success");
                }
            }

            if (advMem.isThrashingDetected()) {
                thrashingAlertLabel.setText("⚠ Memory thrashing alert: High swap paging may freeze UI");
                thrashingAlertLabel.getStyleClass().setAll("text-small", "text-danger");
                memoryStatusBadge.getStyleClass().setAll("badge", "danger");
            } else if (advMem.physicalUsedPercent() >= 85.0) {
                thrashingAlertLabel.setText("Heavy RAM allocation. Freeing background memory recommended.");
                thrashingAlertLabel.getStyleClass().setAll("text-small", "text-warning");
                memoryStatusBadge.getStyleClass().setAll("badge", "warning");
            } else {
                thrashingAlertLabel.setText("✔ Optimal memory paging, no thrashing detected");
                thrashingAlertLabel.getStyleClass().setAll("text-small", "text-success");
                memoryStatusBadge.getStyleClass().setAll("badge", "success");
            }
            memoryStatusBadge.setText(advMem.memoryStatus());
        }

        // Subsystem 5: Stability & Event Log Crashes
        SystemStabilityReport stab = report.stability();
        if (uptimeLabel != null && stab != null) {
            uptimeLabel.setText(stab.formattedUptime() + (stab.isUptimeExcessive() ? " (Fast-Startup Bloat - Reboot Recommended)" : " (Clean Kernel Uptime)"));
            stabilityBadge.setText(stab.stabilityGrade());
            if (stab.hasRecentCrashes()) {
                stabilityBadge.getStyleClass().setAll("badge", "danger");
            } else if (stab.isUptimeExcessive()) {
                stabilityBadge.getStyleClass().setAll("badge", "warning");
            } else {
                stabilityBadge.getStyleClass().setAll("badge", "success");
            }

            if (crashesContainer != null) {
                crashesContainer.getChildren().clear();
                if (stab.recentCrashEvents().isEmpty()) {
                    Label noCrashes = new Label("✔ Zero unexpected BSODs or dirty shutdowns recorded in the past 14 days.");
                    noCrashes.getStyleClass().addAll("text-small", "text-success");
                    crashesContainer.getChildren().add(noCrashes);
                } else {
                    for (SystemStabilityEvent ev : stab.recentCrashEvents()) {
                        HBox evRow = new HBox(8);
                        evRow.setAlignment(Pos.CENTER_LEFT);
                        Label b = new Label("⚠");
                        Label d = new Label(ev.timestamp().toString() + " • " + ev.eventType() + " (" + ev.source() + "): " + ev.description());
                        d.getStyleClass().addAll("text-small", "text-danger");
                        evRow.getChildren().addAll(b, d);
                        crashesContainer.getChildren().add(evRow);
                    }
                }
            }
        }

        // Recommendations
        if (recommendationsContainer != null) {
            recommendationsContainer.getChildren().clear();
            for (String rec : report.recommendations()) {
                HBox rBox = new HBox(8);
                rBox.setAlignment(Pos.CENTER_LEFT);
                Label bullet = new Label("💡");
                Label rText = new Label(rec);
                rText.getStyleClass().add("text-small");
                rBox.getChildren().addAll(bullet, rText);
                recommendationsContainer.getChildren().add(rBox);
            }
        }
    }

    @FXML
    public void onOpenResourceMonitor() {
        if (mainViewController != null) {
            mainViewController.showProcesses();
        }
    }

    @FXML
    public void onNavigateLargeFiles() {
        if (mainViewController != null) {
            mainViewController.showLargeFiles();
        }
    }

    @FXML
    public void onNavigateDuplicates() {
        if (mainViewController != null) {
            mainViewController.showDuplicates();
        }
    }

    @FXML
    public void onNavigateRecuperate() {
        if (mainViewController != null) {
            mainViewController.showRecuperate();
        }
    }

    @FXML
    public void onNavigateDrivers() {
        if (mainViewController != null) {
            mainViewController.showDrivers();
        }
    }

    @FXML
    public void onExportScorecard() {
        submitAsync(() -> {
            try {
                LaptopDiagnosticsReport rep = diagnosticsService.generateReport();
                String userHome = System.getProperty("user.home", ".");
                java.nio.file.Path exportPath = java.nio.file.Paths.get(userHome, "CleanPilot_System_Health_Report.md");

                StringBuilder sb = new StringBuilder();
                sb.append("# CleanPilot Complete System, RAM & Storage Diagnostics\n\n");
                sb.append("**Generated At:** ").append(java.time.LocalDateTime.now()).append("\n");
                sb.append("**Overall Health Score:** ").append(rep.overallHealthScore()).append("/100 (").append(rep.scoreGrade()).append(")\n\n");

                sb.append("## Subsystem 1: Battery & Power Draw\n");
                sb.append("- Status: ").append(rep.battery().healthStatus()).append("\n");
                sb.append("- Remaining Charge: ").append(rep.battery().formattedRemaining()).append("\n");
                sb.append("- Health Capacity: ").append(rep.battery().formattedHealth()).append("\n");
                sb.append("- Power Rate: ").append(rep.battery().formattedPowerRate()).append("\n\n");

                sb.append("## Subsystem 2: CPU Thermals & Throttling\n");
                sb.append("- CPU Temperature: ").append(rep.thermals().formattedCpuTemp()).append("\n");
                sb.append("- Throttling Risk: ").append(rep.thermals().isThermalThrottlingRisk() ? "HIGH RISK" : "Normal").append("\n");
                sb.append("- Fan Telemetry: ").append(rep.thermals().formattedFanSpeeds()).append("\n\n");

                sb.append("## Subsystem 3: Storage Volumes & Physical Drives\n");
                for (StorageVolumeHealthInfo vol : rep.storageVolumes()) {
                    sb.append("- Partition ").append(vol.mountPoint()).append(" [").append(vol.fileSystemType()).append("]: ")
                      .append(vol.formattedFree()).append(" (Status: ").append(vol.statusMessage()).append(")\n");
                }
                for (StorageHealthInfo s : rep.storageDrives()) {
                    sb.append("- Physical Drive: ").append(s.model()).append(" | Size: ").append(s.formattedSize())
                      .append(" | Queue Depth: ").append(s.currentQueueLength())
                      .append(" | Health: ").append(s.healthStatus()).append("\n");
                }

                sb.append("\n## Subsystem 4: Advanced Memory, Commit Limit & Kernel Pools\n");
                sb.append("- Physical RAM Used: ").append(rep.advancedMemory().formattedPhysicalUsed()).append(" (").append(rep.advancedMemory().formattedPhysicalPercent()).append(")\n");
                sb.append("- Commit Limit: ").append(rep.advancedMemory().formattedCommit()).append("\n");
                sb.append("- Kernel Non-Paged Pool: ").append(rep.advancedMemory().formattedNonPagedPool()).append(" (Driver Leak: ").append(rep.advancedMemory().isNonPagedPoolLeak()).append(")\n");
                sb.append("- Kernel Paged Pool: ").append(rep.advancedMemory().formattedPagedPool()).append("\n");
                sb.append("- Swap Virtual Memory: ").append(FormatUtils.formatBytes(rep.advancedMemory().swapUsedBytes())).append(" / ").append(FormatUtils.formatBytes(rep.advancedMemory().swapTotalBytes())).append("\n\n");

                sb.append("## Subsystem 5: Operating System Stability & Crash History\n");
                sb.append("- Continuous Uptime: ").append(rep.stability().formattedUptime()).append("\n");
                sb.append("- Fast-Startup Bloat: ").append(rep.stability().isUptimeExcessive()).append("\n");
                sb.append("- Recent Crashes (Last 14 Days): ").append(rep.stability().totalCrashesLast14Days()).append("\n\n");

                sb.append("## Actionable Health Recommendations\n");
                for (String rec : rep.recommendations()) {
                    sb.append("- 💡 ").append(rec).append("\n");
                }

                java.nio.file.Files.writeString(exportPath, sb.toString());

                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Report Exported");
                    alert.setHeaderText("Health Scorecard Saved");
                    alert.setContentText("The report was written to:\n" + exportPath.toAbsolutePath());
                    alert.showAndWait();
                });
            } catch (Exception ex) {
                log.error("Failed to export diagnostics scorecard: {}", ex.getMessage(), ex);
            }
        });
    }
}
