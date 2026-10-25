package com.cleanpilot.controller;

import com.cleanpilot.service.UserPreferencesService;
import com.cleanpilot.ui.FormatUtils;
import com.cleanpilot.ui.ThemeManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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

    // Lemon Squeezy Licensing FXML
    @FXML private Label licenseBadgeLabel;
    @FXML private Label licenseTierTitleLabel;
    @FXML private Label licenseStatusSubLabel;
    @FXML private Button buyProBtn;
    @FXML private VBox licenseActivationBox;
    @FXML private TextField licenseKeyTextField;
    @FXML private Button activateLicenseBtn;
    @FXML private Label licenseMessageLabel;
    @FXML private HBox licenseActiveInfoBox;
    @FXML private Label licensedUserLabel;
    @FXML private Label maskedKeyLabel;

    private final UserPreferencesService preferencesService;
    private final ThemeManager themeManager;
    private final com.cleanpilot.service.LocalizationService localizationService;
    private final com.cleanpilot.service.WindowsShellIntegrationService shellService;
    private final com.cleanpilot.service.ScheduledMaintenanceService maintenanceService;
    private final com.cleanpilot.service.LicenseService licenseService;
    private final java.util.concurrent.ExecutorService executorService;
    private final ObservableList<String> excludedPaths = FXCollections.observableArrayList();

    public SettingsViewController(
            UserPreferencesService preferencesService,
            ThemeManager themeManager,
            com.cleanpilot.service.LocalizationService localizationService,
            com.cleanpilot.service.WindowsShellIntegrationService shellService,
            com.cleanpilot.service.ScheduledMaintenanceService maintenanceService,
            com.cleanpilot.service.LicenseService licenseService,
            java.util.concurrent.ExecutorService executorService
    ) {
        this.preferencesService = preferencesService;
        this.themeManager = themeManager;
        this.localizationService = localizationService;
        this.shellService = shellService;
        this.maintenanceService = maintenanceService;
        this.licenseService = licenseService;
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

        // Lemon Squeezy Licensing State
        updateLicenseUI();
    }

    private void updateLicenseUI() {
        if (licenseBadgeLabel == null) return;

        var details = licenseService.getLicenseDetails();
        if (details != null && details.isPro()) {
            licenseBadgeLabel.setText("PRO ACTIVE");
            licenseBadgeLabel.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: 800; -fx-padding: 4 10; -fx-background-radius: 4px;");
            licenseTierTitleLabel.setText("CleanPilot Pro — Perpetual License");
            licenseStatusSubLabel.setText("Full commercial features unlocked. Thank you for supporting honest, privacy-first software!");

            if (buyProBtn != null) buyProBtn.setVisible(false);
            if (licenseActivationBox != null) {
                licenseActivationBox.setVisible(false);
                licenseActivationBox.setManaged(false);
            }
            if (licenseActiveInfoBox != null) {
                licenseActiveInfoBox.setVisible(true);
                licenseActiveInfoBox.setManaged(true);
            }
            if (licensedUserLabel != null) {
                String userDisplay = details.customerName();
                if (details.customerEmail() != null && !details.customerEmail().isBlank()) {
                    userDisplay += " (" + details.customerEmail() + ")";
                }
                licensedUserLabel.setText(userDisplay);
            }
            if (maskedKeyLabel != null) {
                maskedKeyLabel.setText(details.formattedMaskedKey());
            }
        } else {
            licenseBadgeLabel.setText("COMMUNITY");
            licenseBadgeLabel.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 800; -fx-padding: 4 10; -fx-background-radius: 4px;");
            licenseTierTitleLabel.setText("CleanPilot Community Edition");
            licenseStatusSubLabel.setText("Upgrade to CleanPilot Pro for automated maintenance, advanced threat heuristics, and priority support.");

            if (buyProBtn != null) buyProBtn.setVisible(true);
            if (licenseActivationBox != null) {
                licenseActivationBox.setVisible(true);
                licenseActivationBox.setManaged(true);
            }
            if (licenseActiveInfoBox != null) {
                licenseActiveInfoBox.setVisible(false);
                licenseActiveInfoBox.setManaged(false);
            }
        }
    }

    @FXML
    public void onBuyPro() {
        showStatus("Opening Lemon Squeezy CleanPilot Pro checkout page...");
        licenseService.openPurchasePage();
    }

    @FXML
    public void onActivateLicense() {
        if (licenseKeyTextField == null) return;
        String rawKey = licenseKeyTextField.getText();
        if (rawKey == null || rawKey.trim().isEmpty()) {
            showLicenseMessage("Please enter your Lemon Squeezy license key.", true);
            return;
        }

        activateLicenseBtn.setDisable(true);
        activateLicenseBtn.setText("Activating...");
        showLicenseMessage("Contacting Lemon Squeezy activation servers...", false);

        licenseService.activateLicense(rawKey.trim())
                .thenAccept(result -> javafx.application.Platform.runLater(() -> {
                    activateLicenseBtn.setDisable(false);
                    activateLicenseBtn.setText("Activate License");
                    if (result.success()) {
                        showLicenseMessage(result.message(), false);
                        updateLicenseUI();
                        showStatus("CleanPilot Pro activated successfully!");
                    } else {
                        showLicenseMessage("Activation Error: " + result.message(), true);
                    }
                }));
    }

    @FXML
    public void onDeactivateLicense() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate CleanPilot Pro License");
        confirm.setHeaderText("Deactivate License on This PC?");
        confirm.setContentText("This will deactivate your license instance with Lemon Squeezy so you can transfer it to another computer.");

        var choice = confirm.showAndWait();
        if (choice.isPresent() && choice.get() == ButtonType.OK) {
            licenseService.deactivateLicense().thenRun(() -> javafx.application.Platform.runLater(() -> {
                updateLicenseUI();
                showStatus("License deactivated. Reverted to Community edition.");
            }));
        }
    }

    private void showLicenseMessage(String msg, boolean isError) {
        if (licenseMessageLabel != null) {
            licenseMessageLabel.setText(msg);
            licenseMessageLabel.setVisible(true);
            licenseMessageLabel.setStyle(isError ? "-fx-text-fill: #ef4444; -fx-font-weight: 600;" : "-fx-text-fill: #10b981; -fx-font-weight: 600;");
        }
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
