package com.cleanpilot.controller;

import com.cleanpilot.model.DrivePartitionInfo;
import com.cleanpilot.model.ProcessInfo;
import com.cleanpilot.model.SmartCleanAnalysis;
import com.cleanpilot.model.SmartCleanResult;
import com.cleanpilot.model.SystemResourceMetrics;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.service.ProcessMonitoringService;
import com.cleanpilot.service.ScanHistoryService;
import com.cleanpilot.service.SmartCleanerService;
import com.cleanpilot.service.SystemMetricsService;
import com.cleanpilot.ui.FormatUtils;
import com.cleanpilot.ui.OperationConfirmationDialog;
import com.cleanpilot.ui.ProcessDetailsDialog;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Window;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Component
public class DashboardViewController implements Initializable {

    // CCleaner Health Check UI Components
    @FXML private Canvas gaugeCanvas;
    @FXML private Label heroTitleLabel;
    @FXML private Label heroSubtitleLabel;
    @FXML private Button heroActionBtn;
    @FXML private Button toggleDetailsBtn;
    @FXML private VBox progressBox;
    @FXML private ProgressBar smartCleanProgressBar;
    @FXML private Label smartCleanStatusLabel;
    @FXML private Button viewSimpleBtn;
    @FXML private Button viewTechnicalBtn;
    @FXML private VBox technicalSection;
    @FXML private VBox detailsDrawer;

    // Detailed Findings Table
    @FXML private TableView<SmartCleanAnalysis.SmartCleanTargetItem> detailsTable;
    @FXML private TableColumn<SmartCleanAnalysis.SmartCleanTargetItem, String> colCategory;
    @FXML private TableColumn<SmartCleanAnalysis.SmartCleanTargetItem, String> colDescription;
    @FXML private TableColumn<SmartCleanAnalysis.SmartCleanTargetItem, Number> colItemCount;
    @FXML private TableColumn<SmartCleanAnalysis.SmartCleanTargetItem, String> colSize;
    @FXML private TableColumn<SmartCleanAnalysis.SmartCleanTargetItem, String> colPath;

    // 4 Category Health Cards
    @FXML private Label cardValue1;
    @FXML private Label cardValue2;
    @FXML private Label cardValue3;
    @FXML private Label cardValue4;
    @FXML private Label cardBadge1;
    @FXML private Label cardBadge2;
    @FXML private Label cardBadge3;
    @FXML private Label cardBadge4;

    // CCleaner Step Indicator Bar
    @FXML private StackPane stepCircle1;
    @FXML private StackPane stepCircle2;
    @FXML private StackPane stepCircle3;
    @FXML private StackPane stepCircle4;
    @FXML private StackPane stepCircle5;
    @FXML private Label stepLabel1;
    @FXML private Label stepLabel2;
    @FXML private Label stepLabel3;
    @FXML private Label stepLabel4;
    @FXML private Label stepLabel5;
    @FXML private Label stepText1;
    @FXML private Label stepText2;
    @FXML private Label stepText3;
    @FXML private Label stepText4;
    @FXML private Label stepText5;
    @FXML private Region stepLine1;
    @FXML private Region stepLine2;
    @FXML private Region stepLine3;
    @FXML private Region stepLine4;

    // Hardware & Telemetry Components (preserved from baseline)
    @FXML private VBox drivesContainer;
    @FXML private VBox hogsContainer;
    @FXML private Label cpuLabel;
    @FXML private ProgressBar cpuProgressBar;
    @FXML private Label memoryLabel;
    @FXML private ProgressBar memoryProgressBar;
    @FXML private Label totalCleanedLabel;
    @FXML private Label totalItemsDeletedLabel;
    @FXML private Label osLabel;

    private final SystemMetricsService systemMetricsService;
    private final ProcessMonitoringService processMonitoringService;
    private final ScanHistoryService scanHistoryService;
    private final SmartCleanerService smartCleanerService;
    private final ExecutorService executorService;
    private final MainViewController mainViewController;
    private final com.cleanpilot.service.LocalizationService localizationService;

