package com.cleanpilot.controller;

import com.cleanpilot.service.UserPreferencesService;
import com.cleanpilot.ui.FormatUtils;
import com.cleanpilot.ui.ThemeManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.Set;

@Component
public class SettingsViewController implements Initializable {

    @FXML private RadioButton darkThemeRadio;
    @FXML private RadioButton lightThemeRadio;
    @FXML private ToggleGroup themeGroup;
    @FXML private RadioButton enLanguageRadio;
    @FXML private RadioButton frLanguageRadio;
    @FXML private RadioButton arLanguageRadio;
    @FXML private ToggleGroup languageGroup;
    @FXML private Spinner<Integer> minSizeSpinner;
    @FXML private CheckBox preferTrashCheckBox;
    @FXML private CheckBox explorerContextMenuCheckBox;
    @FXML private ComboBox<String> maintenanceScheduleComboBox;
    @FXML private CheckBox autoPurgeQuarantineCheckBox;
    @FXML private CheckBox autoCleanTempCheckBox;
    @FXML private CheckBox maintenanceNotificationsCheckBox;
    @FXML private ListView<String> excludedPathsListView;
    @FXML private Label statusLabel;

    private final UserPreferencesService preferencesService;
    private final ThemeManager themeManager;
    private final com.cleanpilot.service.LocalizationService localizationService;
    private final com.cleanpilot.service.WindowsShellIntegrationService shellService;
    private final com.cleanpilot.service.ScheduledMaintenanceService maintenanceService;
    private final java.util.concurrent.ExecutorService executorService;
    private final ObservableList<String> excludedPaths = FXCollections.observableArrayList();

