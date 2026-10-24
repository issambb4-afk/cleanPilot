package com.cleanpilot.controller;

import com.cleanpilot.model.ProcessThreatInfo;
import com.cleanpilot.model.ProcessThreatInfo.ThreatLevel;
import com.cleanpilot.model.ThreatExplanation;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.service.RogueProcessThreatService;
import com.cleanpilot.ui.OperationConfirmationDialog;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Controller for Rogue Process & Threat Hunter view.
 * Inspects running processes for stealth mining, reverse shells, masquerading,
 * unverified signatures, and high entropy payloads.
 *
 * Implements strict fail-safe safeguards protecting essential Windows system processes.
 */
@Component
public class ThreatHunterViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ThreatHunterViewController.class);

    @FXML private Label shieldBadge;
    @FXML private Button scanBtn;
    @FXML private Button freezeBtn;
    @FXML private Button resumeBtn;
    @FXML private Button terminateBtn;

    @FXML private Label overallThreatLabel;
    @FXML private Label threatSummaryLabel;
    @FXML private Label threatCountLabel;
    @FXML private Label beaconCountLabel;
    @FXML private Label safetyNoticeLabel;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> threatFilterComboBox;

    @FXML private TableView<ProcessThreatInfo> threatTable;
    @FXML private TableColumn<ProcessThreatInfo, String> threatLevelCol;
    @FXML private TableColumn<ProcessThreatInfo, Number> riskScoreCol;
    @FXML private TableColumn<ProcessThreatInfo, String> processCol;
    @FXML private TableColumn<ProcessThreatInfo, String> protectionCol;
    @FXML private TableColumn<ProcessThreatInfo, String> cpuCol;
    @FXML private TableColumn<ProcessThreatInfo, String> ramCol;
    @FXML private TableColumn<ProcessThreatInfo, String> socketsCol;
    @FXML private TableColumn<ProcessThreatInfo, String> signatureCol;
    @FXML private TableColumn<ProcessThreatInfo, String> entropyCol;
    @FXML private TableColumn<ProcessThreatInfo, String> pathCol;

    @FXML private Label selectedProcessTitle;
    @FXML private Label selectedProcessStatusBadge;
    @FXML private Button copyPathBtn;
    @FXML private Button closeDetailsBtn;
    @FXML private VBox detailsCard;
    @FXML private TabPane detailsTabPane;
    @FXML private ListView<String> threatReasonsListView;
    @FXML private Label selectedPathLabel;
    @FXML private Label selectedCmdLabel;
    @FXML private Label selectedSignerLabel;
    @FXML private Label selectedSocketsLabel;

    private final RogueProcessThreatService threatService;
    private final ExecutorService executorService;
    private final com.cleanpilot.service.LocalizationService localizationService;

    private final ObservableList<ProcessThreatInfo> masterList = FXCollections.observableArrayList();
    private FilteredList<ProcessThreatInfo> filteredList;

    public ThreatHunterViewController(
            RogueProcessThreatService threatService,
            ExecutorService executorService,
            com.cleanpilot.service.LocalizationService localizationService
    ) {
        this.threatService = threatService;
        this.executorService = executorService;
        this.localizationService = localizationService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupFilters();
        setupReasonsListView();
        setupSelectionListener();
        threatTable.setPlaceholder(new Label(localizationService.get("threat.placeholder")));
    }

    private void setupReasonsListView() {
        threatReasonsListView.setCellFactory(lv -> new ListCell<>() {
            private final Label label = new Label();
            {
                label.setWrapText(true);
                label.maxWidthProperty().bind(lv.widthProperty().subtract(28));
                setPrefWidth(0);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    label.setText(item);
                    if (item.startsWith("---")) {
                        label.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-accent-emphasis; -fx-padding: 4 0 2 0;");
                    } else if (item.startsWith(localizationService.get("threat.details.rationale")) || item.contains("Rationale")) {
                        label.setStyle("-fx-text-fill: -color-danger-emphasis; -fx-font-weight: bold;");
                    } else if (item.startsWith(localizationService.get("threat.details.legitimate")) || item.contains("Benign")) {
                        label.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-style: italic;");
                    } else if (item.startsWith(localizationService.get("threat.details.actions")) || item.contains("Investigation")) {
                        label.setStyle("-fx-text-fill: -color-warning-emphasis;");
                    } else {
                        label.setStyle("-fx-text-fill: -color-fg-default;");
                    }
                    setGraphic(label);
                    setText(null);
                }
            }
        });
    }

    private void setupTableColumns() {
        threatLevelCol.setCellValueFactory(cell -> {
            String localized = switch (cell.getValue().threatLevel()) {
                case CRITICAL -> localizationService.get("threat.status.critical");
                case HIGH -> localizationService.get("threat.status.high");
                case SUSPICIOUS -> localizationService.get("threat.status.investigate");
                case SAFE -> localizationService.get("threat.status.normal");
            };
            return new SimpleStringProperty(localized);
        });
        threatLevelCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item);
                    ProcessThreatInfo row = getTableView().getItems().get(getIndex());
                    String color = switch (row.threatLevel()) {
                        case CRITICAL -> "-fx-text-fill: -color-danger-emphasis; -fx-font-weight: bold;";
                        case HIGH -> "-fx-text-fill: #e65100; -fx-font-weight: bold;";
                        case SUSPICIOUS -> "-fx-text-fill: #f57f17; -fx-font-weight: bold;";
                        case SAFE -> "-fx-text-fill: -color-success-emphasis;";
                    };
                    setStyle(color);
                }
            }
        });

        riskScoreCol.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().riskScore()));
        processCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().displayProcess()));

        protectionCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isProtectedSystem()
                        ? localizationService.get("threat.shield.protected_label")
                        : localizationService.get("threat.shield.userspace_label")
        ));
        protectionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    ProcessThreatInfo row = getTableView().getItems().get(getIndex());
                    if (row.isProtectedSystem()) {
                        setStyle("-fx-text-fill: -color-success-emphasis; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: -color-fg-muted;");
                    }
                }
            }
        });

        cpuCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().formattedCpu()));
        ramCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().formattedMemory()));
        socketsCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().outboundSocketCount() > 0 ? cell.getValue().outboundSocketCount() + " public" : "-"
        ));
        signatureCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isSigned()
                        ? "✓ " + abbreviate(cell.getValue().signer(), 18)
                        : "⚠ " + localizationService.get("threat.col.unsigned")
        ));
        entropyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().formattedEntropy()));
        entropyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    ProcessThreatInfo row = getTableView().getItems().get(getIndex());
                    if (row.entropy() >= 7.20) {
                        setStyle("-fx-text-fill: -color-danger-emphasis; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        pathCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().executablePath()));

        filteredList = new FilteredList<>(masterList, p -> true);
        SortedList<ProcessThreatInfo> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(threatTable.comparatorProperty());
        threatTable.setItems(sortedList);
    }

    private void setupFilters() {
        threatFilterComboBox.setItems(FXCollections.observableArrayList(
                localizationService.get("threat.filter.all"),
                localizationService.get("threat.filter.suspicious"),
                localizationService.get("threat.filter.critical"),
                localizationService.get("threat.filter.protected")
        ));
        threatFilterComboBox.getSelectionModel().select(0);

        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter());
        threatFilterComboBox.valueProperty().addListener((obs, oldV, newV) -> applyFilter());
    }

    private void applyFilter() {
        String search = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        int filterIndex = threatFilterComboBox.getSelectionModel().getSelectedIndex();

        filteredList.setPredicate(item -> {
            if (filterIndex == 1 && item.riskScore() < 20) return false;
            if (filterIndex == 2 && item.riskScore() < 50) return false;
            if (filterIndex == 3 && !item.isProtectedSystem()) return false;

            if (search.isEmpty()) return true;

            boolean matchName = item.processName().toLowerCase(Locale.ROOT).contains(search);
            boolean matchPid = String.valueOf(item.pid()).contains(search);
            boolean matchPath = item.executablePath().toLowerCase(Locale.ROOT).contains(search);
            boolean matchReasons = item.threatReasons().stream().anyMatch(r -> r.toLowerCase(Locale.ROOT).contains(search));

            return matchName || matchPid || matchPath || matchReasons;
        });
    }

    private void setupSelectionListener() {
        threatTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
            if (selected == null) {
                selectedProcessTitle.setText(localizationService.get("threat.details.no_selection"));
                selectedProcessStatusBadge.setText("");
                selectedProcessStatusBadge.getStyleClass().setAll("badge");
                threatReasonsListView.getItems().clear();
                selectedPathLabel.setText("-");
                selectedCmdLabel.setText("-");
                selectedSignerLabel.setText("-");
                selectedSocketsLabel.setText(localizationService.get("threat.details.no_sockets"));

                terminateBtn.setDisable(true);
                freezeBtn.setDisable(true);
                resumeBtn.setDisable(true);
                copyPathBtn.setDisable(true);
                closeDetailsBtn.setDisable(true);
            } else {
                selectedProcessTitle.setText(selected.displayProcess());
                String badgeLevel = switch (selected.threatLevel()) {
                    case CRITICAL -> localizationService.get("threat.status.critical");
                    case HIGH -> localizationService.get("threat.status.high");
                    case SUSPICIOUS -> localizationService.get("threat.status.investigate");
                    case SAFE -> localizationService.get("threat.status.normal");
                };
                selectedProcessStatusBadge.setText(badgeLevel + " (Score " + selected.riskScore() + ")");
                selectedProcessStatusBadge.getStyleClass().setAll("badge", selected.threatLevel().getStyleClass());

                List<String> items = new ArrayList<>();
                items.addAll(selected.threatReasons());
                ThreatExplanation exp = selected.explanation();
                if (exp != null) {
                    items.add(localizationService.get("threat.details.explainability"));
                    items.add(localizationService.get("threat.details.rationale") + " " + exp.anomalyRationale());
                    if (!exp.potentialLegitimateUses().isEmpty()) {
                        items.add(localizationService.get("threat.details.legitimate") + " " + String.join("; ", exp.potentialLegitimateUses()));
                    }
                    if (!exp.recommendedActions().isEmpty()) {
                        items.add(localizationService.get("threat.details.actions") + " " + String.join("; ", exp.recommendedActions()));
                    }
                }
                threatReasonsListView.setItems(FXCollections.observableArrayList(items));
                selectedPathLabel.setText(selected.executablePath() != null ? selected.executablePath() : "-");
                selectedCmdLabel.setText(selected.commandLine() != null && !selected.commandLine().isBlank() ? selected.commandLine() : "-");
                selectedSignerLabel.setText(selected.signer() != null && !selected.signer().isBlank() ? selected.signer() : "-");

                if (selected.foreignConnections().isEmpty()) {
                    selectedSocketsLabel.setText(localizationService.get("threat.details.no_sockets"));
                } else {
                    selectedSocketsLabel.setText(String.join(", ", selected.foreignConnections()));
                }

                // FAIL-SAFE RULE: Disable termination and freeze buttons if process is a protected OS binary
                boolean isProtected = selected.isProtectedSystem();
                terminateBtn.setDisable(isProtected);
                freezeBtn.setDisable(isProtected || selected.isSuspended());
                resumeBtn.setDisable(isProtected || !selected.isSuspended());
                copyPathBtn.setDisable(selected.executablePath() == null || selected.executablePath().isBlank());
                closeDetailsBtn.setDisable(false);

                if (isProtected) {
                    safetyNoticeLabel.setText(localizationService.format("threat.shield.protected_notice", selected.processName()));
                } else {
                    safetyNoticeLabel.setText(localizationService.get("threat.shield.userspace_notice"));
                }
            }
        });
    }

    @FXML
    public void onCloseDetails() {
        threatTable.getSelectionModel().clearSelection();
    }

    @FXML
    public void onCopyPath() {
        ProcessThreatInfo selected = threatTable.getSelectionModel().getSelectedItem();
        if (selected != null && selected.executablePath() != null && !selected.executablePath().isBlank()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(selected.executablePath());
            Clipboard.getSystemClipboard().setContent(content);
            safetyNoticeLabel.setText(localizationService.get("threat.details.copy_path.copied"));
        }
    }

    @FXML
    public void onCopyCmd() {
        ProcessThreatInfo selected = threatTable.getSelectionModel().getSelectedItem();
        if (selected != null && selected.commandLine() != null && !selected.commandLine().isBlank()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(selected.commandLine());
            Clipboard.getSystemClipboard().setContent(content);
            safetyNoticeLabel.setText(localizationService.get("threat.details.copy_cmd.copied"));
        }
    }

    @FXML
    public void onScanThreats() {
        scanBtn.setDisable(true);
        scanBtn.setText(localizationService.get("threat.btn.scanning"));

        submitAsync(() -> {
            try {
                List<ProcessThreatInfo> results = threatService.scanProcessesForThreats();

                Platform.runLater(() -> {
                    masterList.setAll(results);
                    updateKpis(results);
                    scanBtn.setDisable(false);
                    scanBtn.setText(localizationService.get("threat.btn.scan_ready"));
                });
            } catch (Exception e) {
                log.error("Failed to complete threat correlation scan: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    scanBtn.setDisable(false);
                    scanBtn.setText(localizationService.get("threat.btn.scan_ready"));
                });
            }
        });
    }

    private void updateKpis(List<ProcessThreatInfo> list) {
        long elevatedCount = list.stream().filter(p -> p.riskScore() >= 20).count();
        long foreignSockets = list.stream().mapToInt(ProcessThreatInfo::outboundSocketCount).sum();

        threatCountLabel.setText(String.valueOf(elevatedCount));
        beaconCountLabel.setText(String.valueOf(foreignSockets));

        boolean hasCritical = list.stream().anyMatch(p -> p.threatLevel() == ThreatLevel.CRITICAL);
        boolean hasHigh = list.stream().anyMatch(p -> p.threatLevel() == ThreatLevel.HIGH);

        if (hasCritical) {
            overallThreatLabel.setText(localizationService.get("threat.status.critical"));
            overallThreatLabel.setStyle("-fx-text-fill: -color-danger-emphasis;");
            threatSummaryLabel.setText(localizationService.get("threat.summary.critical"));
        } else if (hasHigh) {
            overallThreatLabel.setText(localizationService.get("threat.status.high"));
            overallThreatLabel.setStyle("-fx-text-fill: #e65100;");
            threatSummaryLabel.setText(localizationService.get("threat.summary.high"));
        } else if (elevatedCount > 0) {
            overallThreatLabel.setText(localizationService.get("threat.status.investigate"));
            overallThreatLabel.setStyle("-fx-text-fill: #f57f17;");
            threatSummaryLabel.setText(localizationService.format("threat.status.suspicious_count", elevatedCount));
        } else {
            overallThreatLabel.setText(localizationService.get("threat.status.normal"));
            overallThreatLabel.setStyle("-fx-text-fill: -color-success-emphasis;");
            threatSummaryLabel.setText(localizationService.get("threat.summary.normal"));
        }
    }

    @FXML
    public void onFreezeSelected() {
        ProcessThreatInfo selected = threatTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (selected.isProtectedSystem()) {
            showSafetyAlert("Cannot Freeze Essential System Process",
                    "CleanPilot fail-safe strictly protects core operating system binaries from thread suspension.");
            return;
        }

        Window window = threatTable != null && threatTable.getScene() != null
                ? threatTable.getScene().getWindow() : null;

        OperationPreview preview = OperationPreview.builder()
                .operationType("PROCESS_FREEZE")
                .riskClassification(RiskClassification.MODERATE_RISK)
                .targetCount(1)
                .targetDescriptions(List.of(selected.displayProcess() + " (" + selected.executablePath() + ")"))
                .estimatedBytes(0)
                .exactByteEstimate(false)
                .recoveryMechanism("Thread execution paused (can be resumed via 'Resume Process')")
                .irreversible(false)
                .requiresElevation(false)
                .addWarning("This will immediately pause thread execution, halting active CPU and network activity.")
                .build();

        if (OperationConfirmationDialog.confirm(window, preview)) {
            submitAsync(() -> {
                boolean ok = threatService.suspendRogueProcess(selected.pid());
                Platform.runLater(() -> {
                    if (ok) {
                        onScanThreats();
                    } else {
                        showSafetyAlert("Freeze Failed", "Could not freeze process PID " + selected.pid());
                    }
                });
            });
        }
    }

    @FXML
    public void onResumeSelected() {
        ProcessThreatInfo selected = threatTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        submitAsync(() -> {
            boolean ok = threatService.resumeRogueProcess(selected.pid());
            Platform.runLater(this::onScanThreats);
        });
    }

    @FXML
    public void onTerminateSelected() {
        ProcessThreatInfo selected = threatTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (selected.isProtectedSystem()) {
            showSafetyAlert("Termination Refused (Fail-Safe Shield)",
                    "CleanPilot refused to terminate this process because it is a verified core Windows component.");
            return;
        }

        Window window = threatTable != null && threatTable.getScene() != null
                ? threatTable.getScene().getWindow() : null;

        OperationPreview preview = OperationPreview.builder()
                .operationType("PROCESS_TERMINATION")
                .riskClassification(RiskClassification.HIGH_RISK)
                .targetCount(1)
                .targetDescriptions(List.of(selected.displayProcess() + " (" + selected.executablePath() + ")"))
                .estimatedBytes(0)
                .exactByteEstimate(false)
                .recoveryMechanism("Permanent process termination (unrecoverable active memory state)")
                .irreversible(true)
                .requiresElevation(false)
                .addWarning("CleanPilot will attempt a graceful exit first, then proceed to forced kill if unresponsive.")
                .addWarning("Unsaved data inside this process will be lost.")
                .build();

        if (OperationConfirmationDialog.confirm(window, preview)) {
            submitAsync(() -> {
                boolean terminated = threatService.terminateRogueProcess(selected.pid());
                Platform.runLater(() -> {
                    if (terminated) {
                        onScanThreats();
                    } else {
                        showSafetyAlert("Termination Failed", "Could not terminate PID " + selected.pid() + ". It may require elevated Administrator rights.");
                    }
                });
            });
        }
    }

    private void showSafetyAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String abbreviate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 2) + ".." : str;
    }

    private void submitAsync(Runnable task) {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            return;
        }
        try {
            executorService.submit(task);
        } catch (RejectedExecutionException ex) {
            log.debug("Threat task rejected: executor is shut down.");
        }
    }
}
