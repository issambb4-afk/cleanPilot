package com.cleanpilot.controller;

import com.cleanpilot.service.LocalizationService;
import com.cleanpilot.ui.SpringFXMLLoader;
import com.cleanpilot.ui.ThemeManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@Component
public class MainViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(MainViewController.class);

    private static final String LANG_EN = "🇬🇧 English";
    private static final String LANG_FR = "🇫🇷 Français";
    private static final String LANG_AR = "🇸🇦 العربية";

    @FXML private BorderPane rootPane;
    @FXML private ComboBox<String> languageComboBox;
    @FXML private Label appSubtitleLabel;
    @FXML private Label privacyBadgeLabel;
    @FXML private Label proHeaderBadge;
    @FXML private Label tierBadgeLabel;
    @FXML private Button upgradeProSidebarBtn;

    @FXML private Label sectionMainLabel;
    @FXML private Label sectionPerfLabel;
    @FXML private Label sectionSecLabel;
    @FXML private Label sectionAuditLabel;
    @FXML private Label footerPrivacyLabel;

    @FXML private Menu menuFile;
    @FXML private Menu menuView;
    @FXML private Menu menuWindow;
    @FXML private Menu menuHelp;
    @FXML private MenuItem menuToggleTheme;
    @FXML private MenuItem menuMinimize;
    @FXML private MenuItem menuMaximize;
    @FXML private MenuItem menuClose;
    @FXML private MenuItem menuAbout;

    @FXML private Button navDashboardBtn;
    @FXML private Button navProcessesBtn;
    @FXML private Button navThreatHunterBtn;
    @FXML private Button navDiagnosticsBtn;
    @FXML private Button navDriversBtn;
    @FXML private Button navLargeFilesBtn;
    @FXML private Button navDuplicatesBtn;
    @FXML private Button navHistoryBtn;
    @FXML private Button navRecuperateBtn;
    @FXML private Button navDevCleanerBtn;
    @FXML private Button navStartupBtn;
    @FXML private Button navUninstallerBtn;
    @FXML private Button navSystemRepairBtn;
    @FXML private Button navNetworkOptimizerBtn;
    @FXML private Button navSettingsBtn;
    @FXML private Button themeToggleBtn;
    @FXML private MenuBar mainMenuBar;
    @FXML private StackPane contentArea;

    private final SpringFXMLLoader fxmlLoader;
    private final ThemeManager themeManager;
    private final LocalizationService localizationService;
    private final com.cleanpilot.service.LicenseService licenseService;
    private final Map<String, Parent> viewCache = new HashMap<>();

    private String currentFxmlPath = "/fxml/DashboardView.fxml";
    private Button currentNavBtn;
    private boolean updatingLanguage = false;

    public MainViewController(
            SpringFXMLLoader fxmlLoader,
            ThemeManager themeManager,
            LocalizationService localizationService,
            com.cleanpilot.service.LicenseService licenseService
    ) {
        this.fxmlLoader = fxmlLoader;
        this.themeManager = themeManager;
        this.localizationService = localizationService;
        this.licenseService = licenseService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupLanguageSelector();
        updateLicenseIndicators();
        localizationService.addLanguageChangeListener(locale -> Platform.runLater(this::onLocaleChanged));
        applyLocalization();
        showDashboard();
    }

    private void updateLicenseIndicators() {
        boolean isPro = licenseService != null && licenseService.isPro();
        if (proHeaderBadge != null) {
            proHeaderBadge.setVisible(isPro);
            proHeaderBadge.setManaged(isPro);
        }
        if (tierBadgeLabel != null) {
            if (isPro) {
                tierBadgeLabel.setText("PRO");
                tierBadgeLabel.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: #1e1b4b; -fx-font-weight: 800;");
            } else {
                tierBadgeLabel.setText("FREE");
                tierBadgeLabel.setStyle("");
            }
        }
        if (upgradeProSidebarBtn != null) {
            upgradeProSidebarBtn.setVisible(!isPro);
            upgradeProSidebarBtn.setManaged(!isPro);
        }
    }

    private void setupLanguageSelector() {
        if (languageComboBox == null) return;

        languageComboBox.setItems(FXCollections.observableArrayList(LANG_EN, LANG_FR, LANG_AR));

        Locale current = localizationService.getCurrentLocale();
        if ("fr".equalsIgnoreCase(current.getLanguage())) {
            languageComboBox.getSelectionModel().select(LANG_FR);
        } else if ("ar".equalsIgnoreCase(current.getLanguage())) {
            languageComboBox.getSelectionModel().select(LANG_AR);
        } else {
            languageComboBox.getSelectionModel().select(LANG_EN);
        }

        languageComboBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (updatingLanguage || newV == null) return;
            if (LANG_FR.equals(newV)) {
                localizationService.setLanguage("fr");
            } else if (LANG_AR.equals(newV)) {
                localizationService.setLanguage("ar");
            } else {
                localizationService.setLanguage("en");
            }
        });
    }

    private void onLocaleChanged() {
        updatingLanguage = true;
        try {
            Locale current = localizationService.getCurrentLocale();
            if (languageComboBox != null) {
                if ("fr".equalsIgnoreCase(current.getLanguage())) {
                    languageComboBox.getSelectionModel().select(LANG_FR);
                } else if ("ar".equalsIgnoreCase(current.getLanguage())) {
                    languageComboBox.getSelectionModel().select(LANG_AR);
                } else {
                    languageComboBox.getSelectionModel().select(LANG_EN);
                }
            }

            if (rootPane != null) {
                rootPane.setNodeOrientation(localizationService.getNodeOrientation());
            }

            // Invalidate cache so views reload with the new language bundle
            viewCache.clear();

            applyLocalization();

            // Re-render current active view
            if (currentFxmlPath != null) {
                loadView(currentFxmlPath);
            }
        } finally {
            updatingLanguage = false;
        }
    }

    public void applyLocalization() {
        if (rootPane != null) {
            rootPane.setNodeOrientation(localizationService.getNodeOrientation());
        }

        updateThemeButtonLabel();

        // Top bar
        if (appSubtitleLabel != null) {
            appSubtitleLabel.setText(localizationService.get("app.subtitle"));
        }
        if (privacyBadgeLabel != null) {
            privacyBadgeLabel.setText("● " + localizationService.get("app.privacy"));
        }

        // Section Headers
        if (sectionMainLabel != null) sectionMainLabel.setText(localizationService.get("nav.section.main"));
        if (sectionPerfLabel != null) sectionPerfLabel.setText(localizationService.get("nav.section.performance"));
        if (sectionSecLabel != null) sectionSecLabel.setText(localizationService.get("nav.section.security"));
        if (sectionAuditLabel != null) sectionAuditLabel.setText(localizationService.get("nav.section.audit"));
        if (footerPrivacyLabel != null) footerPrivacyLabel.setText(localizationService.get("app.offline.badge"));

        // Navigation Buttons
        setNavButtonText(navDashboardBtn, "nav.dashboard", "nav.dashboard.tooltip");
        setNavButtonText(navDevCleanerBtn, "nav.dev_cleaner", "nav.dev_cleaner.tooltip");
        setNavButtonText(navProcessesBtn, "nav.processes", "nav.processes.tooltip");
        setNavButtonText(navStartupBtn, "nav.startup", "nav.startup.tooltip");
        setNavButtonText(navDiagnosticsBtn, "nav.diagnostics", "nav.diagnostics.tooltip");
        setNavButtonText(navDriversBtn, "nav.drivers", "nav.drivers.tooltip");
        setNavButtonText(navThreatHunterBtn, "nav.threat_hunter", "nav.threat_hunter.tooltip");
        setNavButtonText(navLargeFilesBtn, "nav.large_files", "nav.large_files.tooltip");
        setNavButtonText(navDuplicatesBtn, "nav.duplicates", "nav.duplicates.tooltip");
        setNavButtonText(navUninstallerBtn, "nav.uninstaller", "nav.uninstaller.tooltip");
        setNavButtonText(navSystemRepairBtn, "nav.system_repair", "nav.system_repair.tooltip");
        setNavButtonText(navNetworkOptimizerBtn, "nav.network_optimizer", "nav.network_optimizer.tooltip");
        setNavButtonText(navRecuperateBtn, "nav.recuperate", "nav.recuperate.tooltip");
        setNavButtonText(navHistoryBtn, "nav.history", "nav.history.tooltip");
        setNavButtonText(navSettingsBtn, "nav.settings", "nav.settings.tooltip");

        // Menu Bar
        if (menuFile != null) menuFile.setText(localizationService.get("menu.file"));
        if (menuView != null) menuView.setText(localizationService.get("menu.view"));
        if (menuWindow != null) menuWindow.setText(localizationService.get("menu.window"));
        if (menuHelp != null) menuHelp.setText(localizationService.get("menu.help"));
        if (menuToggleTheme != null) menuToggleTheme.setText(localizationService.get("menu.toggle_theme"));
        if (menuMinimize != null) menuMinimize.setText(localizationService.get("menu.minimize"));
        if (menuMaximize != null) menuMaximize.setText(localizationService.get("menu.maximize"));
        if (menuClose != null) menuClose.setText(localizationService.get("menu.close"));
        if (menuAbout != null) menuAbout.setText(localizationService.get("menu.about"));
    }

    private void setNavButtonText(Button btn, String textKey, String tooltipKey) {
        if (btn == null) return;
        btn.setText(localizationService.get(textKey));
        btn.setTooltip(new Tooltip(localizationService.get(tooltipKey)));
    }

    @FXML
    public void onMinimizeWindow() {
        Stage stage = getStage();
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    public void onMaximizeWindow() {
        Stage stage = getStage();
        if (stage != null) {
            boolean next = !stage.isMaximized();
            stage.setMaximized(next);
            if (!next && stage.getY() < 0) {
                stage.setY(0);
            }
        }
    }

    @FXML
    public void onCloseWindow() {
        Stage stage = getStage();
        if (stage != null) {
            stage.close();
        }
        javafx.application.Platform.exit();
        System.exit(0);
    }

    @FXML
    public void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(localizationService.get("menu.about"));
        alert.setHeaderText("CleanPilot Desktop v1.0");
        alert.setContentText(
                "Honest, Privacy-First Storage Analyzer & Laptop Diagnostics Hub.\n\n" +
                "• Strict Zero-Telemetry: No network requests, all metrics local-first.\n" +
                "• Native Telemetry: OSHI hardware & system layer.\n" +
                "• Stack: Java 21, Spring Boot 3, JavaFX 21 & AtlantaFX.\n" +
                "• Safe Operations: Native trash delegation & streaming hashing."
        );
        alert.showAndWait();
    }

    private Stage getStage() {
        if (contentArea != null && contentArea.getScene() != null) {
            return (Stage) contentArea.getScene().getWindow();
        }
        return null;
    }

    @FXML
    public void showDashboard() {
        currentFxmlPath = "/fxml/DashboardView.fxml";
        currentNavBtn = navDashboardBtn;
        setActiveNav(navDashboardBtn);
        loadView("/fxml/DashboardView.fxml");
    }

    @FXML
    public void showProcesses() {
        currentFxmlPath = "/fxml/ProcessesView.fxml";
        currentNavBtn = navProcessesBtn;
        setActiveNav(navProcessesBtn);
        loadView("/fxml/ProcessesView.fxml");
    }

    @FXML
    public void showThreatHunter() {
        currentFxmlPath = "/fxml/ThreatHunterView.fxml";
        currentNavBtn = navThreatHunterBtn;
        setActiveNav(navThreatHunterBtn);
        loadView("/fxml/ThreatHunterView.fxml");
    }

    @FXML
    public void showDiagnostics() {
        currentFxmlPath = "/fxml/LaptopDiagnosticsView.fxml";
        currentNavBtn = navDiagnosticsBtn;
        setActiveNav(navDiagnosticsBtn);
        loadView("/fxml/LaptopDiagnosticsView.fxml");
    }

    @FXML
    public void showDrivers() {
        currentFxmlPath = "/fxml/DriversView.fxml";
        currentNavBtn = navDriversBtn;
        setActiveNav(navDriversBtn);
        loadView("/fxml/DriversView.fxml");
    }

    @FXML
    public void showLargeFiles() {
        currentFxmlPath = "/fxml/LargeFilesView.fxml";
        currentNavBtn = navLargeFilesBtn;
        setActiveNav(navLargeFilesBtn);
        loadView("/fxml/LargeFilesView.fxml");
    }

    @FXML
    public void showDuplicates() {
        currentFxmlPath = "/fxml/DuplicatesView.fxml";
        currentNavBtn = navDuplicatesBtn;
        setActiveNav(navDuplicatesBtn);
        loadView("/fxml/DuplicatesView.fxml");
    }

    @FXML
    public void showHistory() {
        currentFxmlPath = "/fxml/HistoryView.fxml";
        currentNavBtn = navHistoryBtn;
        setActiveNav(navHistoryBtn);
        loadView("/fxml/HistoryView.fxml");
    }

    @FXML
    public void showRecuperate() {
        currentFxmlPath = "/fxml/recuperate-view.fxml";
        currentNavBtn = navRecuperateBtn;
        setActiveNav(navRecuperateBtn);
        loadView("/fxml/recuperate-view.fxml");
    }

    @FXML
    public void showDeveloperCleaner() {
        currentFxmlPath = "/fxml/DeveloperCleanerView.fxml";
        currentNavBtn = navDevCleanerBtn;
        setActiveNav(navDevCleanerBtn);
        loadView("/fxml/DeveloperCleanerView.fxml");
    }

    @FXML
    public void showStartupOptimizer() {
        currentFxmlPath = "/fxml/StartupOptimizerView.fxml";
        currentNavBtn = navStartupBtn;
        setActiveNav(navStartupBtn);
        loadView("/fxml/StartupOptimizerView.fxml");
    }

    @FXML
    public void showAppUninstaller() {
        currentFxmlPath = "/fxml/AppUninstallerView.fxml";
        currentNavBtn = navUninstallerBtn;
        setActiveNav(navUninstallerBtn);
        loadView("/fxml/AppUninstallerView.fxml");
    }

    @FXML
    public void showSystemRepair() {
        currentFxmlPath = "/fxml/WindowsSystemRepairView.fxml";
        currentNavBtn = navSystemRepairBtn;
        setActiveNav(navSystemRepairBtn);
        loadView("/fxml/WindowsSystemRepairView.fxml");
    }

    @FXML
    public void showNetworkOptimizer() {
        currentFxmlPath = "/fxml/NetworkOptimizerView.fxml";
        currentNavBtn = navNetworkOptimizerBtn;
        setActiveNav(navNetworkOptimizerBtn);
        loadView("/fxml/NetworkOptimizerView.fxml");
    }

    @FXML
    public void showSettings() {
        currentFxmlPath = "/fxml/SettingsView.fxml";
        currentNavBtn = navSettingsBtn;
        setActiveNav(navSettingsBtn);
        loadView("/fxml/SettingsView.fxml");
    }

    @FXML
    public void onToggleTheme() {
        themeManager.toggleTheme();
        updateThemeButtonLabel();
    }

    private void updateThemeButtonLabel() {
        if (themeToggleBtn != null) {
            themeToggleBtn.setText(themeManager.isDark()
                    ? localizationService.get("theme.light")
                    : localizationService.get("theme.dark"));
        }
    }

    private void setActiveNav(Button activeBtn) {
        Button[] buttons = {
                navDashboardBtn, navProcessesBtn, navThreatHunterBtn, navDiagnosticsBtn, navDriversBtn,
                navLargeFilesBtn, navDuplicatesBtn, navHistoryBtn, navRecuperateBtn, navDevCleanerBtn,
                navStartupBtn, navUninstallerBtn, navSystemRepairBtn, navNetworkOptimizerBtn, navSettingsBtn
        };
        for (Button btn : buttons) {
            if (btn != null) {
                if (btn == activeBtn) {
                    if (!btn.getStyleClass().contains("accent")) {
                        btn.getStyleClass().add("accent");
                    }
                } else {
                    btn.getStyleClass().remove("accent");
                }
            }
        }
    }

    public void loadView(String fxmlPath) {
        try {
            Parent view = viewCache.get(fxmlPath);
            if (view == null) {
                view = fxmlLoader.load(fxmlPath);
                viewCache.put(fxmlPath, view);
            }
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load view {}: {}", fxmlPath, e.getMessage(), e);
        }
    }
}
