package com.cleanpilot.controller;

import com.cleanpilot.model.HardwareDriverInfo;
import com.cleanpilot.service.HardwareDriverService;
import com.cleanpilot.ui.DriverDetailsDialog;
import com.cleanpilot.ui.PrefixTrie;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Component
public class DriversViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(DriversViewController.class);

    @FXML private TextField searchField;
    @FXML private ComboBox<String> classFilterComboBox;
    @FXML private ComboBox<String> statusFilterComboBox;
    @FXML private Button refreshBtn;
    @FXML private Button inspectBtn;
    @FXML private Button backupBtn;
    @FXML private Label totalDevicesLabel;
    @FXML private Label operationalCountLabel;
    @FXML private Label issuesCountLabel;
    @FXML private Label outdatedCountLabel;
    @FXML private Label statusSummaryLabel;

    @FXML private TableView<HardwareDriverInfo> driversTable;
    @FXML private TableColumn<HardwareDriverInfo, String> nameCol;
    @FXML private TableColumn<HardwareDriverInfo, String> classCol;
    @FXML private TableColumn<HardwareDriverInfo, String> statusCol;
    @FXML private TableColumn<HardwareDriverInfo, String> compatibilityCol;
    @FXML private TableColumn<HardwareDriverInfo, String> providerCol;
    @FXML private TableColumn<HardwareDriverInfo, String> versionCol;
    @FXML private TableColumn<HardwareDriverInfo, String> dateCol;
    @FXML private TableColumn<HardwareDriverInfo, String> infCol;
    @FXML private TableColumn<HardwareDriverInfo, Boolean> signedCol;

    private final HardwareDriverService driverService;
    private final ExecutorService executorService;

    private final ObservableList<HardwareDriverInfo> masterList = FXCollections.observableArrayList();
    private FilteredList<HardwareDriverInfo> filteredList;
    private final PrefixTrie<HardwareDriverInfo> prefixTrie = new PrefixTrie<>();

    public DriversViewController(HardwareDriverService driverService, ExecutorService executorService) {
        this.driverService = driverService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupFilters();
        setupRowSelection();
        driversTable.setPlaceholder(new Label("Click 'Scan Drivers' to inspect installed device drivers via PnPUtil."));
    }

    private void setupTableColumns() {
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        classCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().pnpClass()));
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status()));
        compatibilityCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().compatibilityStatus()));
        providerCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayDriverProvider()));
        versionCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayDriverVersion()));
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().driverDate().isBlank() ? "N/A" : data.getValue().driverDate()));
        infCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().displayInfName()));
        signedCol.setCellValueFactory(data -> new SimpleBooleanProperty(data.getValue().isSigned()));

        // Status column styling
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item);
                    HardwareDriverInfo row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null && row.isHealthy()) {
                        setStyle("-fx-text-fill: #2da44e; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #cf222e; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Release Date column styling (highlight outdated)
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    HardwareDriverInfo row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null && row.isOutdated()) {
                        setStyle("-fx-text-fill: #d4a72c; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Signed badge styling
        signedCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean signed, boolean empty) {
                super.updateItem(signed, empty);
                if (empty || signed == null) {
                    setText(null);
                    setStyle("");
                } else {
                    if (signed) {
                        setText("✓ Signed");
                        setStyle("-fx-text-fill: #0969da; -fx-font-weight: bold;");
                    } else {
                        setText("⚠ Unsigned");
                        setStyle("-fx-text-fill: #d4a72c; -fx-font-weight: bold;");
                    }
                }
            }
        });
    }

    private void setupFilters() {
        classFilterComboBox.getItems().add("All Classes");
        classFilterComboBox.setValue("All Classes");
        classFilterComboBox.setOnAction(e -> applyFilter());

        statusFilterComboBox.getItems().addAll(
                "All Statuses",
                "Operational Only",
                "Issues / Disabled Only",
                "Outdated Only (>5 yrs)",
                "Unsigned Only"
        );
        statusFilterComboBox.setValue("All Statuses");
        statusFilterComboBox.setOnAction(e -> applyFilter());

        filteredList = new FilteredList<>(masterList, p -> true);
        SortedList<HardwareDriverInfo> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(driversTable.comparatorProperty());
        driversTable.setItems(sortedList);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }

    private void setupRowSelection() {
        driversTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            if (inspectBtn != null) {
                inspectBtn.setDisable(!hasSelection);
            }
        });

        driversTable.setRowFactory(tv -> {
            TableRow<HardwareDriverInfo> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showSelectedDriverDetails();
                }
            });
            return row;
        });
    }

    @FXML
    public void loadDriverData() {
        if (statusSummaryLabel != null) {
            statusSummaryLabel.setText("Scanning hardware bus and driver catalog...");
        }
        if (refreshBtn != null) {
            refreshBtn.setDisable(true);
        }

        submitAsync(() -> {
            try {
                List<HardwareDriverInfo> drivers = driverService.scanHardwareAndDrivers();

                Platform.runLater(() -> {
                    masterList.setAll(drivers);

                    // Rebuild Trie
                    rebuildTrie(drivers);

                    // Populate classes dropdown
                    Set<String> uniqueClasses = new TreeSet<>();
                    int operationalCount = 0;
                    int issuesCount = 0;
                    int outdatedCount = 0;

                    for (HardwareDriverInfo d : drivers) {
                        if (d.pnpClass() != null && !d.pnpClass().isBlank()) {
                            uniqueClasses.add(d.pnpClass());
                        }
                        if (d.isHealthy()) {
                            operationalCount++;
                        } else {
                            issuesCount++;
                        }
                        if (d.isOutdated()) {
                            outdatedCount++;
                        }
                    }

                    String currentClass = classFilterComboBox.getValue();
                    classFilterComboBox.getItems().clear();
                    classFilterComboBox.getItems().add("All Classes");
                    classFilterComboBox.getItems().addAll(uniqueClasses);
                    classFilterComboBox.setValue(uniqueClasses.contains(currentClass) ? currentClass : "All Classes");

                    if (totalDevicesLabel != null) totalDevicesLabel.setText(String.valueOf(drivers.size()));
                    if (operationalCountLabel != null) operationalCountLabel.setText(String.valueOf(operationalCount));
                    if (issuesCountLabel != null) issuesCountLabel.setText(String.valueOf(issuesCount));
                    if (outdatedCountLabel != null) outdatedCountLabel.setText(String.valueOf(outdatedCount));
                    if (statusSummaryLabel != null) {
                        String summary = String.format(
                                "%d hardware components active (%d healthy, %d problem/disabled, %d legacy/outdated >5 yrs).",
                                drivers.size(), operationalCount, issuesCount, outdatedCount
                        );
                        statusSummaryLabel.setText(summary);
                    }
                    if (refreshBtn != null) refreshBtn.setDisable(false);
                });
            } catch (Exception e) {
                log.error("Failed to scan hardware drivers: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    if (statusSummaryLabel != null) statusSummaryLabel.setText("Failed to scan drivers: " + e.getMessage());
                    if (refreshBtn != null) refreshBtn.setDisable(false);
                });
            }
        });
    }

    private void rebuildTrie(List<HardwareDriverInfo> drivers) {
        prefixTrie.clear();
        for (HardwareDriverInfo item : drivers) {
            prefixTrie.insertTokens(item.name(), item);
            prefixTrie.insertTokens(item.displayManufacturer(), item);
            prefixTrie.insertTokens(item.displayDriverProvider(), item);
            prefixTrie.insertTokens(item.pnpClass(), item);
            prefixTrie.insertTokens(item.deviceId(), item);
            prefixTrie.insertTokens(item.displayInfName(), item);
        }
    }

    private void applyFilter() {
        String query = searchField.getText() != null ? searchField.getText().trim() : "";
        String selectedClass = classFilterComboBox.getValue();
        String selectedStatus = statusFilterComboBox.getValue();

        Set<HardwareDriverInfo> matchedByTrie = null;
        if (!query.isBlank()) {
            matchedByTrie = new HashSet<>(prefixTrie.searchByPrefix(query));
        }

        final Set<HardwareDriverInfo> finalMatched = matchedByTrie;
        filteredList.setPredicate(driver -> {
            // Trie text match
            if (finalMatched != null) {
                if (!finalMatched.contains(driver)) {
                    // Fallback substring check
                    String lowerQuery = query.toLowerCase(Locale.ROOT);
                    boolean match = driver.name().toLowerCase(Locale.ROOT).contains(lowerQuery)
                            || driver.displayManufacturer().toLowerCase(Locale.ROOT).contains(lowerQuery)
                            || driver.displayDriverProvider().toLowerCase(Locale.ROOT).contains(lowerQuery)
                            || driver.pnpClass().toLowerCase(Locale.ROOT).contains(lowerQuery)
                            || driver.deviceId().toLowerCase(Locale.ROOT).contains(lowerQuery)
                            || driver.displayInfName().toLowerCase(Locale.ROOT).contains(lowerQuery);
                    if (!match) return false;
                }
            }

            // Class match
            if (selectedClass != null && !"All Classes".equalsIgnoreCase(selectedClass)) {
                if (!selectedClass.equalsIgnoreCase(driver.pnpClass())) {
                    return false;
                }
            }

            // Status filter match
            if (selectedStatus != null && !"All Statuses".equalsIgnoreCase(selectedStatus)) {
                if ("Operational Only".equalsIgnoreCase(selectedStatus) && !driver.isHealthy()) {
                    return false;
                }
                if ("Issues / Disabled Only".equalsIgnoreCase(selectedStatus) && driver.isHealthy()) {
                    return false;
                }
                if ("Outdated Only (>5 yrs)".equalsIgnoreCase(selectedStatus) && !driver.isOutdated()) {
                    return false;
                }
                if ("Unsigned Only".equalsIgnoreCase(selectedStatus) && driver.isSigned()) {
                    return false;
                }
            }

            return true;
        });
    }

    @FXML
    public void onBackupDrivers() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Destination Directory for Driver Store Backup");
        java.io.File dir = chooser.showDialog(driversTable.getScene().getWindow());
        if (dir == null) return;

        if (statusSummaryLabel != null) {
            statusSummaryLabel.setText("Exporting driver store packages to " + dir.getAbsolutePath() + "...");
        }

        submitAsync(() -> {
            boolean success = driverService.backupAllDrivers(dir.getAbsolutePath());
            Platform.runLater(() -> {
                if (success) {
                    if (statusSummaryLabel != null) {
                        statusSummaryLabel.setText("Driver packages exported successfully to " + dir.getAbsolutePath());
                    }
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Driver Backup Complete");
                    alert.setHeaderText("Drivers Exported Successfully");
                    alert.setContentText("OEM and third-party driver packages were exported to:\n" + dir.getAbsolutePath());
                    alert.showAndWait();
                } else {
                    if (statusSummaryLabel != null) {
                        statusSummaryLabel.setText("Failed to export driver packages.");
                    }
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Driver Backup Failed");
                    alert.setHeaderText("Export Operation Failed");
                    alert.setContentText("Windows pnputil could not export driver packages to the chosen directory.");
                    alert.showAndWait();
                }
            });
        });
    }

    @FXML
    public void showSelectedDriverDetails() {
        HardwareDriverInfo selected = driversTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        DriverDetailsDialog.show(driversTable.getScene().getWindow(), selected, driverService, executorService);
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
