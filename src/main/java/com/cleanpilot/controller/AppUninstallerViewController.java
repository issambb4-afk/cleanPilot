package com.cleanpilot.controller;

import com.cleanpilot.model.AppResidualInfo;
import com.cleanpilot.model.InstalledAppInfo;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.service.AppUninstallerService;
import com.cleanpilot.ui.FormatUtils;
import com.cleanpilot.ui.OperationConfirmationDialog;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Controller for the Deep App Uninstaller & Leftovers Cleaner view.
 */
@Component
public class AppUninstallerViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(AppUninstallerViewController.class);

    private final AppUninstallerService uninstallerService;
    private final ExecutorService executorService;

    @FXML private Label totalAppsLabel;
    @FXML private Label totalFootprintLabel;
    @FXML private Label residualSizeLabel;
    @FXML private Label residualCountLabel;

    @FXML private ToggleButton installedAppsTabBtn;
    @FXML private ToggleButton residualsTabBtn;
    @FXML private TextField searchField;
    @FXML private Button refreshBtn;

    // Table 1: Installed Programs
    @FXML private TableView<InstalledAppInfo> appsTable;
    @FXML private TableColumn<InstalledAppInfo, String> appNameCol;
    @FXML private TableColumn<InstalledAppInfo, String> appPublisherCol;
    @FXML private TableColumn<InstalledAppInfo, String> appVersionCol;
    @FXML private TableColumn<InstalledAppInfo, String> appSizeCol;
    @FXML private TableColumn<InstalledAppInfo, String> appArchCol;
    @FXML private TableColumn<InstalledAppInfo, String> appDateCol;
    @FXML private TableColumn<InstalledAppInfo, String> appLocationCol;

    // Table 2: Orphaned Residuals
    @FXML private TableView<AppResidualInfo> residualsTable;
    @FXML private TableColumn<AppResidualInfo, Boolean> resSelectCol;
    @FXML private TableColumn<AppResidualInfo, String> resFolderCol;
    @FXML private TableColumn<AppResidualInfo, String> resAreaCol;
    @FXML private TableColumn<AppResidualInfo, String> resSizeCol;
    @FXML private TableColumn<AppResidualInfo, String> resPathCol;

    // Action toolbars
    @FXML private HBox appsActionBox;
    @FXML private Label selectedAppLabel;
    @FXML private CheckBox quietUninstallCheckBox;
    @FXML private Button uninstallBtn;

    @FXML private HBox residualsActionBox;
    @FXML private Label residualSelectionLabel;
    @FXML private Button cleanResidualsBtn;

    private final ObservableList<InstalledAppInfo> masterApps = FXCollections.observableArrayList();
    private FilteredList<InstalledAppInfo> filteredApps;

    private final ObservableList<AppResidualInfo> masterResiduals = FXCollections.observableArrayList();
    private FilteredList<AppResidualInfo> filteredResiduals;

    public AppUninstallerViewController(AppUninstallerService uninstallerService, ExecutorService executorService) {
        this.uninstallerService = uninstallerService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTabs();
        setupAppsTable();
        setupResidualsTable();
        appsTable.setPlaceholder(new Label("Click 'Scan Installed Apps' to query installed programs from Windows Registry."));
        residualsTable.setPlaceholder(new Label("Click 'Scan Residuals' to detect orphaned leftovers in ProgramData and AppData."));
    }

    private void setupTabs() {
        ToggleGroup group = new ToggleGroup();
        installedAppsTabBtn.setToggleGroup(group);
        residualsTabBtn.setToggleGroup(group);

        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isApps = installedAppsTabBtn.isSelected();
            appsTable.setVisible(isApps);
            appsTable.setManaged(isApps);
            appsActionBox.setVisible(isApps);
            appsActionBox.setManaged(isApps);

            residualsTable.setVisible(!isApps);
            residualsTable.setManaged(!isApps);
            residualsActionBox.setVisible(!isApps);
            residualsActionBox.setManaged(!isApps);

            applySearchFilter();
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter());
    }

    private void setupAppsTable() {
        appNameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDisplayName()));
        appPublisherCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPublisher()));
        appVersionCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDisplayVersion()));
        appSizeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFormattedSize()));
        appArchCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().is64Bit() ? "64-bit" : "32-bit"));
        appDateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getInstallDate()));
        appLocationCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getInstallLocation()));

        filteredApps = new FilteredList<>(masterApps, p -> true);
        SortedList<InstalledAppInfo> sorted = new SortedList<>(filteredApps);
        sorted.comparatorProperty().bind(appsTable.comparatorProperty());
        appsTable.setItems(sorted);

        appsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedAppLabel.setText("Selected: " + newVal.getDisplayName() + " (" + newVal.getFormattedSize() + ")");
                uninstallBtn.setDisable(false);
                quietUninstallCheckBox.setDisable(!newVal.hasQuietUninstall());
            } else {
                selectedAppLabel.setText("Select an application above to uninstall.");
                uninstallBtn.setDisable(true);
            }
        });
    }

    private void setupResidualsTable() {
        resSelectCol.setCellValueFactory(d -> d.getValue().selectedProperty());
        resSelectCol.setCellFactory(CheckBoxTableCell.forTableColumn(resSelectCol));

        resFolderCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAppName()));
        resAreaCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSourceArea()));
        resSizeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFormattedSize()));
        resPathCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPath().toString()));

        filteredResiduals = new FilteredList<>(masterResiduals, p -> true);
        SortedList<AppResidualInfo> sorted = new SortedList<>(filteredResiduals);
        sorted.comparatorProperty().bind(residualsTable.comparatorProperty());
        residualsTable.setItems(sorted);

        masterResiduals.addListener((javafx.beans.Observable o) -> updateResidualSelection());
    }

    private void applySearchFilter() {
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";

        if (installedAppsTabBtn.isSelected()) {
            filteredApps.setPredicate(app -> {
                if (q.isEmpty()) return true;
                return app.getDisplayName().toLowerCase(Locale.ROOT).contains(q)
                        || app.getPublisher().toLowerCase(Locale.ROOT).contains(q)
                        || app.getInstallLocation().toLowerCase(Locale.ROOT).contains(q);
            });
        } else {
            filteredResiduals.setPredicate(res -> {
                if (q.isEmpty()) return true;
                return res.getAppName().toLowerCase(Locale.ROOT).contains(q)
                        || res.getPath().toString().toLowerCase(Locale.ROOT).contains(q);
            });
        }
    }

    @FXML
    public void onRefresh() {
        if (refreshBtn != null) {
            refreshBtn.setDisable(true);
            refreshBtn.setText("⏳ Scanning Applications & Residuals...");
        }

        submitAsync(() -> {
            try {
                List<InstalledAppInfo> apps = uninstallerService.scanInstalledApps();
                List<AppResidualInfo> residuals = uninstallerService.scanOrphanedResiduals(apps);

                Platform.runLater(() -> {
                    masterApps.setAll(apps);
                    masterResiduals.setAll(residuals);

                    for (AppResidualInfo r : residuals) {
                        r.selectedProperty().addListener((obs, o, n) -> updateResidualSelection());
                    }

                    updateKpis();
                    updateResidualSelection();

                    if (refreshBtn != null) {
                        refreshBtn.setDisable(false);
                        refreshBtn.setText("🔄 Refresh Programs");
                    }
                });
            } catch (Exception ex) {
                log.error("Failed scanning apps/residuals: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    if (refreshBtn != null) {
                        refreshBtn.setDisable(false);
                        refreshBtn.setText("🔄 Refresh Programs");
                    }
                });
            }
        });
    }

    @FXML
    public void onUninstallApp() {
        InstalledAppInfo selected = appsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        boolean quiet = quietUninstallCheckBox.isSelected();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Program Uninstallation");
        confirm.setHeaderText("Launch uninstaller for " + selected.getDisplayName() + "?");
        confirm.setContentText("Command: " + (quiet ? selected.getQuietUninstallString() : selected.getUninstallString())
                + "\n\nCleanPilot will execute the native software uninstaller. After uninstallation finishes, click 'Refresh Programs' to scan for any leftover AppData folders.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            boolean launched = uninstallerService.runUninstaller(selected, quiet);
            if (launched) {
                Alert done = new Alert(Alert.AlertType.INFORMATION);
                done.setTitle("Uninstaller Launched");
                done.setHeaderText("Uninstaller is running for " + selected.getDisplayName());
                done.setContentText("Complete the on-screen uninstaller steps. Once finished, switch to 'Orphaned AppData Leftovers' to clean residual files.");
                done.showAndWait();
            } else {
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("Execution Failed");
                err.setHeaderText("Could not launch uninstaller.");
                err.setContentText("Access was denied or the uninstaller command path is invalid.");
                err.showAndWait();
            }
        }
    }

    @FXML
    public void onSelectAllResiduals() {
        for (AppResidualInfo item : filteredResiduals) {
            item.setSelected(true);
        }
        updateResidualSelection();
    }

    @FXML
    public void onDeselectAllResiduals() {
        for (AppResidualInfo item : filteredResiduals) {
            item.setSelected(false);
        }
        updateResidualSelection();
    }

    @FXML
    public void onCleanResiduals() {
        List<AppResidualInfo> toClean = masterResiduals.stream().filter(AppResidualInfo::isSelected).toList();
        if (toClean.isEmpty()) return;

        long totalBytes = toClean.stream().mapToLong(AppResidualInfo::getSizeBytes).sum();

        Window window = residualsTable != null && residualsTable.getScene() != null
                ? residualsTable.getScene().getWindow() : null;

        OperationPreview preview = OperationPreview.builder()
                .operationType("APP_RESIDUAL_CLEANUP")
                .riskClassification(RiskClassification.IRREVERSIBLE)
                .targetCount(toClean.size())
                .targetDescriptions(toClean.stream().map(r -> r.getPath().toString()).toList())
                .estimatedBytes(totalBytes)
                .exactByteEstimate(false)
                .recoveryMechanism("Permanent Deletion (Unrecoverable)")
                .irreversible(true)
                .requiresElevation(false)
                .addWarning("These directories belong to software that is no longer registered as installed.")
                .build();

        if (OperationConfirmationDialog.confirm(window, preview)) {
            submitAsync(() -> {
                int cleanedCount = 0;
                long cleanedBytes = 0;

                for (AppResidualInfo r : toClean) {
                    if (uninstallerService.deleteResidual(r)) {
                        cleanedCount++;
                        cleanedBytes += r.getSizeBytes();
                    }
                }

                int finalCount = cleanedCount;
                long finalBytes = cleanedBytes;
                Platform.runLater(() -> {
                    masterResiduals.removeAll(toClean);
                    updateKpis();
                    updateResidualSelection();

                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setTitle("Cleanup Complete");
                    done.setHeaderText("Reclaimed " + FormatUtils.formatBytes(finalBytes) + " of disk space.");
                    done.setContentText("Successfully removed " + finalCount + " orphaned leftover folders.");
                    done.showAndWait();
                });
            });
        }
    }

    private void updateKpis() {
        long totalApps = masterApps.size();
        long totalFootprint = masterApps.stream().mapToLong(InstalledAppInfo::getEstimatedSizeBytes).sum();
        long resBytes = masterResiduals.stream().mapToLong(AppResidualInfo::getSizeBytes).sum();
        long resCount = masterResiduals.size();

        totalAppsLabel.setText(String.valueOf(totalApps));
        totalFootprintLabel.setText(FormatUtils.formatBytes(totalFootprint));
        residualSizeLabel.setText(FormatUtils.formatBytes(resBytes));
        residualCountLabel.setText(resCount + " leftover folders found");
    }

    private void updateResidualSelection() {
        List<AppResidualInfo> selected = masterResiduals.stream().filter(AppResidualInfo::isSelected).toList();
        long bytes = selected.stream().mapToLong(AppResidualInfo::getSizeBytes).sum();
        residualSelectionLabel.setText("Selected: " + selected.size() + " folders (" + FormatUtils.formatBytes(bytes) + ")");
        cleanResidualsBtn.setDisable(selected.isEmpty());
    }

    private void submitAsync(Runnable task) {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            return;
        }
        try {
            executorService.submit(task);
        } catch (RejectedExecutionException ex) {
            log.debug("App uninstaller task rejected: executor is shut down.");
        }
    }
}
