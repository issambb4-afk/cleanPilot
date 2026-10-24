package com.cleanpilot.controller;

import com.cleanpilot.model.ActiveSocketInfo;
import com.cleanpilot.model.NetworkOptimizationResult;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.service.NetworkOptimizationService;
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
 * Controller for DNS Cache Optimizer & Socket Leak Hunter view.
 */
@Component
public class NetworkOptimizerViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(NetworkOptimizerViewController.class);

    @FXML private Label adminBadge;
    @FXML private Button refreshBtn;
    @FXML private Button optimizeDnsBtn;
    @FXML private Label totalSocketsLabel;
    @FXML private Label socketLeaksLabel;
    @FXML private Label stackStatusLabel;
    @FXML private Label statusBannerLabel;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> stateFilterComboBox;
    @FXML private TableView<ActiveSocketInfo> socketsTable;
    @FXML private TableColumn<ActiveSocketInfo, String> protoCol;
    @FXML private TableColumn<ActiveSocketInfo, String> localCol;
    @FXML private TableColumn<ActiveSocketInfo, String> foreignCol;
    @FXML private TableColumn<ActiveSocketInfo, String> stateCol;
    @FXML private TableColumn<ActiveSocketInfo, String> processCol;
    @FXML private TableColumn<ActiveSocketInfo, String> leakCol;

    private final NetworkOptimizationService networkService;
    private final ExecutorService executorService;

    private final ObservableList<ActiveSocketInfo> masterList = FXCollections.observableArrayList();
    private FilteredList<ActiveSocketInfo> filteredList;

    public NetworkOptimizerViewController(NetworkOptimizationService networkService, ExecutorService executorService) {
        this.networkService = networkService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        checkAdminStatus();
        setupTable();
        setupFilters();
        onRefresh();
    }

    private void checkAdminStatus() {
        boolean isAdmin = WindowsSecurityUtils.isRunningAsAdmin();
        if (adminBadge != null) {
            if (isAdmin) {
                adminBadge.setText("🛡️ Administrator");
                adminBadge.setStyle("-fx-background-color: #2da44e22; -fx-text-fill: #2da44e; -fx-font-weight: bold;");
            } else {
                adminBadge.setText("👤 Standard User");
                adminBadge.setStyle("-fx-background-color: #0969da22; -fx-text-fill: #0969da;");
            }
        }
    }

    private void setupTable() {
        protoCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().protocol()));
        localCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().localAddress()));
        foreignCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().foreignAddress()));
        stateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().state()));
        processCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().displayProcess()));
        leakCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isSuspectLeak() ? "⚠ Socket Leak / Hang" : "Healthy"));

        stateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String state, boolean empty) {
                super.updateItem(state, empty);
                if (empty || state == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(state);
                    if ("ESTABLISHED".equalsIgnoreCase(state)) {
                        setStyle("-fx-text-fill: #2da44e; -fx-font-weight: bold;");
                    } else if ("CLOSE_WAIT".equalsIgnoreCase(state) || "TIME_WAIT".equalsIgnoreCase(state)) {
                        setStyle("-fx-text-fill: #cf222e; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        leakCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String verdict, boolean empty) {
                super.updateItem(verdict, empty);
                if (empty || verdict == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(verdict);
                    if (verdict.contains("Leak")) {
                        setStyle("-fx-text-fill: #d4a72c; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #2da44e;");
                    }
                }
            }
        });

        filteredList = new FilteredList<>(masterList, p -> true);
        SortedList<ActiveSocketInfo> sorted = new SortedList<>(filteredList);
        sorted.comparatorProperty().bind(socketsTable.comparatorProperty());
        socketsTable.setItems(sorted);
    }

    private void setupFilters() {
        stateFilterComboBox.getItems().addAll("All States", "ESTABLISHED", "LISTENING", "CLOSE_WAIT / TIME_WAIT Only", "Socket Leaks Only");
        stateFilterComboBox.setValue("All States");
        stateFilterComboBox.setOnAction(e -> applyFilter());

        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
    }

    @FXML
    public void onRefresh() {
        if (refreshBtn != null) refreshBtn.setDisable(true);
        if (statusBannerLabel != null) statusBannerLabel.setText("Scanning network sockets...");

        submitAsync(() -> {
            List<ActiveSocketInfo> sockets = networkService.scanActiveSockets();
            int leaks = (int) sockets.stream().filter(ActiveSocketInfo::isSuspectLeak).count();

            Platform.runLater(() -> {
                masterList.setAll(sockets);
                if (totalSocketsLabel != null) totalSocketsLabel.setText(String.valueOf(sockets.size()));
                if (socketLeaksLabel != null) socketLeaksLabel.setText(String.valueOf(leaks));
                if (statusBannerLabel != null) {
                    statusBannerLabel.setText(leaks == 0
                            ? "All active connections healthy. No orphaned socket leaks detected."
                            : leaks + " unclosed connection handles detected (CLOSE_WAIT / TIME_WAIT). Stack optimization recommended.");
                }
                if (refreshBtn != null) refreshBtn.setDisable(false);
            });
        });
    }

    private void applyFilter() {
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String state = stateFilterComboBox.getValue();

        filteredList.setPredicate(socket -> {
            if (!q.isBlank()) {
                boolean match = socket.localAddress().toLowerCase(Locale.ROOT).contains(q)
                        || socket.foreignAddress().toLowerCase(Locale.ROOT).contains(q)
                        || socket.displayProcess().toLowerCase(Locale.ROOT).contains(q)
                        || socket.state().toLowerCase(Locale.ROOT).contains(q);
                if (!match) return false;
            }

            if (state != null && !"All States".equalsIgnoreCase(state)) {
                if ("CLOSE_WAIT / TIME_WAIT Only".equalsIgnoreCase(state) || "Socket Leaks Only".equalsIgnoreCase(state)) {
                    return socket.isSuspectLeak();
                }
                return state.equalsIgnoreCase(socket.state());
            }

            return true;
        });
    }

    @FXML
    public void onOptimizeNetwork() {
        OperationPreview preview = OperationPreview.builder("Network Stack Optimization & DNS Flush", RiskClassification.MODERATE_RISK)
                .targetDescriptions(List.of(
                        "Local DNS Client Resolver Cache (Clear-DnsClientCache / ipconfig /flushdns)",
                        "Windows Sockets (Winsock) Catalog (netsh winsock reset)"
                ))
                .estimatedBytes(0, true)
                .irreversible(false)
                .recoveryMechanism("Reversible: DNS cache will rebuild automatically. Winsock catalog will be reset to default configuration.")
                .warnings(List.of(
                        "Active network connections and VPNs will momentarily disconnect",
                        "A Windows computer restart is required for Winsock catalog reset to take full effect"
                ))
                .build();

        Window window = optimizeDnsBtn != null && optimizeDnsBtn.getScene() != null ? optimizeDnsBtn.getScene().getWindow() : null;
        if (!OperationConfirmationDialog.confirm(window, preview)) {
            return;
        }

        if (optimizeDnsBtn != null) optimizeDnsBtn.setDisable(true);
        if (statusBannerLabel != null) statusBannerLabel.setText("Flushing DNS resolver cache and optimizing network stack...");

        submitAsync(() -> {
            NetworkOptimizationResult result = networkService.optimizeNetworkStack();

            Platform.runLater(() -> {
                if (optimizeDnsBtn != null) optimizeDnsBtn.setDisable(false);
                if (statusBannerLabel != null) statusBannerLabel.setText(result.statusMessage());
                Alert alert = new Alert(result.dnsFlushed() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
                alert.setTitle("Network Optimization");
                alert.setHeaderText("Network Stack Optimization Complete");
                alert.setContentText(result.statusMessage() + "\n\nDNS entries were refreshed to resolve connectivity stalls.\nNote: A restart is recommended if Winsock catalog was reset.");
                alert.showAndWait();
                onRefresh();
            });
        });
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