    public SettingsViewController(
            UserPreferencesService preferencesService,
            ThemeManager themeManager,
            com.cleanpilot.service.LocalizationService localizationService,
            com.cleanpilot.service.WindowsShellIntegrationService shellService,
            com.cleanpilot.service.ScheduledMaintenanceService maintenanceService,
            java.util.concurrent.ExecutorService executorService
    ) {
        this.preferencesService = preferencesService;
        this.themeManager = themeManager;
        this.localizationService = localizationService;
        this.shellService = shellService;
        this.maintenanceService = maintenanceService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Language
        String lang = localizationService.getCurrentLocale().getLanguage();
        if ("fr".equalsIgnoreCase(lang) && frLanguageRadio != null) {
            frLanguageRadio.setSelected(true);
        } else if ("ar".equalsIgnoreCase(lang) && arLanguageRadio != null) {
            arLanguageRadio.setSelected(true);
        } else if (enLanguageRadio != null) {
            enLanguageRadio.setSelected(true);
        }

        if (enLanguageRadio != null) enLanguageRadio.setOnAction(e -> localizationService.setLanguage("en"));
        if (frLanguageRadio != null) frLanguageRadio.setOnAction(e -> localizationService.setLanguage("fr"));
        if (arLanguageRadio != null) arLanguageRadio.setOnAction(e -> localizationService.setLanguage("ar"));

        // Theme
        boolean isDark = "DARK".equalsIgnoreCase(preferencesService.getTheme());
        darkThemeRadio.setSelected(isDark);
        lightThemeRadio.setSelected(!isDark);

        darkThemeRadio.setOnAction(e -> {
            themeManager.setTheme(true);
            showStatus("Theme updated to Primer Dark.");
        });

        lightThemeRadio.setOnAction(e -> {
            themeManager.setTheme(false);
            showStatus("Theme updated to Primer Light.");
        });

        // Min large file size spinner (10 MB to 100,000 MB, step 50 MB)
        int currentMb = (int) preferencesService.getMinLargeFileSizeMb();
        SpinnerValueFactory<Integer> valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 100_000, currentMb, 50);
        minSizeSpinner.setValueFactory(valueFactory);
        minSizeSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                preferencesService.setMinLargeFileSizeMb(newV);
                showStatus("Default minimum large file size saved: " + newV + " MB");
            }
        });

        // Safe trash preference
        preferTrashCheckBox.setSelected(preferencesService.isPreferTrash());
        preferTrashCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
            preferencesService.setPreferTrash(newV);
            showStatus("Trash preference saved: " + (newV ? "Prefer native recycle bin" : "Permanent deletion"));
        });

        // Explorer right-click context menu
        if (explorerContextMenuCheckBox != null) {
            explorerContextMenuCheckBox.setSelected(shellService.isContextMenuEnabled());
            explorerContextMenuCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    boolean ok = newV ? shellService.enableContextMenu() : shellService.disableContextMenu();
                    if (ok) {
                        showStatus("Windows File Explorer right-click menu " + (newV ? "enabled." : "disabled."));
                    } else {
                        showStatus("Could not update Windows File Explorer context menu.");
                    }
                }
            });
        }

        // Automated Maintenance Settings
        if (maintenanceScheduleComboBox != null) {
            maintenanceScheduleComboBox.setItems(FXCollections.observableArrayList("WEEKLY", "DAILY", "DISABLED"));
            maintenanceScheduleComboBox.setValue(preferencesService.getAutoMaintenanceSchedule());
            maintenanceScheduleComboBox.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    preferencesService.setAutoMaintenanceSchedule(newV);
                    showStatus("Automated maintenance frequency saved: " + newV);
                }
            });
        }

        if (autoPurgeQuarantineCheckBox != null) {
            autoPurgeQuarantineCheckBox.setSelected(preferencesService.isAutoPurgeQuarantine());
            autoPurgeQuarantineCheckBox.selectedProperty().addListener((obs, o, n) -> {
                preferencesService.setAutoPurgeQuarantine(n);
                showStatus("Quarantine auto-purge " + (n ? "enabled." : "disabled."));
            });
        }

        if (autoCleanTempCheckBox != null) {
            autoCleanTempCheckBox.setSelected(preferencesService.isAutoCleanTemp());
            autoCleanTempCheckBox.selectedProperty().addListener((obs, o, n) -> {
                preferencesService.setAutoCleanTemp(n);
                showStatus("Temp auto-clean " + (n ? "enabled." : "disabled."));
            });
        }

        if (maintenanceNotificationsCheckBox != null) {
            maintenanceNotificationsCheckBox.setSelected(preferencesService.isMaintenanceNotifications());
            maintenanceNotificationsCheckBox.selectedProperty().addListener((obs, o, n) -> {
                preferencesService.setMaintenanceNotifications(n);
                showStatus("Maintenance notifications " + (n ? "enabled." : "disabled."));
            });
        }

        // Excluded paths
        Set<String> savedExclusions = preferencesService.getExcludedPaths();
        excludedPaths.setAll(savedExclusions);
        excludedPathsListView.setItems(excludedPaths);
        excludedPathsListView.setPlaceholder(new Label("No exclusion paths configured."));
    }

    @FXML
    public void onRunMaintenanceNow() {
        showStatus("Running maintenance in background...");
        if (executorService != null && !executorService.isShutdown() && !executorService.isTerminated()) {
            try {
                executorService.submit(() -> {
                    var result = maintenanceService.executeMaintenance(false);
                    javafx.application.Platform.runLater(() -> {
                        showStatus("Maintenance complete: Reclaimed " + FormatUtils.formatBytes(result.totalBytesReclaimed()) + " across " + result.totalFilesCleaned() + " files.");
                        Alert done = new Alert(Alert.AlertType.INFORMATION);
                        done.setTitle("Maintenance Completed");
                        done.setHeaderText("Automated Maintenance Finished");
                        done.setContentText("Reclaimed " + FormatUtils.formatBytes(result.totalBytesReclaimed()) + " across " + result.totalFilesCleaned() + " files.\nStandby RAM and expired quarantine items optimized.");
                        done.showAndWait();
                    });
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        }
    }

    @FXML
    public void onAddExcludedPath() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Directory to Exclude from All Scans");
        File dir = chooser.showDialog(excludedPathsListView.getScene().getWindow());
        if (dir != null) {
            String path = dir.getAbsolutePath();
            if (!excludedPaths.contains(path)) {
                excludedPaths.add(path);
                preferencesService.addExcludedPath(path);
                showStatus("Added exclusion rule: " + path);
            }
        }
    }

    @FXML
    public void onRemoveExcludedPath() {
        String selected = excludedPathsListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            excludedPaths.remove(selected);
            preferencesService.removeExcludedPath(selected);
            showStatus("Removed exclusion rule: " + selected);
        }
    }

    private void showStatus(String msg) {
        if (statusLabel != null) {
            statusLabel.setText(msg);
        }
    }
}
