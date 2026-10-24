package com.cleanpilot.ui;

import com.cleanpilot.model.DetailedProcessInfo;
import com.cleanpilot.service.ProcessMonitoringService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Advanced modal inspector for running processes, providing authentic telemetry:
 * paths, command line arguments, parent/child relationships, CPU user/kernel breakdown,
 * physical vs virtual memory, I/O rates, open file descriptors, and safety classifications.
 */
public class ProcessDetailsDialog extends Stage {

    private static final Logger log = LoggerFactory.getLogger(ProcessDetailsDialog.class);

    private final ProcessMonitoringService processService;
    private final ExecutorService executorService;
    private int currentPid;

    // UI Nodes updated dynamically on refresh
    private final Label titleLabel = new Label("Process Inspector");
    private final HBox badgesBox = new HBox(8);
    private final VBox alertBox = new VBox(6);

    // Tab 1: Identity & Execution
    private final TextField pathField = new TextField();
    private final TextArea commandLineArea = new TextArea();
    private final TextField cwdField = new TextField();
    private final Label userLabel = new Label("-");
    private final Label groupLabel = new Label("-");
    private final Label startedLabel = new Label("-");
    private final Label uptimeLabel = new Label("-");
    private final FlowPane childPidsPane = new FlowPane(6, 6);

    // Tab 2: CPU & Scheduling
    private final Label cpuValueLabel = new Label("0.0%");
    private final ProgressBar cpuBar = new ProgressBar(0);
    private final Label userTimeLabel = new Label("-");
    private final Label kernelTimeLabel = new Label("-");
    private final Label totalCpuTimeLabel = new Label("-");
    private final Label threadsLabel = new Label("-");
    private final Label priorityLabel = new Label("-");

    // Tab 3: Memory & I/O
    private final Label rssLabel = new Label("0 B");
    private final Label vszLabel = new Label("0 B");
    private final Label minorFaultsLabel = new Label("0");
    private final Label majorFaultsLabel = new Label("0");
    private final Label totalIoLabel = new Label("0 B");
    private final Label readIoLabel = new Label("0 B");
    private final Label writeIoLabel = new Label("0 B");
    private final Label openFilesLabel = new Label("-");

    // Action buttons
    private final Button refreshBtn = new Button("🔄 Refresh Metrics");
    private final Button openFolderBtn = new Button("📁 Open Folder");
    private final Button copyReportBtn = new Button("📋 Copy Full Report");
    private final Button terminateBtn = new Button("🛑 Terminate Process");

    private DetailedProcessInfo currentDetails;

    public ProcessDetailsDialog(
            Window owner,
            int initialPid,
            ProcessMonitoringService processService,
            ExecutorService executorService
    ) {
        this.processService = processService;
        this.executorService = executorService;
        this.currentPid = initialPid;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("CleanPilot - Process Inspector (PID " + initialPid + ")");
        setMinWidth(720);
        setMinHeight(560);
        setWidth(780);
        setHeight(620);

        buildUi();
        loadProcessDetails(initialPid);
    }

