package com.cleanpilot.controller;

import com.cleanpilot.model.StartupAppInfo;
import com.cleanpilot.service.StartupOptimizerService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Controller for the Windows Startup & Boot Optimizer view.
 */
@Component
public class StartupOptimizerViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(StartupOptimizerViewController.class);

    @FXML private Label totalAppsLabel;
    @FXML private Label highImpactLabel;
    @FXML private Label disabledAppsLabel;
    @FXML private Label bootDurationLabel;
    @FXML private Label bootVerdictLabel;
    @FXML private TextField searchField;

    @FXML private TableView<StartupAppInfo> startupTable;
    @FXML private TableColumn<StartupAppInfo, Void> statusCol;
    @FXML private TableColumn<StartupAppInfo, String> nameCol;
    @FXML private TableColumn<StartupAppInfo, String> publisherCol;
    @FXML private TableColumn<StartupAppInfo, String> impactCol;
    @FXML private TableColumn<StartupAppInfo, String> locationCol;
    @FXML private TableColumn<StartupAppInfo, String> commandCol;

    private final StartupOptimizerService startupService;
    private final com.cleanpilot.service.BootTraceProfilerService bootProfilerService;
    private final ExecutorService executorService;

    private final ObservableList<StartupAppInfo> masterList = FXCollections.observableArrayList();
    private FilteredList<StartupAppInfo> filteredList;

    public StartupOptimizerViewController(StartupOptimizerService startupService,
                                          com.cleanpilot.service.BootTraceProfilerService bootProfilerService,
                                          ExecutorService executorService) {
        this.startupService = startupService;
        this.bootProfilerService = bootProfilerService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupSearchAndFilter();
        loadStartupApps();
    }

    private void setupTableColumns() {
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
        publisherCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPublisher()));
        locationCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getLocation()));
        commandCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCommand()));

        impactCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getImpact()));
        impactCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String impact, boolean empty) {
                super.updateItem(impact, empty);
                if (empty || impact == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(impact);
                    if ("HIGH".equalsIgnoreCase(impact)) {
                        badge.getStyleClass().setAll("badge", "danger");
                    } else if ("MEDIUM".equalsIgnoreCase(impact)) {
                        badge.getStyleClass().setAll("badge", "warning");
                    } else {
                        badge.getStyleClass().setAll("badge", "success");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        statusCol.setCellFactory(col -> new TableCell<>() {
            private final Button toggleBtn = new Button();

            {
                toggleBtn.getStyleClass().add("button-sm");
                toggleBtn.setOnAction(e -> {
                    StartupAppInfo item = getTableRow().getItem();
                    if (item != null) {
                        boolean targetState = !item.isEnabled();
                        submitAsync(() -> {
                            boolean ok = startupService.setStartupEnabled(item, targetState);
                            Platform.runLater(() -> {
                                if (ok) {
                                    updateKpis();
                                }
                            });
                        });
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    StartupAppInfo app = getTableRow().getItem();
                    if (app.isEnabled()) {
                        toggleBtn.setText("✔ Enabled");
                        toggleBtn.getStyleClass().setAll("button-sm", "success");
                    } else {
                        toggleBtn.setText("✕ Disabled");
                        toggleBtn.getStyleClass().setAll("button-sm", "button-outlined");
                    }
                    setGraphic(toggleBtn);
                }
            }
        });
    }

    private void setupSearchAndFilter() {
        filteredList = new FilteredList<>(masterList, p -> true);
        SortedList<StartupAppInfo> sorted = new SortedList<>(filteredList);
        sorted.comparatorProperty().bind(startupTable.comparatorProperty());
        startupTable.setItems(sorted);

        searchField.textProperty().addListener((obs, o, n) -> {
            String query = (n != null ? n.trim().toLowerCase(Locale.ROOT) : "");
            filteredList.setPredicate(app -> {
                if (query.isEmpty()) return true;
                return app.getName().toLowerCase(Locale.ROOT).contains(query)
                        || app.getPublisher().toLowerCase(Locale.ROOT).contains(query)
                        || app.getCommand().toLowerCase(Locale.ROOT).contains(query);
            });
        });
    }

    @FXML
    public void onRefresh() {
        loadStartupApps();
    }

    @FXML
    public void onDisableAllHighImpact() {
        List<StartupAppInfo> toDisable = masterList.stream()
                .filter(app -> app.isEnabled() && "HIGH".equalsIgnoreCase(app.getImpact()))
                .toList();

        if (toDisable.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Startup Optimized");
            alert.setHeaderText("No High-Impact Apps Active");
            alert.setContentText("All high-impact startup hogs are already disabled or none are configured.");
            alert.showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Boot Optimization");
        confirm.setHeaderText("Disable " + toDisable.size() + " High-Impact Startup Programs?");
        confirm.setContentText("These applications will no longer launch automatically during Windows boot, accelerating desktop loading time.\n\nYou can re-enable them anytime in CleanPilot with 1 click.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            submitAsync(() -> {
                int disabled = 0;
                for (StartupAppInfo app : toDisable) {
                    if (startupService.setStartupEnabled(app, false)) {
                        disabled++;
                    }
                }
                int finalDisabled = disabled;
                Platform.runLater(() -> {
                    updateKpis();
                    startupTable.refresh();
                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setTitle("Optimization Complete");
                    done.setHeaderText("Successfully disabled " + finalDisabled + " startup programs.");
                    done.setContentText("Windows boot time will be significantly accelerated on your next restart.");
                    done.showAndWait();
                });
            });
        }
    }

    public void loadStartupApps() {
        submitAsync(() -> {
            try {
                List<StartupAppInfo> apps = startupService.scanStartupApps();
                com.cleanpilot.model.BootMetricInfo bootMetric = bootProfilerService.getBootMetrics();
                Platform.runLater(() -> {
                    masterList.setAll(apps);
                    updateKpis();
                    if (bootDurationLabel != null) {
                        bootDurationLabel.setText(bootMetric.formattedTotalBootTime());
                    }
                    if (bootVerdictLabel != null) {
                        bootVerdictLabel.setText(bootMetric.bootEvaluationVerdict());
                    }
                });
            } catch (Exception e) {
                log.error("Failed to load startup programs: {}", e.getMessage(), e);
            }
        });
    }

    private void submitAsync(Runnable task) {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            return;
        }
        try {
            executorService.submit(task);
        } catch (RejectedExecutionException ex) {
            log.debug("Startup optimizer task rejected: executor is shut down.");
        }
    }

    private void updateKpis() {
        long total = masterList.size();
        long high = masterList.stream().filter(app -> app.isEnabled() && "HIGH".equalsIgnoreCase(app.getImpact())).count();
        long disabled = masterList.stream().filter(app -> !app.isEnabled()).count();

        if (totalAppsLabel != null) totalAppsLabel.setText(String.valueOf(total));
        if (highImpactLabel != null) highImpactLabel.setText(String.valueOf(high));
        if (disabledAppsLabel != null) disabledAppsLabel.setText(String.valueOf(disabled));
    }
}
