package com.cleanpilot.controller;

import com.cleanpilot.model.AppxPackageInfo;
import com.cleanpilot.model.ComponentStoreReport;
import com.cleanpilot.model.SystemIntegrityReport;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.service.OperationLifecycleManager;
import com.cleanpilot.service.SystemIntegrityRepairService;
import com.cleanpilot.service.WindowsAppxDebloaterService;
import com.cleanpilot.service.WindowsComponentStoreService;
import com.cleanpilot.ui.FormatUtils;
import com.cleanpilot.ui.OperationConfirmationDialog;
import com.cleanpilot.util.WindowsSecurityUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Controller for Windows System Integrity, Component Store (WinSxS), and UWP De-Bloater view.
 */
@Component
public class WindowsSystemRepairViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(WindowsSystemRepairViewController.class);

    @FXML private Label adminBadge;
    @FXML private Button refreshAllBtn;
    @FXML private Label integrityStatusLabel;
    @FXML private Label reclaimableWinSxSLabel;
    @FXML private Label debloatableCountLabel;

    // Component store & repair tab
    @FXML private Label winSxSSummaryLabel;
    @FXML private Button cleanWinSxSBtn;
    @FXML private ProgressBar winSxSProgressBar;
    @FXML private Label integritySummaryLabel;
    @FXML private Button scanHealthBtn;
    @FXML private Button repairHealthBtn;
    @FXML private TextArea repairLogArea;

    // AppX De-bloater tab
    @FXML private TextField appxSearchField;
    @FXML private ComboBox<String> appxCategoryComboBox;
    @FXML private Button removeSelectedAppxBtn;
    @FXML private TableView<AppxPackageInfo> appxTableView;
    @FXML private TableColumn<AppxPackageInfo, String> appNameCol;
    @FXML private TableColumn<AppxPackageInfo, String> appCategoryCol;
    @FXML private TableColumn<AppxPackageInfo, String> appPublisherCol;
    @FXML private TableColumn<AppxPackageInfo, String> appSizeCol;
    @FXML private TableColumn<AppxPackageInfo, String> appDescCol;

    private final WindowsComponentStoreService componentStoreService;
    private final SystemIntegrityRepairService integrityService;
    private final WindowsAppxDebloaterService debloaterService;
    private final ExecutorService executorService;
    private final OperationLifecycleManager lifecycleManager;

    private final ObservableList<AppxPackageInfo> masterAppxList = FXCollections.observableArrayList();
    private FilteredList<AppxPackageInfo> filteredAppxList;

    public WindowsSystemRepairViewController(
            WindowsComponentStoreService componentStoreService,
            SystemIntegrityRepairService integrityService,
            WindowsAppxDebloaterService debloaterService,
            ExecutorService executorService,
            @org.springframework.context.annotation.Lazy OperationLifecycleManager lifecycleManager) {
        this.componentStoreService = componentStoreService;
        this.integrityService = integrityService;
        this.debloaterService = debloaterService;
        this.executorService = executorService;
        this.lifecycleManager = lifecycleManager;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        checkAdminStatus();
        setupAppxTable();
        appxTableView.setPlaceholder(new Label("Click 'Analyze Packages' to inspect installed UWP AppX packages."));
    }

    private void checkAdminStatus() {
        boolean isAdmin = WindowsSecurityUtils.isRunningAsAdmin();
        if (adminBadge != null) {
            if (isAdmin) {
                adminBadge.setText("🛡️ Administrator (Full Control)");
                adminBadge.setStyle("-fx-background-color: #2da44e22; -fx-text-fill: #2da44e; -fx-font-weight: bold;");
            } else {
                adminBadge.setText("👤 Standard User Mode (UAC Elevated on Demand)");
                adminBadge.setStyle("-fx-background-color: #0969da22; -fx-text-fill: #0969da;");
            }
        }
    }

    private void setupAppxTable() {
        appNameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().displayName()));
        appCategoryCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().displayCategory()));
        appPublisherCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().publisher()));
        appSizeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().formattedSize()));
        appDescCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().description()));

        filteredAppxList = new FilteredList<>(masterAppxList, p -> true);
        SortedList<AppxPackageInfo> sorted = new SortedList<>(filteredAppxList);
        sorted.comparatorProperty().bind(appxTableView.comparatorProperty());
        appxTableView.setItems(sorted);

        appxCategoryComboBox.getItems().add("All Categories");
        appxCategoryComboBox.setValue("All Categories");
        appxCategoryComboBox.setOnAction(e -> applyAppxFilter());

        appxSearchField.textProperty().addListener((obs, o, n) -> applyAppxFilter());

        appxTableView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            removeSelectedAppxBtn.setDisable(n == null);
        });
    }

    @FXML
    public void onRefreshAll() {
        if (refreshAllBtn != null) refreshAllBtn.setDisable(true);

        submitAsync(() -> {
            // 1. Analyze WinSxS
            ComponentStoreReport report = componentStoreService.analyzeComponentStore();

            // 2. Scan AppX Packages
            List<AppxPackageInfo> appxList = debloaterService.scanDebloatablePackages();

            Platform.runLater(() -> {
                if (reclaimableWinSxSLabel != null) {
                    reclaimableWinSxSLabel.setText(report.formattedReclaimable());
                }
                if (winSxSSummaryLabel != null) {
                    winSxSSummaryLabel.setText(String.format(
                            "WinSxS footprint: %s • Backups/Superseded: %s • Download cache: %s",
                            report.formattedActualStoreSize(),
                            FormatUtils.formatBytes(report.backupsAndDisabledFeaturesBytes()),
                            FormatUtils.formatBytes(componentStoreService.calculateCacheFootprint())
                    ));
                }

                masterAppxList.setAll(appxList);
                if (debloatableCountLabel != null) {
                    debloatableCountLabel.setText(String.valueOf(appxList.size()));
                }

                Set<String> categories = new TreeSet<>();
                for (AppxPackageInfo info : appxList) {
                    if (info.category() != null) categories.add(info.category());
                }
                String currentCat = appxCategoryComboBox.getValue();
                appxCategoryComboBox.getItems().clear();
                appxCategoryComboBox.getItems().add("All Categories");
                appxCategoryComboBox.getItems().addAll(categories);
                appxCategoryComboBox.setValue(categories.contains(currentCat) ? currentCat : "All Categories");

                if (refreshAllBtn != null) refreshAllBtn.setDisable(false);
            });
        });
    }

    private void applyAppxFilter() {
        String q = appxSearchField.getText() != null ? appxSearchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String cat = appxCategoryComboBox.getValue();

        filteredAppxList.setPredicate(app -> {
            if (!q.isBlank()) {
                boolean match = app.displayName().toLowerCase(Locale.ROOT).contains(q)
                        || app.packageFullName().toLowerCase(Locale.ROOT).contains(q)
                        || app.publisher().toLowerCase(Locale.ROOT).contains(q)
                        || app.category().toLowerCase(Locale.ROOT).contains(q);
                if (!match) return false;
            }
            if (cat != null && !"All Categories".equalsIgnoreCase(cat)) {
                return cat.equalsIgnoreCase(app.category());
            }
            return true;
        });
    }

    @FXML
    public void onCleanComponentStore() {
        long reclaimable = componentStoreService.calculateCacheFootprint();
        OperationPreview preview = OperationPreview.builder("Windows Component Store Cleanup (WinSxS & Download Cache)", RiskClassification.HIGH_RISK)
                .targetDescriptions(List.of(
                        "C:\\Windows\\SoftwareDistribution\\Download (Windows Update caches)",
                        "C:\\Windows\\SoftwareDistribution\\DeliveryOptimization (Delivery caches)",
                        "DISM /Online /Cleanup-Image /StartComponentCleanup /ResetBase (Superseded components)"
                ))
                .estimatedBytes(reclaimable, false)
                .irreversible(true)
                .recoveryMechanism("Irreversible: Superseded Windows updates and download caches are permanently purged. Updates cannot be uninstalled afterwards.")
                .warnings(List.of("Requires Administrator privileges", "Aborting midway may disrupt Windows update services"))
                .requiresElevation(true)
                .build();

        Window window = cleanWinSxSBtn.getScene() != null ? cleanWinSxSBtn.getScene().getWindow() : null;
        if (!OperationConfirmationDialog.confirm(window, preview)) {
            return;
        }

        cleanWinSxSBtn.setDisable(true);
        winSxSProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        String opId = lifecycleManager != null ?
                lifecycleManager.startOperation("WINSXS_CLEANUP", RiskClassification.HIGH_RISK, "Component Store") : null;
        if (opId != null) {
            lifecycleManager.recordPreview(opId, preview);
            lifecycleManager.authorize(opId);
            lifecycleManager.beginExecution(opId);
        }

        submitAsync(() -> {
            boolean success = componentStoreService.cleanupComponentStore(msg -> {
                Platform.runLater(() -> winSxSSummaryLabel.setText(msg));
            });

            Platform.runLater(() -> {
                winSxSProgressBar.setProgress(1.0);
                cleanWinSxSBtn.setDisable(false);
                if (opId != null) {
                    if (success) {
                        lifecycleManager.completeOperation(opId, reclaimable, Collections.emptyList());
                    } else {
                        lifecycleManager.failOperation(opId, "Component store cleanup failed or elevation was denied", null);
                    }
                }
                Alert alert = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
                alert.setTitle("Component Store Cleanup");
                alert.setHeaderText(success ? "Cleanup Completed Successfully" : "Cleanup Notice");
                alert.setContentText(success
                        ? "Component store (WinSxS) and SoftwareDistribution caches were cleaned."
                        : "DISM cleanup was aborted or requires administrative elevation.");
                alert.showAndWait();
                onRefreshAll();
            });
        });
    }

    @FXML
    public void onScanHealth() {
        scanHealthBtn.setDisable(true);
        repairLogArea.setText("Starting Windows system integrity check (DISM /CheckHealth)...\n");

        submitAsync(() -> {
            SystemIntegrityReport report = integrityService.scanSystemHealth(msg -> {
                Platform.runLater(() -> repairLogArea.appendText(msg + "\n"));
            });

            Platform.runLater(() -> {
                scanHealthBtn.setDisable(false);
                if (integrityStatusLabel != null) {
                    integrityStatusLabel.setText(report.healthy() ? "Healthy" : "Corruption Detected");
                    integrityStatusLabel.setStyle(report.healthy()
                            ? "-fx-text-fill: -color-success-emphasis;"
                            : "-fx-text-fill: -color-danger-emphasis;");
                }
                repairLogArea.appendText("\nResult: " + report.statusMessage() + "\n");
                if (!report.cbsLogSummary().isBlank()) {
                    repairLogArea.appendText("\nRecent CBS Log entries:\n" + report.cbsLogSummary() + "\n");
                }
            });
        });
    }

    @FXML
    public void onRepairHealth() {
        OperationPreview preview = OperationPreview.builder("Windows System File & Component Store Repair (DISM & SFC)", RiskClassification.HIGH_RISK)
                .targetDescriptions(List.of(
                        "DISM /Online /Cleanup-Image /RestoreHealth (Component store repair)",
                        "sfc /scannow (System file integrity scan & repair)"
                ))
                .estimatedBytes(0, true)
                .irreversible(false)
                .recoveryMechanism("Modifies Windows system binaries to restore corrupted system files from healthy component store sources.")
                .warnings(List.of("Requires Administrator privileges via UAC prompt", "Scan and repair may take 10 to 30 minutes on corrupted systems", "Do not reboot or shut down during repair"))
                .requiresElevation(true)
                .build();

        Window window = repairHealthBtn.getScene() != null ? repairHealthBtn.getScene().getWindow() : null;
        if (!OperationConfirmationDialog.confirm(window, preview)) {
            return;
        }

        repairHealthBtn.setDisable(true);
        repairLogArea.appendText("\nStarting DISM /RestoreHealth and SFC /scannow...\n");

        String opId = lifecycleManager != null ?
                lifecycleManager.startOperation("SYSTEM_FILE_REPAIR", RiskClassification.HIGH_RISK, "System Files & WinSxS") : null;
        if (opId != null) {
            lifecycleManager.recordPreview(opId, preview);
            lifecycleManager.authorize(opId);
            lifecycleManager.beginExecution(opId);
        }

        submitAsync(() -> {
            boolean success = integrityService.repairSystemHealth(msg -> {
                Platform.runLater(() -> repairLogArea.appendText(msg + "\n"));
            });

            Platform.runLater(() -> {
                repairHealthBtn.setDisable(false);
                if (opId != null) {
                    if (success) {
                        lifecycleManager.completeOperation(opId, 0, Collections.emptyList());
                    } else {
                        lifecycleManager.failOperation(opId, "System integrity repair failed or elevation was denied", null);
                    }
                }
                repairLogArea.appendText(success ? "\n✔ System files repaired successfully.\n" : "\n✖ Repair encountered errors or elevation was denied.\n");
                Alert alert = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
                alert.setTitle("System Integrity Repair");
                alert.setHeaderText(success ? "Repair Completed" : "Repair Interrupted");
                alert.setContentText(success
                        ? "Windows component store and system files were restored and verified."
                        : "The repair process did not complete cleanly or required Administrator elevation.");
                alert.showAndWait();
                onScanHealth();
            });
        });
    }

    @FXML
    public void onRemoveSelectedAppx() {
        AppxPackageInfo selected = appxTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Uninstall Package");
        confirm.setHeaderText("Remove " + selected.displayName() + "?");
        confirm.setContentText("Package: " + selected.packageFullName() + "\n\nThis will remove the application for your user account.");
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            removeSelectedAppxBtn.setDisable(true);
            submitAsync(() -> {
                boolean success = debloaterService.removePackage(selected.packageFullName());
                Platform.runLater(() -> {
                    removeSelectedAppxBtn.setDisable(false);
                    if (success) {
                        masterAppxList.remove(selected);
                        if (debloatableCountLabel != null) {
                            debloatableCountLabel.setText(String.valueOf(masterAppxList.size()));
                        }
                    } else {
                        Alert err = new Alert(Alert.AlertType.ERROR);
                        err.setTitle("Uninstall Failed");
                        err.setContentText("Could not uninstall " + selected.displayName() + ". It may be in use or protected.");
                        err.showAndWait();
                    }
                });
            });
        }
    }

    private void submitAsync(Runnable task) {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            return;
        }
        try {
            executorService.submit(task);
        } catch (RejectedExecutionException ignored) {
        }
    }
}
