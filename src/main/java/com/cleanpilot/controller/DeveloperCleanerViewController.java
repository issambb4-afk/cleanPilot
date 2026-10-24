package com.cleanpilot.controller;

import com.cleanpilot.model.DeveloperArtifactInfo;
import com.cleanpilot.model.DeveloperArtifactInfo.ArtifactType;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.service.DeveloperCleanupService;
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
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller for the Developer & Power-User Disk Hunter view.
 */
@Component
public class DeveloperCleanerViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(DeveloperCleanerViewController.class);

    private final DeveloperCleanupService developerService;
    private final ExecutorService executorService;

    @FXML private Label totalReclaimableLabel;
    @FXML private Label totalArtifactsCountLabel;
    @FXML private Label nodeModulesSizeLabel;
    @FXML private Label nodeModulesCountLabel;
    @FXML private Label wslSizeLabel;

    @FXML private ToggleButton filterAllBtn;
    @FXML private ToggleButton filterNodeBtn;
    @FXML private ToggleButton filterBuildsBtn;
    @FXML private ToggleButton filterPythonBtn;
    @FXML private ToggleButton filterWslBtn;
    @FXML private TextField searchField;

    @FXML private TableView<DeveloperArtifactInfo> artifactTable;
    @FXML private TableColumn<DeveloperArtifactInfo, Boolean> selectCol;
    @FXML private TableColumn<DeveloperArtifactInfo, String> typeCol;
    @FXML private TableColumn<DeveloperArtifactInfo, String> projectCol;
    @FXML private TableColumn<DeveloperArtifactInfo, String> nameCol;
    @FXML private TableColumn<DeveloperArtifactInfo, String> sizeCol;
    @FXML private TableColumn<DeveloperArtifactInfo, String> fileCountCol;
    @FXML private TableColumn<DeveloperArtifactInfo, String> modifiedCol;
    @FXML private TableColumn<DeveloperArtifactInfo, String> pathCol;

    @FXML private Label selectionSummaryLabel;
    @FXML private Button scanBtn;
    @FXML private Button cleanSelectedBtn;
    @FXML private Button compactWslBtn;

    private final ObservableList<DeveloperArtifactInfo> masterList = FXCollections.observableArrayList();
    private FilteredList<DeveloperArtifactInfo> filteredList;
    private final List<Path> customFolders = new ArrayList<>();
    private final AtomicBoolean scanCancelFlag = new AtomicBoolean(false);

    public DeveloperCleanerViewController(DeveloperCleanupService developerService, ExecutorService executorService) {
        this.developerService = developerService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupFilters();
        if (artifactTable != null) {
            artifactTable.setPlaceholder(new Label("Click 'Scan Workspaces' to discover reclaimable build caches and artifacts."));
        }
    }

    private void setupTableColumns() {
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));

        typeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType().getDisplayLabel()));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    DeveloperArtifactInfo info = getTableRow().getItem();
                    Label badge = new Label(item);
                    badge.getStyleClass().addAll("badge", info != null ? info.getType().getBadgeStyleClass() : "badge-subtle");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        projectCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getProjectName()));
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        sizeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormattedSize()));
        fileCountCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.valueOf(cellData.getValue().getFileCount())));
        modifiedCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormattedLastModified()));
        pathCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPath().toString()));

        filteredList = new FilteredList<>(masterList, p -> true);
        SortedList<DeveloperArtifactInfo> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(artifactTable.comparatorProperty());
        artifactTable.setItems(sortedList);

        // Selection listener to update toolbar summary
        masterList.addListener((javafx.beans.Observable o) -> updateSelectionSummary());
        artifactTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasWsl = newVal != null && newVal.getType() == ArtifactType.WSL2_VHDX;
            if (compactWslBtn != null) {
                compactWslBtn.setDisable(!hasWsl);
            }
        });
    }

    private void setupFilters() {
        ToggleGroup group = new ToggleGroup();
        filterAllBtn.setToggleGroup(group);
        filterNodeBtn.setToggleGroup(group);
        filterBuildsBtn.setToggleGroup(group);
        filterPythonBtn.setToggleGroup(group);
        filterWslBtn.setToggleGroup(group);

        group.selectedToggleProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }

    private void applyFilter() {
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";

        filteredList.setPredicate(item -> {
            // Type toggle filter
            if (filterNodeBtn.isSelected() && item.getType() != ArtifactType.NODE_MODULES) return false;
            if (filterBuildsBtn.isSelected() && item.getType() != ArtifactType.GRADLE_CACHE && item.getType() != ArtifactType.MAVEN_CACHE && item.getType() != ArtifactType.RUST_TARGET && item.getType() != ArtifactType.BUILD_OUTPUT) return false;
            if (filterPythonBtn.isSelected() && item.getType() != ArtifactType.PYTHON_VENV) return false;
            if (filterWslBtn.isSelected() && item.getType() != ArtifactType.WSL2_VHDX) return false;

            // Search query filter
            if (!query.isEmpty()) {
                boolean matchName = item.getName().toLowerCase(Locale.ROOT).contains(query);
                boolean matchProj = item.getProjectName().toLowerCase(Locale.ROOT).contains(query);
                boolean matchPath = item.getPath().toString().toLowerCase(Locale.ROOT).contains(query);
                return matchName || matchProj || matchPath;
            }

            return true;
        });
        updateSelectionSummary();
    }

    @FXML
    public void onScanWorkspaces() {
        if (scanBtn != null) {
            scanBtn.setDisable(true);
            scanBtn.setText("⏳ Scanning Developer Trees...");
        }

        scanCancelFlag.set(false);

        submitAsync(() -> {
            try {
                List<Path> roots = new ArrayList<>(developerService.getDefaultSearchRoots());
                roots.addAll(customFolders);

                List<DeveloperArtifactInfo> artifacts = developerService.scanDeveloperArtifacts(
                        roots,
                        scanCancelFlag,
                        (msg, pct) -> {}
                );

                Platform.runLater(() -> {
                    masterList.setAll(artifacts);
                    for (DeveloperArtifactInfo info : artifacts) {
                        info.selectedProperty().addListener((obs, o, n) -> updateSelectionSummary());
                    }
                    updateKpis();
                    updateSelectionSummary();
                    if (scanBtn != null) {
                        scanBtn.setDisable(false);
                        scanBtn.setText("⚡ Scan Developer Workspaces");
                    }
                });
            } catch (Exception ex) {
                log.error("Failed to scan developer artifacts: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    if (scanBtn != null) {
                        scanBtn.setDisable(false);
                        scanBtn.setText("⚡ Scan Developer Workspaces");
                    }
                });
            }
        });
    }

    @FXML
    public void onAddCustomFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Additional Developer Workspace / Project Directory");
        File dir = chooser.showDialog(artifactTable != null && artifactTable.getScene() != null ? artifactTable.getScene().getWindow() : null);
        if (dir != null && dir.exists()) {
            customFolders.add(dir.toPath());
            onScanWorkspaces();
        }
    }

    @FXML
    public void onSelectAll() {
        for (DeveloperArtifactInfo item : filteredList) {
            item.setSelected(true);
        }
        updateSelectionSummary();
    }

    @FXML
    public void onDeselectAll() {
        for (DeveloperArtifactInfo item : filteredList) {
            item.setSelected(false);
        }
        updateSelectionSummary();
    }

    @FXML
    public void onCleanSelected() {
        List<DeveloperArtifactInfo> toDelete = masterList.stream().filter(DeveloperArtifactInfo::isSelected).toList();
        if (toDelete.isEmpty()) return;

        long totalBytes = toDelete.stream().mapToLong(DeveloperArtifactInfo::getSizeBytes).sum();

        Window window = artifactTable != null && artifactTable.getScene() != null
                ? artifactTable.getScene().getWindow() : null;

        OperationPreview preview = OperationPreview.builder()
                .operationType("DEVELOPER_ARTIFACT_CLEANUP")
                .riskClassification(RiskClassification.IRREVERSIBLE)
                .targetCount(toDelete.size())
                .targetDescriptions(toDelete.stream().map(item -> item.getPath().toString()).toList())
                .estimatedBytes(totalBytes)
                .exactByteEstimate(false)
                .recoveryMechanism("Permanent Deletion (Unrecoverable - can be regenerated via package managers)")
                .irreversible(true)
                .requiresElevation(false)
                .addWarning("This will purge build outputs, dependencies, and caches.")
                .build();

        if (OperationConfirmationDialog.confirm(window, preview)) {
            submitAsync(() -> {
                int cleanedCount = 0;
                long cleanedBytes = 0;

                for (DeveloperArtifactInfo item : toDelete) {
                    if (developerService.deleteArtifact(item)) {
                        cleanedCount++;
                        cleanedBytes += item.getSizeBytes();
                    }
                }

                int finalCount = cleanedCount;
                long finalBytes = cleanedBytes;
                Platform.runLater(() -> {
                    masterList.removeAll(toDelete);
                    updateKpis();
                    updateSelectionSummary();

                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setTitle("Cleanup Complete");
                    done.setHeaderText("Successfully reclaimed " + FormatUtils.formatBytes(finalBytes) + " of disk space.");
                    done.setContentText("Cleaned " + finalCount + " developer artifact directories.");
                    done.showAndWait();
                });
            });
        }
    }

    @FXML
    public void onCompactWsl() {
        DeveloperArtifactInfo selected = artifactTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getType() != ArtifactType.WSL2_VHDX) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Compact WSL2 Virtual Disk");
        confirm.setHeaderText("Compact " + selected.getName() + " (" + selected.getFormattedSize() + ")?");
        confirm.setContentText("WSL will be shut down briefly ('wsl --shutdown') to release locks and compact the ext4.vhdx image via diskpart.\n\n"
                + "Ensure all open WSL terminals are saved before proceeding.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            submitAsync(() -> {
                long reclaimed = developerService.compactWslDisk(selected.getPath());
                Platform.runLater(() -> {
                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setTitle("WSL2 Compaction Result");
                    if (reclaimed > 0) {
                        done.setHeaderText("Successfully compacted virtual disk!");
                        done.setContentText("Reclaimed " + FormatUtils.formatBytes(reclaimed) + " of physical host storage.");
                    } else if (reclaimed == 0) {
                        done.setHeaderText("Disk is already maximally compacted.");
                        done.setContentText("No additional unused block space was found in the virtual disk container.");
                    } else {
                        done.setHeaderText("Compaction failed or required administrative privilege.");
                        done.setContentText("Please run CleanPilot with administrator rights to execute diskpart compaction.");
                    }
                    done.showAndWait();
                    onScanWorkspaces();
                });
            });
        }
    }

    private void updateKpis() {
        long totalBytes = masterList.stream().mapToLong(DeveloperArtifactInfo::getSizeBytes).sum();
        long totalCount = masterList.size();

        long nodeBytes = masterList.stream()
                .filter(a -> a.getType() == ArtifactType.NODE_MODULES || a.getType() == ArtifactType.BUILD_OUTPUT)
                .mapToLong(DeveloperArtifactInfo::getSizeBytes).sum();
        long nodeCount = masterList.stream()
                .filter(a -> a.getType() == ArtifactType.NODE_MODULES || a.getType() == ArtifactType.BUILD_OUTPUT)
                .count();

        long wslBytes = masterList.stream()
                .filter(a -> a.getType() == ArtifactType.WSL2_VHDX)
                .mapToLong(DeveloperArtifactInfo::getSizeBytes).sum();

        totalReclaimableLabel.setText(FormatUtils.formatBytes(totalBytes));
        totalArtifactsCountLabel.setText(totalCount + " artifacts detected");
        nodeModulesSizeLabel.setText(FormatUtils.formatBytes(nodeBytes));
        nodeModulesCountLabel.setText(nodeCount + " project folders");
        wslSizeLabel.setText(FormatUtils.formatBytes(wslBytes));
    }

    private void updateSelectionSummary() {
        List<DeveloperArtifactInfo> selected = masterList.stream().filter(DeveloperArtifactInfo::isSelected).toList();
        long bytes = selected.stream().mapToLong(DeveloperArtifactInfo::getSizeBytes).sum();
        selectionSummaryLabel.setText("Selected: " + selected.size() + " artifacts (" + FormatUtils.formatBytes(bytes) + ")");
        cleanSelectedBtn.setDisable(selected.isEmpty());
    }

    private void submitAsync(Runnable task) {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            return;
        }
        try {
            executorService.submit(task);
        } catch (RejectedExecutionException ex) {
            log.debug("Developer cleaner task rejected: executor is shut down.");
        }
    }
}