    private enum HealthState { IDLE, ANALYZING, ANALYZED, CLEANING, CLEANED }
    private HealthState currentState = HealthState.IDLE;
    private SmartCleanAnalysis currentAnalysis = null;

    public DashboardViewController(
            SystemMetricsService systemMetricsService,
            ProcessMonitoringService processMonitoringService,
            ScanHistoryService scanHistoryService,
            SmartCleanerService smartCleanerService,
            ExecutorService executorService,
            @Lazy MainViewController mainViewController,
            com.cleanpilot.service.LocalizationService localizationService
    ) {
        this.systemMetricsService = systemMetricsService;
        this.processMonitoringService = processMonitoringService;
        this.scanHistoryService = scanHistoryService;
        this.smartCleanerService = smartCleanerService;
        this.executorService = executorService;
        this.mainViewController = mainViewController;
        this.localizationService = localizationService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (osLabel != null) {
            osLabel.setText("OS: " + systemMetricsService.getOsFamily() + " • " + localizationService.get("app.privacy"));
        }
        if (hogsContainer != null) {
            Label placeholder = new Label(localizationService.get("dashboard.refresh.tooltip"));
            placeholder.getStyleClass().add("text-subtle");
            hogsContainer.getChildren().setAll(placeholder);
        }

        // Initialize Table Columns
        if (detailsTable != null && colCategory != null) {
            colCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category()));
            colDescription.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().description()));
            colItemCount.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().itemCount()));
            colSize.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().formattedSize()));
            colPath.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path()));
        }

        // Initialize Gauge & State
        drawGauge(0.0, false, false);
        setStepState(1);

        // Fetch initial telemetry without hog scan to keep 0% CPU
        refreshTelemetry(false);
    }

    /* =========================================================================
       CCleaner Health Check State & Action Handlers
       ========================================================================= */

    @FXML
    public void onHeroActionClick() {
        if (currentState == HealthState.IDLE || currentState == HealthState.CLEANED) {
            runHealthAnalysis();
        } else if (currentState == HealthState.ANALYZED) {
            onRunSmartClean();
        }
    }

    private void runHealthAnalysis() {
        currentState = HealthState.ANALYZING;
        updateUIState(HealthState.ANALYZING);

        submitAsync(() -> {
            try {
                SmartCleanAnalysis analysis = smartCleanerService.analyzeSmartClean((taskName, percent) -> {
                    Platform.runLater(() -> {
                        if (smartCleanStatusLabel != null) smartCleanStatusLabel.setText(taskName);
                        if (smartCleanProgressBar != null) smartCleanProgressBar.setProgress(percent);
                        drawGauge(percent, false, false);

                        if (percent < 0.25) setStepState(1);
                        else if (percent < 0.50) setStepState(2);
                        else if (percent < 0.75) setStepState(3);
                        else setStepState(4);
                    });
                });

                this.currentAnalysis = analysis;

                Platform.runLater(() -> {
                    currentState = HealthState.ANALYZED;
                    updateUIState(HealthState.ANALYZED);
                    applyAnalysisResults(analysis);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    currentState = HealthState.IDLE;
                    updateUIState(HealthState.IDLE);
                });
            }
        });
    }

    private void applyAnalysisResults(SmartCleanAnalysis analysis) {
        if (cardValue1 != null) cardValue1.setText(analysis.formattedPrivacyBytes());
        if (cardBadge1 != null) {
            cardBadge1.setText(analysis.browserCacheBytes() > 0 ? "À nettoyer" : "Propre");
            cardBadge1.getStyleClass().setAll("badge", analysis.browserCacheBytes() > 0 ? "badge-warning" : "badge-success");
        }

        if (cardValue2 != null) cardValue2.setText(analysis.formattedSpaceBytes());
        if (cardBadge2 != null) {
            cardBadge2.setText(analysis.totalBytesReclaimable() > 0 ? "Récupérable" : "Optimal");
            cardBadge2.getStyleClass().setAll("badge", analysis.totalBytesReclaimable() > 0 ? "badge-info" : "badge-success");
        }

        if (cardValue3 != null) cardValue3.setText(analysis.formattedMemoryReclaimable());
        if (cardBadge3 != null) {
            cardBadge3.setText("Optimisable");
            cardBadge3.getStyleClass().setAll("badge", "badge-warning");
        }

        if (cardValue4 != null) cardValue4.setText("Système Sain");
        if (cardBadge4 != null) {
            cardBadge4.setText("Protégé");
            cardBadge4.getStyleClass().setAll("badge", "badge-success");
        }

        if (detailsTable != null && analysis.targetDetails() != null) {
            detailsTable.setItems(FXCollections.observableArrayList(analysis.targetDetails()));
        }

        setStepState(5);
        drawGauge(1.0, false, true);
    }

    @FXML
    public void onRunSmartClean() {
        Window window = heroActionBtn != null && heroActionBtn.getScene() != null
                ? heroActionBtn.getScene().getWindow() : null;

        long estBytes = currentAnalysis != null ? currentAnalysis.totalBytesReclaimable() : 0L;
        int targetCount = currentAnalysis != null && currentAnalysis.targetDetails() != null
                ? currentAnalysis.targetDetails().size() : 4;

        List<String> targetDescriptions = List.of(
                "Caches temporaires et traces de navigation (%LOCALAPPDATA%\\Google\\Chrome & Edge)",
                "Répertoire temporaire utilisateur (%TEMP%)",
                "Fichiers temporaires Windows (C:\\Windows\\Temp)",
                "Rapports d'erreurs et fichiers Crash Dumps (*.dmp)",
                "Optimisation et trim des working sets de la mémoire vive (RAM)"
        );

        OperationPreview preview = OperationPreview.builder()
                .operationType("SMART_CLEAN_AND_BOOST")
                .riskClassification(RiskClassification.LOW_RISK)
                .targetCount(targetCount)
                .targetDescriptions(targetDescriptions)
                .estimatedBytes(estBytes)
                .exactByteEstimate(estBytes > 0)
                .recoveryMechanism("Purge sécurisée des caches temporaires et optimisation RAM")
                .irreversible(false)
                .requiresElevation(false)
                .build();

        if (!OperationConfirmationDialog.confirm(window, preview)) {
            return;
        }

        currentState = HealthState.CLEANING;
        updateUIState(HealthState.CLEANING);

        submitAsync(() -> {
            try {
                SmartCleanResult result = smartCleanerService.performSmartClean(
                        (taskName, percent) -> Platform.runLater(() -> {
                            if (smartCleanStatusLabel != null) smartCleanStatusLabel.setText(taskName);
                            if (smartCleanProgressBar != null) smartCleanProgressBar.setProgress(percent);
                            drawGauge(percent, false, false);
                        })
                );

                Platform.runLater(() -> {
                    currentState = HealthState.CLEANED;
                    updateUIState(HealthState.CLEANED);
                    drawGauge(1.0, true, false);

                    if (cardValue1 != null) cardValue1.setText("0 B");
                    if (cardBadge1 != null) {
                        cardBadge1.setText("Nettoyé ✔");
                        cardBadge1.getStyleClass().setAll("badge", "badge-success");
                    }
                    if (cardValue2 != null) cardValue2.setText("0 B");
                    if (cardBadge2 != null) {
                        cardBadge2.setText("Libéré ✔");
                        cardBadge2.getStyleClass().setAll("badge", "badge-success");
                    }
                    if (cardValue3 != null) cardValue3.setText("Optimisée");
                    if (cardBadge3 != null) {
                        cardBadge3.setText("Fluide ✔");
                        cardBadge3.getStyleClass().setAll("badge", "badge-success");
                    }

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Bilan de santé terminé");
                    alert.setHeaderText("Votre PC est propre et optimisé !\nEspace libéré : " + result.formattedTotalReclaimed() + " • RAM récupérée : " + result.formattedMemoryTrimmed());

                    StringBuilder sb = new StringBuilder();
                    sb.append("Rapport d'optimisation CleanPilot :\n\n");
                    for (String detail : result.details()) {
                        sb.append("• ").append(detail).append("\n");
                    }
                    sb.append("\nAudit consigné dans l'historique local.");
                    alert.setContentText(sb.toString());
                    alert.showAndWait();

                    refreshTelemetry(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    currentState = HealthState.IDLE;
                    updateUIState(HealthState.IDLE);
                });
            }
        });
    }

    private void updateUIState(HealthState state) {
        switch (state) {
            case IDLE -> {
                if (heroTitleLabel != null) heroTitleLabel.setText(localizationService.get("dashboard.hero.title"));
                if (heroSubtitleLabel != null) heroSubtitleLabel.setText(localizationService.get("dashboard.hero.subtitle"));
                if (heroActionBtn != null) {
                    heroActionBtn.setText(localizationService.get("dashboard.btn.analyze"));
                    heroActionBtn.setDisable(false);
                    heroActionBtn.getStyleClass().setAll("btn-pill", "btn-pill-primary");
                }
                if (toggleDetailsBtn != null) {
                    toggleDetailsBtn.setVisible(false);
                    toggleDetailsBtn.setManaged(false);
                }
                if (progressBox != null) {
                    progressBox.setVisible(false);
                    progressBox.setManaged(false);
                }
                drawGauge(0.0, false, false);
                setStepState(1);
            }
            case ANALYZING -> {
                if (heroTitleLabel != null) heroTitleLabel.setText(localizationService.get("dashboard.hero.analyzing_title"));
                if (heroSubtitleLabel != null) heroSubtitleLabel.setText(localizationService.get("dashboard.hero.analyzing_subtitle"));
                if (heroActionBtn != null) {
                    heroActionBtn.setText(localizationService.get("dashboard.btn.analyzing"));
                    heroActionBtn.setDisable(true);
                }
                if (progressBox != null) {
                    progressBox.setVisible(true);
                    progressBox.setManaged(true);
                }
            }
            case ANALYZED -> {
                long total = currentAnalysis != null ? currentAnalysis.totalBytesReclaimable() : 0;
                String formatted = FormatUtils.formatBytes(total);
                if (heroTitleLabel != null) heroTitleLabel.setText(localizationService.get("dashboard.hero.ready_title") + " (" + formatted + ")");
                if (heroSubtitleLabel != null) heroSubtitleLabel.setText(localizationService.get("dashboard.hero.ready_subtitle"));
                if (heroActionBtn != null) {
                    heroActionBtn.setText(localizationService.get("dashboard.btn.clean"));
                    heroActionBtn.setDisable(false);
                    heroActionBtn.getStyleClass().setAll("btn-pill", "btn-pill-success");
                }
                if (toggleDetailsBtn != null) {
                    toggleDetailsBtn.setVisible(true);
                    toggleDetailsBtn.setManaged(true);
                    toggleDetailsBtn.setText(localizationService.get("dashboard.btn.view_details"));
                }
                if (progressBox != null) {
                    progressBox.setVisible(false);
                    progressBox.setManaged(false);
                }
            }
            case CLEANING -> {
                if (heroTitleLabel != null) heroTitleLabel.setText(localizationService.get("dashboard.hero.cleaning_title"));
                if (heroSubtitleLabel != null) heroSubtitleLabel.setText(localizationService.get("dashboard.hero.cleaning_subtitle"));
                if (heroActionBtn != null) {
                    heroActionBtn.setText(localizationService.get("dashboard.btn.cleaning"));
                    heroActionBtn.setDisable(true);
                }
                if (progressBox != null) {
                    progressBox.setVisible(true);
                    progressBox.setManaged(true);
                }
            }
            case CLEANED -> {
                if (heroTitleLabel != null) heroTitleLabel.setText(localizationService.get("dashboard.hero.done_title"));
                if (heroSubtitleLabel != null) heroSubtitleLabel.setText(localizationService.get("dashboard.hero.done_subtitle"));
                if (heroActionBtn != null) {
                    heroActionBtn.setText(localizationService.get("dashboard.btn.reanalyze"));
                    heroActionBtn.setDisable(false);
                    heroActionBtn.getStyleClass().setAll("btn-pill", "btn-pill-primary");
                }
                if (progressBox != null) {
                    progressBox.setVisible(false);
                    progressBox.setManaged(false);
                }
            }
        }
    }

    private void setStepState(int activeStep) {
        StackPane[] circles = {stepCircle1, stepCircle2, stepCircle3, stepCircle4, stepCircle5};
        Label[] circleLabels = {stepLabel1, stepLabel2, stepLabel3, stepLabel4, stepLabel5};
        Label[] texts = {stepText1, stepText2, stepText3, stepText4, stepText5};
        Region[] lines = {stepLine1, stepLine2, stepLine3, stepLine4};

        for (int i = 0; i < 5; i++) {
            StackPane c = circles[i];
            Label cl = circleLabels[i];
            Label t = texts[i];
            if (c == null || cl == null || t == null) continue;

            c.getStyleClass().removeAll("active", "completed");
            t.getStyleClass().remove("active");

            if (i + 1 < activeStep || currentState == HealthState.CLEANED) {
                c.getStyleClass().add("completed");
                cl.setText("✔");
                t.getStyleClass().add("active");
            } else if (i + 1 == activeStep) {
                c.getStyleClass().add("active");
                cl.setText(String.valueOf(i + 1));
                t.getStyleClass().add("active");
            } else {
                cl.setText(String.valueOf(i + 1));
            }
        }

        for (int i = 0; i < 4; i++) {
            Region line = lines[i];
            if (line == null) continue;
            line.getStyleClass().remove("active");
            if (i + 1 < activeStep || currentState == HealthState.CLEANED) {
                line.getStyleClass().add("active");
            }
        }
    }

    private void drawGauge(double progress, boolean isCleaned, boolean hasIssues) {
        if (gaugeCanvas == null) return;
        GraphicsContext gc = gaugeCanvas.getGraphicsContext2D();
        double w = gaugeCanvas.getWidth();
        double h = gaugeCanvas.getHeight();
        gc.clearRect(0, 0, w, h);

        double centerX = w / 2.0;
        double centerY = h / 2.0;
        double radius = Math.min(centerX, centerY) - 10;

        // Background track ring
        gc.setLineWidth(10.0);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setStroke(Color.web("#e2e8f0"));
        gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Active progress arc
        Color arcColor;
        if (isCleaned) {
            arcColor = Color.web("#10b981");
        } else if (hasIssues) {
            arcColor = Color.web("#0284c7");
        } else {
            arcColor = Color.web("#2563eb");
        }

        if (progress > 0) {
            gc.setStroke(arcColor);
            double angle = Math.min(360.0, Math.max(4.0, progress * 360.0));
            gc.strokeArc(centerX - radius, centerY - radius, radius * 2, radius * 2, 90, -angle, ArcType.OPEN);
        }

        // Center typography or icon
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        if (isCleaned) {
            gc.setFill(Color.web("#10b981"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 38));
            gc.fillText("✔", centerX, centerY);
        } else if (progress > 0 && progress < 1.0) {
            gc.setFill(Color.web("#1e293b"));
            gc.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 26));
            int pct = (int) Math.round(progress * 100);
            gc.fillText(pct + "%", centerX, centerY);
        } else if (hasIssues) {
            gc.setFill(Color.web("#0284c7"));
            gc.setFont(Font.font("Segoe UI", FontWeight.EXTRA_BOLD, 28));
            gc.fillText("100%", centerX, centerY);
        } else {
            gc.setFill(Color.web("#2563eb"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 36));
            gc.fillText("🩺", centerX, centerY);
        }
    }

    /* =========================================================================
       View Mode & Details Drawer Toggles
       ========================================================================= */

    @FXML
    public void onSwitchToSimpleView() {
        if (viewSimpleBtn != null) viewSimpleBtn.getStyleClass().setAll("view-mode-pill", "active");
        if (viewTechnicalBtn != null) viewTechnicalBtn.getStyleClass().setAll("view-mode-pill");
        if (technicalSection != null) {
            technicalSection.setVisible(false);
            technicalSection.setManaged(false);
        }
    }

    @FXML
    public void onSwitchToTechnicalView() {
        if (viewTechnicalBtn != null) viewTechnicalBtn.getStyleClass().setAll("view-mode-pill", "active");
        if (viewSimpleBtn != null) viewSimpleBtn.getStyleClass().setAll("view-mode-pill");
        if (technicalSection != null) {
            technicalSection.setVisible(true);
            technicalSection.setManaged(true);
        }
    }

    @FXML
    public void onToggleDetailsView() {
        if (detailsDrawer != null) {
            boolean visible = !detailsDrawer.isVisible();
            detailsDrawer.setVisible(visible);
            detailsDrawer.setManaged(visible);
        }
    }

    @FXML
    public void onCloseDetailsDrawer() {
        if (detailsDrawer != null) {
            detailsDrawer.setVisible(false);
            detailsDrawer.setManaged(false);
        }
    }

    @FXML
    public void onInspectPrivacy() {
        onToggleDetailsView();
    }

    @FXML
    public void onInspectSpace() {
        onToggleDetailsView();
    }

    @FXML
    public void onInspectSpeed() {
        mainViewController.showStartupOptimizer();
    }

    @FXML
    public void onInspectSecurity() {
        mainViewController.showThreatHunter();
    }

    /* =========================================================================
       Hardware & Telemetry Handlers (Preserved from baseline)
       ========================================================================= */

    @FXML
    public void refreshTelemetry() {
        refreshTelemetry(true);
    }

    public void refreshTelemetry(boolean scanHogs) {
        submitAsync(() -> {
            List<DrivePartitionInfo> partitions = systemMetricsService.getPartitions();
            SystemResourceMetrics metrics = systemMetricsService.getSystemResourceMetrics();
            List<ProcessInfo> hogs = scanHogs ? processMonitoringService.getSystemHogs() : List.of();
            long totalBytesRecovered = scanHistoryService.getTotalBytesRecovered();
            long totalItemsDeleted = scanHistoryService.getTotalItemsDeleted();

            Platform.runLater(() -> {
                updatePartitionCards(partitions);
                updateHardwareMetrics(metrics);
                if (scanHogs) {
                    updateHogsContainer(hogs);
                }
                updateAuditMetrics(totalBytesRecovered, totalItemsDeleted);
            });
        });
    }

    private void updatePartitionCards(List<DrivePartitionInfo> partitions) {
        if (drivesContainer == null) return;
        drivesContainer.getChildren().clear();

        if (partitions.isEmpty()) {
            Label emptyLabel = new Label("Aucune partition locale détectée.");
            emptyLabel.getStyleClass().add("text-subtle");
            drivesContainer.getChildren().add(emptyLabel);
            return;
        }

        for (DrivePartitionInfo p : partitions) {
            VBox card = new VBox(8);
            card.getStyleClass().addAll("card", "drive-card");
            card.setPadding(new Insets(14));

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(p.name());
            nameLabel.getStyleClass().addAll("title-4");

            Label typeBadge = new Label(p.type() != null ? p.type() : "Disque");
            typeBadge.getStyleClass().addAll("badge", "badge-subtle");

            header.getChildren().addAll(nameLabel, typeBadge);

            ProgressBar bar = new ProgressBar(p.usedPercentage());
            bar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bar, Priority.ALWAYS);

            if (p.usedPercentage() > 0.90) {
                bar.getStyleClass().add("danger");
            } else if (p.usedPercentage() > 0.75) {
                bar.getStyleClass().add("warning");
            } else {
                bar.getStyleClass().add("success");
            }

            HBox stats = new HBox(16);
            stats.setAlignment(Pos.CENTER_LEFT);

            Label usedLabel = new Label("Utilisé : " + p.formattedUsed() + " (" + p.formattedUsedPercentage() + ")");
            usedLabel.getStyleClass().add("text-bold");

            Label freeLabel = new Label("Libre : " + p.formattedFree());
            Label totalLabel = new Label("Total : " + p.formattedTotal());
            totalLabel.getStyleClass().add("text-subtle");

            stats.getChildren().addAll(usedLabel, freeLabel, totalLabel);
            card.getChildren().addAll(header, bar, stats);
            drivesContainer.getChildren().add(card);
        }
    }

    private void updateHogsContainer(List<ProcessInfo> hogs) {
        if (hogsContainer == null) return;
        hogsContainer.getChildren().clear();

        if (hogs.isEmpty()) {
            Label normalLabel = new Label("✔ Aucun processus lourd ne sature les ressources système actuellement.");
            normalLabel.getStyleClass().addAll("text-small", "text-success");
            hogsContainer.getChildren().add(normalLabel);
            return;
        }

        int count = Math.min(4, hogs.size());
        for (int i = 0; i < count; i++) {
            ProcessInfo proc = hogs.get(i);
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 10, 6, 10));
            row.getStyleClass().addAll("card");

            Label name = new Label(proc.name() + " (PID " + proc.pid() + ")");
            name.getStyleClass().addAll("text-bold");

            Label impact = new Label(proc.getImpactLevel());
            impact.getStyleClass().addAll("badge", proc.isHighCpu() ? "danger" : "warning");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label cpu = new Label("CPU : " + proc.formattedCpu());
            cpu.getStyleClass().add("text-bold");

            Label ram = new Label("RAM : " + proc.formattedRam());
            ram.getStyleClass().add("text-subtle");

            Button inspectBtn = new Button("🔍 Inspecter");
            inspectBtn.getStyleClass().addAll("button-outlined", "button-small");
            inspectBtn.setOnAction(e -> openProcessInspector(proc.pid()));

            row.getChildren().addAll(name, impact, spacer, cpu, ram, inspectBtn);
            hogsContainer.getChildren().add(row);
        }
    }

    private void openProcessInspector(int pid) {
        ProcessDetailsDialog dialog = new ProcessDetailsDialog(
                hogsContainer.getScene().getWindow(),
                pid,
                processMonitoringService,
                executorService
        );
        dialog.showAndWait();
        refreshTelemetry();
    }

    private void updateHardwareMetrics(SystemResourceMetrics metrics) {
        if (cpuLabel != null && cpuProgressBar != null) {
            cpuLabel.setText(metrics.formattedCpu());
            cpuProgressBar.setProgress(Math.max(0.01, metrics.cpuLoadPercentage()));
        }

        if (memoryLabel != null && memoryProgressBar != null) {
            memoryLabel.setText(metrics.formattedMemoryUsed() + " / " + metrics.formattedMemoryTotal() + " (" + metrics.formattedMemoryPercentage() + ")");
            memoryProgressBar.setProgress(metrics.memoryUsagePercentage());
        }
    }

    private void updateAuditMetrics(long totalCleanedBytes, long totalItemsDeleted) {
        if (totalCleanedLabel != null) {
            totalCleanedLabel.setText(FormatUtils.formatBytes(totalCleanedBytes));
        }
        if (totalItemsDeletedLabel != null) {
            totalItemsDeletedLabel.setText(FormatUtils.formatNumber(totalItemsDeleted) + " items");
        }
    }

    @FXML
    public void onOpenProcessMonitor() {
        mainViewController.showProcesses();
    }

    @FXML
    public void onQuickScanLargeFiles() {
        mainViewController.showLargeFiles();
    }

    @FXML
    public void onQuickScanDuplicates() {
        mainViewController.showDuplicates();
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