    private void buildUi() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(18));
        root.setFillWidth(true);

        // Header Section
        VBox headerCard = new VBox(8);
        headerCard.getStyleClass().addAll("card");
        headerCard.setPadding(new Insets(12, 16, 12, 16));

        titleLabel.getStyleClass().addAll("title-2");
        badgesBox.setAlignment(Pos.CENTER_LEFT);

        headerCard.getChildren().addAll(titleLabel, badgesBox);

        // Alert Banner (for Critical system processes or heavy hogs)
        alertBox.setVisible(false);
        alertBox.setManaged(false);
        alertBox.setPadding(new Insets(10, 14, 10, 14));

        // TabPane for categorized metrics
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        tabPane.getTabs().addAll(
                createIdentityTab(),
                createCpuTab(),
                createMemoryIoTab()
        );

        // Footer Actions Bar
        HBox footerBar = new HBox(10);
        footerBar.setAlignment(Pos.CENTER_LEFT);
        footerBar.setPadding(new Insets(8, 0, 0, 0));

        refreshBtn.getStyleClass().add("button-outlined");
        refreshBtn.setOnAction(e -> loadProcessDetails(currentPid));

        openFolderBtn.getStyleClass().add("button-outlined");
        openFolderBtn.setOnAction(e -> openFileLocation());

        copyReportBtn.getStyleClass().add("button-outlined");
        copyReportBtn.setOnAction(e -> copyDiagnosticsToClipboard());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        terminateBtn.getStyleClass().add("danger");
        terminateBtn.setOnAction(e -> handleTermination());

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> close());

        footerBar.getChildren().addAll(
                refreshBtn,
                openFolderBtn,
                copyReportBtn,
                spacer,
                terminateBtn,
                closeBtn
        );

        root.getChildren().addAll(headerCard, alertBox, tabPane, footerBar);

        Scene scene = new Scene(root);
        String customCss = getClass().getResource("/css/app.css") != null
                ? getClass().getResource("/css/app.css").toExternalForm()
                : null;
        if (customCss != null) {
            scene.getStylesheets().add(customCss);
        }
        setScene(scene);
    }

    private Tab createIdentityTab() {
        Tab tab = new Tab("Identity & Execution");

        VBox content = new VBox(12);
        content.setPadding(new Insets(14));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        pathField.setEditable(false);
        pathField.setStyle("-fx-font-family: monospace;");
        Button copyPathBtn = new Button("Copy");
        copyPathBtn.setOnAction(e -> copyToClipboard(pathField.getText()));
        HBox pathBox = new HBox(6, pathField, copyPathBtn);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        commandLineArea.setEditable(false);
        commandLineArea.setWrapText(true);
        commandLineArea.setPrefRowCount(3);
        commandLineArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        Button copyCmdBtn = new Button("Copy");
        copyCmdBtn.setOnAction(e -> copyToClipboard(commandLineArea.getText()));
        VBox cmdBox = new VBox(4, commandLineArea, copyCmdBtn);
        VBox.setVgrow(commandLineArea, Priority.ALWAYS);

        cwdField.setEditable(false);
        cwdField.setStyle("-fx-font-family: monospace;");
        Button copyCwdBtn = new Button("Copy");
        copyCwdBtn.setOnAction(e -> copyToClipboard(cwdField.getText()));
        HBox cwdBox = new HBox(6, cwdField, copyCwdBtn);
        HBox.setHgrow(cwdField, Priority.ALWAYS);

        int row = 0;
        grid.add(createBoldLabel("Executable Path:"), 0, row);
        grid.add(pathBox, 1, row++);

        grid.add(createBoldLabel("Command Line:"), 0, row);
        grid.add(cmdBox, 1, row++);

        grid.add(createBoldLabel("Working Directory:"), 0, row);
        grid.add(cwdBox, 1, row++);

        grid.add(createBoldLabel("User & UID:"), 0, row);
        grid.add(userLabel, 1, row++);

        grid.add(createBoldLabel("Group & GID:"), 0, row);
        grid.add(groupLabel, 1, row++);

        grid.add(createBoldLabel("Process Start Time:"), 0, row);
        grid.add(startedLabel, 1, row++);

        grid.add(createBoldLabel("Elapsed Uptime:"), 0, row);
        grid.add(uptimeLabel, 1, row++);

        grid.add(createBoldLabel("Child Process PIDs:"), 0, row);
        grid.add(childPidsPane, 1, row);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(140);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent;");

        content.getChildren().add(sp);
        tab.setContent(content);
        return tab;
    }

    private Tab createCpuTab() {
        Tab tab = new Tab("CPU & Scheduling");

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        HBox cpuBox = new HBox(10, cpuValueLabel, cpuBar);
        cpuBox.setAlignment(Pos.CENTER_LEFT);
        cpuBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cpuBar, Priority.ALWAYS);

        int row = 0;
        grid.add(createBoldLabel("Current CPU Load:"), 0, row);
        grid.add(cpuBox, 1, row++);

        grid.add(createBoldLabel("User Mode CPU Time:"), 0, row);
        grid.add(userTimeLabel, 1, row++);

        grid.add(createBoldLabel("Kernel Mode CPU Time:"), 0, row);
        grid.add(kernelTimeLabel, 1, row++);

        grid.add(createBoldLabel("Cumulative CPU Time:"), 0, row);
        grid.add(totalCpuTimeLabel, 1, row++);

        grid.add(createBoldLabel("Thread Count:"), 0, row);
        grid.add(threadsLabel, 1, row++);

        grid.add(createBoldLabel("Process Priority:"), 0, row);
        grid.add(priorityLabel, 1, row);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(160);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        content.getChildren().add(grid);
        tab.setContent(content);
        return tab;
    }

    private Tab createMemoryIoTab() {
        Tab tab = new Tab("Memory & Disk I/O");

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        int row = 0;
        grid.add(createBoldLabel("Physical Memory (RSS):"), 0, row);
        grid.add(rssLabel, 1, row++);

        grid.add(createBoldLabel("Virtual Memory (VSZ):"), 0, row);
        grid.add(vszLabel, 1, row++);

        grid.add(createBoldLabel("Minor Page Faults:"), 0, row);
        grid.add(minorFaultsLabel, 1, row++);

        grid.add(createBoldLabel("Major Page Faults:"), 0, row);
        grid.add(majorFaultsLabel, 1, row++);

        grid.add(createBoldLabel("Total Disk I/O:"), 0, row);
        grid.add(totalIoLabel, 1, row++);

        grid.add(createBoldLabel("Total Bytes Read:"), 0, row);
        grid.add(readIoLabel, 1, row++);

        grid.add(createBoldLabel("Total Bytes Written:"), 0, row);
        grid.add(writeIoLabel, 1, row++);

        grid.add(createBoldLabel("Open File Descriptors:"), 0, row);
        grid.add(openFilesLabel, 1, row);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(170);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        content.getChildren().add(grid);
        tab.setContent(content);
        return tab;
    }

    public void loadProcessDetails(int pid) {
        this.currentPid = pid;
        refreshBtn.setDisable(true);

        executorService.submit(() -> {
            DetailedProcessInfo info = processService.getProcessDetails(pid);

            Platform.runLater(() -> {
                refreshBtn.setDisable(false);
                if (info == null) {
                    showProcessTerminatedState(pid);
                } else {
                    renderProcessDetails(info);
                }
            });
        });
    }

    private void renderProcessDetails(DetailedProcessInfo info) {
        this.currentDetails = info;

        titleLabel.setText(info.name() + " (PID " + info.pid() + ")");

        // Header Badges
        badgesBox.getChildren().clear();

        Label pidBadge = new Label("PID: " + info.pid());
        pidBadge.getStyleClass().addAll("badge", "badge-subtle");

        Label parentBadge = new Label("Parent: " + info.parentName());
        parentBadge.getStyleClass().addAll("badge", "badge-subtle");

        Label bitnessBadge = new Label(info.bitness() > 0 ? info.bitness() + "-bit" : "Unknown Arch");
        bitnessBadge.getStyleClass().addAll("badge", "badge-subtle");

        Label stateBadge = new Label(info.state());
        stateBadge.getStyleClass().addAll("badge", "success");

        Label impactBadge = new Label(info.impactLevel());
        impactBadge.getStyleClass().add("badge");
        if (info.isCriticalSystemProcess()) {
            impactBadge.getStyleClass().add("danger");
        } else if (info.isHighCpu()) {
            impactBadge.getStyleClass().add("danger");
        } else if (info.isHighMemory()) {
            impactBadge.getStyleClass().add("warning");
        } else {
            impactBadge.getStyleClass().add("badge-subtle");
        }

        badgesBox.getChildren().addAll(pidBadge, parentBadge, bitnessBadge, stateBadge, impactBadge);

        // Alert Banner
        alertBox.getChildren().clear();
        if (info.isCriticalSystemProcess()) {
            alertBox.getStyleClass().setAll("card");
            alertBox.setStyle("-fx-border-color: -color-danger-emphasis; -fx-background-color: rgba(230, 57, 70, 0.08);");
            Label warnTitle = new Label("🛡 Critical Operating System Process");
            warnTitle.getStyleClass().addAll("text-bold", "text-danger");
            Label warnDesc = new Label("This executable is an essential operating system component. Termination is restricted to prevent system collapse or data corruption.");
            warnDesc.getStyleClass().add("text-small");
            alertBox.getChildren().addAll(warnTitle, warnDesc);
            alertBox.setVisible(true);
            alertBox.setManaged(true);
            terminateBtn.setDisable(true);
            terminateBtn.setTooltip(new Tooltip("Cannot terminate protected system core process."));
        } else if (info.isSystemHog()) {
            alertBox.getStyleClass().setAll("card");
            alertBox.setStyle("-fx-border-color: -color-warning-emphasis; -fx-background-color: rgba(244, 162, 97, 0.08);");
            Label warnTitle = new Label("⚠ Active Resource Blocker Detected");
            warnTitle.getStyleClass().addAll("text-bold", "text-warning");
            Label warnDesc = new Label("This task is currently utilizing heavy hardware capacity (" + info.formattedCpu() + " CPU, " + info.formattedRam() + " RAM). If the application is unresponsive, consider terminating it.");
            warnDesc.getStyleClass().add("text-small");
            alertBox.getChildren().addAll(warnTitle, warnDesc);
            alertBox.setVisible(true);
            alertBox.setManaged(true);
            terminateBtn.setDisable(false);
            terminateBtn.setTooltip(null);
        } else {
            alertBox.setVisible(false);
            alertBox.setManaged(false);
            terminateBtn.setDisable(false);
            terminateBtn.setTooltip(null);
        }

        // Tab 1: Identity
        pathField.setText(info.path());
        commandLineArea.setText(info.commandLine());
        cwdField.setText(info.currentWorkingDirectory());
        userLabel.setText(info.user() + (info.userId() != null && !"-".equals(info.userId()) ? " (UID: " + info.userId() + ")" : ""));
        groupLabel.setText(info.group() + (info.groupId() != null && !"-".equals(info.groupId()) ? " (GID: " + info.groupId() + ")" : ""));
        startedLabel.setText(info.formattedStartTime());
        uptimeLabel.setText(info.formattedUpTime());

        // Child PIDs
        childPidsPane.getChildren().clear();
        if (info.childPids().isEmpty()) {
            Label noChildren = new Label("None (No active subprocesses)");
            noChildren.getStyleClass().add("text-subtle");
            childPidsPane.getChildren().add(noChildren);
        } else {
            for (int childPid : info.childPids()) {
                Button childBtn = new Button("PID " + childPid + " →");
                childBtn.getStyleClass().addAll("button-outlined", "button-small");
                childBtn.setOnAction(e -> loadProcessDetails(childPid));
                childPidsPane.getChildren().add(childBtn);
            }
        }

        // Tab 2: CPU
        cpuValueLabel.setText(info.formattedCpu());
        cpuBar.setProgress(Math.max(0.01, Math.min(1.0, info.cpuPercent() / 100.0)));
        if (info.cpuPercent() >= 25.0) {
            cpuBar.getStyleClass().setAll("progress-bar", "danger");
        } else if (info.cpuPercent() >= 10.0) {
            cpuBar.getStyleClass().setAll("progress-bar", "warning");
        } else {
            cpuBar.getStyleClass().setAll("progress-bar", "success");
        }
        userTimeLabel.setText(info.formattedUserTime());
        kernelTimeLabel.setText(info.formattedKernelTime());
        totalCpuTimeLabel.setText(info.formattedTotalCpuTime());
        threadsLabel.setText(FormatUtils.formatNumber(info.threadCount()) + " active threads");
        priorityLabel.setText(String.valueOf(info.priority()));

        // Tab 3: Memory & I/O
        rssLabel.setText(info.formattedRam());
        vszLabel.setText(info.formattedVirtualRam());
        minorFaultsLabel.setText(FormatUtils.formatNumber(info.minorFaults()));
        majorFaultsLabel.setText(FormatUtils.formatNumber(info.majorFaults()));
        totalIoLabel.setText(info.formattedTotalIo());
        readIoLabel.setText(info.formattedDiskRead());
        writeIoLabel.setText(info.formattedDiskWritten());
        openFilesLabel.setText(info.openFiles() >= 0 ? FormatUtils.formatNumber(info.openFiles()) : "Unavailable (OS restricted)");
    }

    private void showProcessTerminatedState(int pid) {
        titleLabel.setText("Process PID " + pid + " (Terminated / Exited)");
        badgesBox.getChildren().clear();
        Label exitedBadge = new Label("EXITED");
        exitedBadge.getStyleClass().addAll("badge", "badge-subtle");
        badgesBox.getChildren().add(exitedBadge);

        alertBox.getChildren().clear();
        alertBox.getStyleClass().setAll("card");
        Label deadTitle = new Label("Process Has Ended");
        deadTitle.getStyleClass().addAll("text-bold");
        Label deadDesc = new Label("This process is no longer running or active in the operating system process table.");
        alertBox.getChildren().addAll(deadTitle, deadDesc);
        alertBox.setVisible(true);
        alertBox.setManaged(true);

        terminateBtn.setDisable(true);
    }

    private void openFileLocation() {
        if (currentDetails == null) return;
        String pathStr = currentDetails.path();
        if (pathStr == null || pathStr.isBlank() || "-".equals(pathStr)) return;

        executorService.submit(() -> {
            try {
                File file = new File(pathStr);
                File dir = file.isDirectory() ? file : file.getParentFile();
                if (dir != null && dir.exists()) {
                    java.awt.Desktop.getDesktop().open(dir);
                }
            } catch (Exception e) {
                log.error("Failed to open file directory for {}: {}", pathStr, e.getMessage());
            }
        });
    }

    private void copyDiagnosticsToClipboard() {
        if (currentDetails == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("=== CleanPilot Process Diagnostics Report ===\n");
        sb.append("Process Name:     ").append(currentDetails.name()).append("\n");
        sb.append("Process ID (PID): ").append(currentDetails.pid()).append("\n");
        sb.append("Parent Process:   ").append(currentDetails.parentName()).append("\n");
        sb.append("Architecture:     ").append(currentDetails.bitness()).append("-bit\n");
        sb.append("State:            ").append(currentDetails.state()).append("\n");
        sb.append("System Impact:    ").append(currentDetails.impactLevel()).append("\n");
        sb.append("Executable Path:  ").append(currentDetails.path()).append("\n");
        sb.append("Command Line:     ").append(currentDetails.commandLine()).append("\n");
        sb.append("Working Dir:      ").append(currentDetails.currentWorkingDirectory()).append("\n");
        sb.append("User / UID:       ").append(currentDetails.user()).append(" / ").append(currentDetails.userId()).append("\n");
        sb.append("Start Time:       ").append(currentDetails.formattedStartTime()).append("\n");
        sb.append("Elapsed Uptime:   ").append(currentDetails.formattedUpTime()).append("\n");
        sb.append("CPU Load:         ").append(currentDetails.formattedCpu()).append("\n");
        sb.append("CPU Time (User):  ").append(currentDetails.formattedUserTime()).append("\n");
        sb.append("CPU Time (Sys):   ").append(currentDetails.formattedKernelTime()).append("\n");
        sb.append("Thread Count:     ").append(currentDetails.threadCount()).append("\n");
        sb.append("Memory (RSS):     ").append(currentDetails.formattedRam()).append("\n");
        sb.append("Virtual Mem (VSZ):").append(currentDetails.formattedVirtualRam()).append("\n");
        sb.append("Disk Read:        ").append(currentDetails.formattedDiskRead()).append("\n");
        sb.append("Disk Written:     ").append(currentDetails.formattedDiskWritten()).append("\n");
        sb.append("Open Handles:     ").append(currentDetails.openFiles()).append("\n");
        sb.append("Child PIDs:       ").append(currentDetails.childPids()).append("\n");

        copyToClipboard(sb.toString());
    }

    private void handleTermination() {
        if (currentDetails == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(this);
        confirm.setTitle("Confirm Process Termination");
        confirm.setHeaderText("Terminate " + currentDetails.name() + " (PID " + currentDetails.pid() + ")?");
        confirm.setContentText("Any unsaved work in this process may be lost.\nDo you want to proceed?");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            executorService.submit(() -> {
                boolean success = processService.terminateProcess(currentDetails.pid(), false);
                Platform.runLater(() -> {
                    if (success) {
                        showProcessTerminatedState(currentDetails.pid());
                    } else {
                        Alert err = new Alert(Alert.AlertType.ERROR);
                        err.initOwner(this);
                        err.setTitle("Termination Failed");
                        err.setHeaderText("Could not terminate process PID " + currentDetails.pid());
                        err.setContentText("Access was denied or the process is protected by operating system security policies.");
                        err.showAndWait();
                    }
                });
            });
        }
    }

    private void copyToClipboard(String text) {
        if (text == null) return;
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private Label createBoldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-bold");
        return label;
    }
}
