package com.cleanpilot.controller;

import com.cleanpilot.model.ProcessInfo;
import com.cleanpilot.service.ProcessMonitoringService;
import com.cleanpilot.service.SystemMetricsService;
import com.cleanpilot.ui.FormatUtils;
import com.cleanpilot.ui.PrefixTrie;
import com.cleanpilot.ui.ProcessDetailsDialog;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import jakarta.annotation.PreDestroy;

@Component
public class ProcessMonitorViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ProcessMonitorViewController.class);

    @FXML private TextField searchField;
    @FXML private ComboBox<String> sortByComboBox;
    @FXML private CheckBox filterHogsCheckBox;
    @FXML private CheckBox autoRefreshCheckBox;
    @FXML private Label statusLabel;
    @FXML private Label processCountLabel;
    @FXML private TableView<ProcessInfo> processTable;
    @FXML private TableColumn<ProcessInfo, Integer> pidCol;
    @FXML private TableColumn<ProcessInfo, String> nameCol;
    @FXML private TableColumn<ProcessInfo, Double> cpuCol;
    @FXML private TableColumn<ProcessInfo, Long> ramCol;
    @FXML private TableColumn<ProcessInfo, Long> ioCol;
    @FXML private TableColumn<ProcessInfo, String> impactCol;
    @FXML private TableColumn<ProcessInfo, String> stateCol;
    @FXML private TableColumn<ProcessInfo, String> userCol;
    @FXML private Button inspectProcessBtn;
    @FXML private Button endProcessBtn;
    @FXML private Label cpuLiveValueLabel;
    @FXML private Label ramLiveValueLabel;
    @FXML private javafx.scene.layout.StackPane cpuSparklineContainer;
    @FXML private javafx.scene.layout.StackPane ramSparklineContainer;

    private com.cleanpilot.ui.RollingSparklineView cpuSparkline;
    private com.cleanpilot.ui.RollingSparklineView ramSparkline;

    private final ProcessMonitoringService processService;
    private final SystemMetricsService metricsService;
    private final ExecutorService executorService;

    private final ObservableList<ProcessInfo> masterProcessList = FXCollections.observableArrayList();
    private FilteredList<ProcessInfo> filteredProcessList;
    private final PrefixTrie<ProcessInfo> prefixTrie = new PrefixTrie<>();
    private Timeline autoRefreshTimeline;

    public ProcessMonitorViewController(
            ProcessMonitoringService processService,
            SystemMetricsService metricsService,
            ExecutorService executorService
    ) {
        this.processService = processService;
        this.metricsService = metricsService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        sortByComboBox.getItems().setAll("CPU %", "Physical RAM (RSS)", "Disk I/O", "Process Name", "PID");
        sortByComboBox.setValue("CPU %");
        sortByComboBox.valueProperty().addListener((obs, oldV, newV) -> {
            updateTableSort(newV);
            refreshProcesses();
        });

        if (cpuSparklineContainer != null) {
            cpuSparkline = new com.cleanpilot.ui.RollingSparklineView();
            cpuSparkline.setColors(javafx.scene.paint.Color.web("#0969da"), javafx.scene.paint.Color.web("#0969da", 0.2));
            cpuSparklineContainer.getChildren().setAll(cpuSparkline);
        }
        if (ramSparklineContainer != null) {
            ramSparkline = new com.cleanpilot.ui.RollingSparklineView();
            ramSparkline.setColors(javafx.scene.paint.Color.web("#1a7f37"), javafx.scene.paint.Color.web("#1a7f37", 0.2));
            ramSparklineContainer.getChildren().setAll(ramSparkline);
        }

        processTable.setPlaceholder(new Label("No processes found."));

        pidCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().pid()));
        nameCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().name()));

        cpuCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().cpuPercent()));
        cpuCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatDouble(item, 1) + "%");
                }
            }
        });

        ramCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().memoryBytes()));
        ramCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatBytes(item));
                }
            }
        });

        ioCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().totalIoBytes()));
        ioCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatBytes(item));
                }
            }
        });

        impactCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getImpactLevel()));
        stateCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().state()));
        userCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().user()));

        filteredProcessList = new FilteredList<>(masterProcessList, p -> true);
        SortedList<ProcessInfo> sortedProcessList = new SortedList<>(filteredProcessList);
        sortedProcessList.comparatorProperty().bind(processTable.comparatorProperty());
        processTable.setItems(sortedProcessList);

        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilters());
        filterHogsCheckBox.selectedProperty().addListener((obs, oldV, newV) -> applyFilters());

        processTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            boolean hasSelection = (newV != null);
            endProcessBtn.setDisable(!hasSelection);
            if (inspectProcessBtn != null) {
                inspectProcessBtn.setDisable(!hasSelection);
            }
        });

        processTable.setRowFactory(tv -> {
            TableRow<ProcessInfo> row = new TableRow<>();

            ContextMenu contextMenu = new ContextMenu();
            MenuItem inspectItem = new MenuItem("🔍 Inspect Process Details");
            inspectItem.setOnAction(e -> {
                if (!row.isEmpty()) {
                    inspectProcess(row.getItem().pid());
                }
            });

            MenuItem terminateItem = new MenuItem("🛑 Terminate Process");
            terminateItem.setOnAction(e -> {
                if (!row.isEmpty()) {
                    processTable.getSelectionModel().select(row.getItem());
                    onEndProcess();
                }
            });

            contextMenu.getItems().addAll(inspectItem, new SeparatorMenuItem(), terminateItem);

            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    inspectProcess(row.getItem().pid());
                }
            });

            return row;
        });

        updateTableSort(sortByComboBox.getValue());

        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }

        // Setup auto-refresh (only active when explicitly checked by user)
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            if (autoRefreshCheckBox != null && autoRefreshCheckBox.isSelected()) {
                if (processTable != null && processTable.getScene() != null
                        && processTable.getScene().getWindow() != null
                        && processTable.getScene().getWindow().isShowing()) {
                    refreshProcesses();
                }
            }
        }));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        if (autoRefreshCheckBox != null) {
            autoRefreshCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
                if (newV) {
                    refreshProcesses();
                    if (autoRefreshTimeline != null) autoRefreshTimeline.play();
                } else {
                    if (autoRefreshTimeline != null) autoRefreshTimeline.stop();
                }
            });
            if (autoRefreshCheckBox.isSelected()) {
                autoRefreshTimeline.play();
            }
        }

        refreshProcesses();
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
            log.debug("Process task rejected: executor is shut down.");
        }
    }

    private void updateTableSort(String sortCriteria) {
        if (sortCriteria == null) return;
        processTable.getSortOrder().clear();
        switch (sortCriteria) {
            case "CPU %" -> {
                cpuCol.setSortType(TableColumn.SortType.DESCENDING);
                processTable.getSortOrder().add(cpuCol);
            }
            case "Physical RAM (RSS)" -> {
                ramCol.setSortType(TableColumn.SortType.DESCENDING);
                processTable.getSortOrder().add(ramCol);
            }
            case "Disk I/O" -> {
                ioCol.setSortType(TableColumn.SortType.DESCENDING);
                processTable.getSortOrder().add(ioCol);
            }
            case "Process Name" -> {
                nameCol.setSortType(TableColumn.SortType.ASCENDING);
                processTable.getSortOrder().add(nameCol);
            }
            case "PID" -> {
                pidCol.setSortType(TableColumn.SortType.ASCENDING);
                processTable.getSortOrder().add(pidCol);
            }
        }
    }

    @FXML
    public void refreshProcesses() {
        ProcessMonitoringService.ProcessSortBy sortCriteria = getSelectedSortCriteria();

        submitAsync(() -> {
            List<ProcessInfo> procs = processService.getProcesses(150, sortCriteria);
            long hogCount = procs.stream().filter(ProcessInfo::isSystemHog).count();
            com.cleanpilot.model.SystemResourceMetrics metrics = metricsService.getSystemResourceMetrics();

            Platform.runLater(() -> {
                prefixTrie.clear();
                for (ProcessInfo pi : procs) {
                    prefixTrie.insertTokens(pi.name(), pi);
                    prefixTrie.insert(String.valueOf(pi.pid()), pi);
                    prefixTrie.insertTokens(pi.user(), pi);
                }
                masterProcessList.setAll(procs);
                applyFilters();

                // Update real-time rolling sparklines
                double cpuPct = metrics.cpuLoadPercentage() * 100.0;
                double ramPct = metrics.memoryUsagePercentage() * 100.0;
                if (cpuSparkline != null) {
                    cpuSparkline.addSample(cpuPct);
                }
                if (ramSparkline != null) {
                    ramSparkline.addSample(ramPct);
                }
                if (cpuLiveValueLabel != null) {
                    cpuLiveValueLabel.setText(FormatUtils.formatDouble(cpuPct, 1) + "%");
                }
                if (ramLiveValueLabel != null) {
                    ramLiveValueLabel.setText(FormatUtils.formatDouble(ramPct, 1) + "% ("
                            + FormatUtils.formatBytes(metrics.usedMemoryBytes()) + " / "
                            + FormatUtils.formatBytes(metrics.totalMemoryBytes()) + ")");
                }

                if (statusLabel != null) {
                    statusLabel.setText("Active processes: " + procs.size()
                            + (hogCount > 0 ? " | ⚠ " + hogCount + " high-load tasks detected" : " | System resources normal"));
                }
            });
        });
    }

    private void applyFilters() {
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        boolean hogsOnly = filterHogsCheckBox.isSelected();
        Set<ProcessInfo> trieMatches = query.isEmpty() ? null : new HashSet<>(prefixTrie.searchByPrefix(query));

        filteredProcessList.setPredicate(p -> {
            boolean matchesSearch = query.isEmpty()
                    || (trieMatches != null && trieMatches.contains(p))
                    || p.name().toLowerCase(Locale.ROOT).contains(query)
                    || String.valueOf(p.pid()).contains(query)
                    || p.user().toLowerCase(Locale.ROOT).contains(query);

            boolean matchesHogs = !hogsOnly || p.isSystemHog();

            return matchesSearch && matchesHogs;
        });

        if (processCountLabel != null) {
            processCountLabel.setText("Showing: " + filteredProcessList.size() + " processes");
        }
    }

    @FXML
    public void onInspectProcess() {
        ProcessInfo selected = processTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        inspectProcess(selected.pid());
    }

    public void inspectProcess(int pid) {
        ProcessDetailsDialog dialog = new ProcessDetailsDialog(
                processTable.getScene().getWindow(),
                pid,
                processService,
                executorService
        );
        dialog.showAndWait();
        refreshProcesses();
    }

    @FXML
    public void onEndProcess() {
        ProcessInfo selected = processTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Terminate Process");
        confirm.setHeaderText("Terminate " + selected.name() + " (PID: " + selected.pid() + ")?");
        confirm.setContentText("CPU: " + selected.formattedCpu() + " | RAM: " + selected.formattedRam() + "\n\n"
                + "Terminating an unsaved or critical system process may cause instability or loss of unsaved work.");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            submitAsync(() -> {
                boolean terminated = processService.terminateProcess(selected.pid(), true);
                Platform.runLater(() -> {
                    if (terminated) {
                        statusLabel.setText("Process " + selected.name() + " (PID " + selected.pid() + ") terminated.");
                    } else {
                        statusLabel.setText("Could not terminate " + selected.name() + " (Access Denied / System Protected).");
                    }
                    refreshProcesses();
                });
            });
        }
    }

    private ProcessMonitoringService.ProcessSortBy getSelectedSortCriteria() {
        String val = sortByComboBox.getValue();
        if ("Physical RAM (RSS)".equalsIgnoreCase(val)) {
            return ProcessMonitoringService.ProcessSortBy.MEMORY;
        }
        if ("Disk I/O".equalsIgnoreCase(val)) {
            return ProcessMonitoringService.ProcessSortBy.DISK_IO;
        }
        if ("Process Name".equalsIgnoreCase(val)) {
            return ProcessMonitoringService.ProcessSortBy.NAME;
        }
        if ("PID".equalsIgnoreCase(val)) {
            return ProcessMonitoringService.ProcessSortBy.PID;
        }
        return ProcessMonitoringService.ProcessSortBy.CPU;
    }
}
