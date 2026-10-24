package com.cleanpilot.controller;

import com.cleanpilot.model.ScanHistoryEntity;
import com.cleanpilot.model.ScannedFileItem;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.service.FileDeletionService;
import com.cleanpilot.service.LargeFileScannerService;
import com.cleanpilot.service.ScanHistoryService;
import com.cleanpilot.service.UserPreferencesService;
import com.cleanpilot.ui.FormatUtils;
import com.cleanpilot.ui.OperationConfirmationDialog;
import com.cleanpilot.ui.PrefixTrie;
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
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LargeFilesViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(LargeFilesViewController.class);

    @FXML private TextField targetPathField;
    @FXML private ComboBox<String> minSizeComboBox;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private ComboBox<String> sortByComboBox;
    @FXML private TextField searchField;
    @FXML private Button scanBtn;
    @FXML private Button cancelBtn;
    @FXML private ProgressBar scanProgressBar;
    @FXML private Label scanStatusLabel;
    @FXML private TableView<ScannedFileItem> filesTable;
    @FXML private TableColumn<ScannedFileItem, Boolean> selectCol;
    @FXML private TableColumn<ScannedFileItem, String> nameCol;
    @FXML private TableColumn<ScannedFileItem, String> categoryCol;
    @FXML private TableColumn<ScannedFileItem, Long> sizeCol;
    @FXML private TableColumn<ScannedFileItem, java.time.Instant> dateCol;
    @FXML private TableColumn<ScannedFileItem, String> pathCol;
    @FXML private Label selectionSummaryLabel;
    @FXML private Button deleteSelectedBtn;
    @FXML private Button smartCacheBtn;
    @FXML private javafx.scene.layout.StackPane treemapContainer;

    private final PrefixTrie<ScannedFileItem> prefixTrie = new PrefixTrie<>();
    private com.cleanpilot.ui.DiskTreemapView treemapView;

    private final LargeFileScannerService scannerService;
    private final FileDeletionService deletionService;
    private final ScanHistoryService historyService;
    private final UserPreferencesService preferencesService;
    private final com.cleanpilot.service.CachePresetService cachePresetService;
    private final ExecutorService executorService;

    private final ObservableList<ScannedFileItem> masterFileList = FXCollections.observableArrayList();
    private FilteredList<ScannedFileItem> filteredFileList;
    private AtomicBoolean scanCancelFlag = new AtomicBoolean(false);
    private Long currentScanRecordId = null;

    public LargeFilesViewController(
            LargeFileScannerService scannerService,
            FileDeletionService deletionService,
            ScanHistoryService historyService,
            UserPreferencesService preferencesService,
            com.cleanpilot.service.CachePresetService cachePresetService,
            ExecutorService executorService
    ) {
        this.scannerService = scannerService;
        this.deletionService = deletionService;
        this.historyService = historyService;
        this.preferencesService = preferencesService;
        this.cachePresetService = cachePresetService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Set default path to User Home
        String userHome = System.getProperty("user.home", ".");
        targetPathField.setText(userHome);

        // Populate size dropdown
        minSizeComboBox.getItems().setAll("25 MB", "50 MB", "100 MB", "250 MB", "500 MB", "1 GB", "5 GB");
        long prefMb = preferencesService.getMinLargeFileSizeMb();
        String defaultSizeStr = prefMb >= 1024 ? (prefMb / 1024) + " GB" : prefMb + " MB";
        if (minSizeComboBox.getItems().contains(defaultSizeStr)) {
            minSizeComboBox.setValue(defaultSizeStr);
        } else {
            minSizeComboBox.setValue("100 MB");
        }

        // Populate category dropdown
        categoryComboBox.getItems().setAll(
                "All Categories",
                "Developer Artifacts",
                "Video",
                "Audio",
                "Archive / Image",
                "Document",
                "Executable / Binary",
                "Stale Files (>1 Year)",
                "Other"
        );
        categoryComboBox.setValue("All Categories");

        if (treemapContainer != null) {
            treemapView = new com.cleanpilot.ui.DiskTreemapView();
            treemapContainer.getChildren().setAll(treemapView);
        }

        // Populate sorting dropdown
        sortByComboBox.getItems().setAll(
                "Size (Largest First)",
                "Size (Smallest First)",
                "Date (Newest First)",
                "Date (Oldest First)",
                "Name (A-Z)",
                "Name (Z-A)"
        );
        sortByComboBox.setValue("Size (Largest First)");
        sortByComboBox.valueProperty().addListener((obs, oldV, newV) -> applySorting(newV));

        // Setup TableView
        filesTable.setPlaceholder(new Label("No large files found. Select a directory and start scanning."));

        selectCol.setCellValueFactory(param -> param.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);

        nameCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getFileName()));
        categoryCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getCategory()));
        
        sizeCol.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue().getSizeInBytes()));
        sizeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatBytes(item));
                }
            }
        });

        dateCol.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue().getLastModified()));
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(java.time.Instant item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatInstant(item));
                }
            }
        });

        pathCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getAbsolutePathString()));

        // Filtering & Sorting
        filteredFileList = new FilteredList<>(masterFileList, p -> true);
        SortedList<ScannedFileItem> sortedList = new SortedList<>(filteredFileList);
        sortedList.comparatorProperty().bind(filesTable.comparatorProperty());
        filesTable.setItems(sortedList);

        // Filter listeners
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilters());
        categoryComboBox.valueProperty().addListener((obs, oldV, newV) -> applyFilters());

        filesTable.setEditable(true);
        applySorting(sortByComboBox.getValue());
        updateSelectionSummary();
    }

    private void applySorting(String sortOption) {
        if (sortOption == null) return;
        filesTable.getSortOrder().clear();
        switch (sortOption) {
            case "Size (Largest First)" -> {
                sizeCol.setSortType(TableColumn.SortType.DESCENDING);
                filesTable.getSortOrder().add(sizeCol);
            }
            case "Size (Smallest First)" -> {
                sizeCol.setSortType(TableColumn.SortType.ASCENDING);
                filesTable.getSortOrder().add(sizeCol);
            }
            case "Date (Newest First)" -> {
                dateCol.setSortType(TableColumn.SortType.DESCENDING);
                filesTable.getSortOrder().add(dateCol);
            }
            case "Date (Oldest First)" -> {
                dateCol.setSortType(TableColumn.SortType.ASCENDING);
                filesTable.getSortOrder().add(dateCol);
            }
            case "Name (A-Z)" -> {
                nameCol.setSortType(TableColumn.SortType.ASCENDING);
                filesTable.getSortOrder().add(nameCol);
            }
            case "Name (Z-A)" -> {
                nameCol.setSortType(TableColumn.SortType.DESCENDING);
                filesTable.getSortOrder().add(nameCol);
            }
        }
    }

    private void applyFilters() {
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String cat = categoryComboBox.getValue();

        Set<ScannedFileItem> prefixMatches = query.isEmpty() ? null : new HashSet<>(prefixTrie.searchByPrefix(query));

        filteredFileList.setPredicate(item -> {
            boolean matchesSearch = query.isEmpty()
                    || (prefixMatches != null && prefixMatches.contains(item))
                    || item.getFileName().toLowerCase(Locale.ROOT).contains(query)
                    || item.getAbsolutePathString().toLowerCase(Locale.ROOT).contains(query);

            boolean matchesCategory;
            if (cat == null || "All Categories".equals(cat)) {
                matchesCategory = true;
            } else if ("Stale Files (>1 Year)".equalsIgnoreCase(cat)) {
                matchesCategory = item.isStale(365);
            } else {
                matchesCategory = cat.equalsIgnoreCase(item.getCategory());
            }

            return matchesSearch && matchesCategory;
        });

        updateSelectionSummary();
    }

    @FXML
    public void onBrowseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Directory to Analyze");
        String current = targetPathField.getText();
        if (current != null && !current.isBlank() && new File(current).isDirectory()) {
            chooser.setInitialDirectory(new File(current));
        }
        File selected = chooser.showDialog(targetPathField.getScene().getWindow());
        if (selected != null) {
            targetPathField.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    public void onStartScan() {
        String pathStr = targetPathField.getText();
        if (pathStr == null || pathStr.isBlank()) {
            showAlert("Input Required", "Please specify a valid directory path to scan.");
            return;
        }

        Path target = Paths.get(pathStr);
        if (!java.nio.file.Files.exists(target)) {
            showAlert("Directory Not Found", "The specified path does not exist on disk: " + pathStr);
            return;
        }

        long minBytes = parseSelectedMinBytes();
        Set<String> exclusions = preferencesService.getExcludedPaths();

        scanCancelFlag.set(false);
        setScanningUi(true);
        masterFileList.clear();
        updateSelectionSummary();

        long startTime = System.currentTimeMillis();

        submitAsync(() -> {
            try {
                List<ScannedFileItem> results = scannerService.scanLargeFiles(
                        target,
                        minBytes,
                        exclusions,
                        scanCancelFlag,
                        new com.cleanpilot.service.ScanProgressListener() {
                            @Override
                            public void onProgress(long filesVisited, long bytesExamined, String current) {
                                Platform.runLater(() -> scanStatusLabel.setText("Visited: " + FormatUtils.formatNumber(filesVisited)
                                        + " (" + FormatUtils.formatBytes(bytesExamined) + ") - " + current));
                            }

                            @Override
                            public void onThroughput(long filesVisited, long bytesExamined, String current, double filesPerSec, double bytesPerSec) {
                                Platform.runLater(() -> scanStatusLabel.setText(
                                        "Visited: " + FormatUtils.formatNumber(filesVisited)
                                                + " (" + FormatUtils.formatBytes(bytesExamined) + ") | "
                                                + FormatUtils.formatThroughput(filesPerSec) + " • "
                                                + FormatUtils.formatSpeed(bytesPerSec) + " | " + current));
                            }
                        }
                );

                long duration = System.currentTimeMillis() - startTime;
                long totalBytes = results.stream().mapToLong(ScannedFileItem::getSizeInBytes).sum();

                // Persist historical run
                ScanHistoryEntity history = new ScanHistoryEntity("LARGE_FILES", target.toString());
                history.setFilesScanned(results.size());
                history.setBytesScanned(totalBytes);
                history.setItemsIdentified(results.size());
                history.setBytesIdentified(totalBytes);
                history.setDurationMillis(duration);
                history.setStatus("COMPLETED");
                ScanHistoryEntity saved = historyService.saveScan(history);
                currentScanRecordId = saved.getId();

                Platform.runLater(() -> {
                    prefixTrie.clear();
                    for (ScannedFileItem item : results) {
                        prefixTrie.insertTokens(item.getFileName(), item);
                        prefixTrie.insertTokens(item.getAbsolutePathString(), item);
                    }
                    masterFileList.setAll(results);
                    if (treemapView != null) {
                        treemapView.setFiles(results);
                    }
                    // Add listener to item selection
                    for (ScannedFileItem item : results) {
                        item.selectedProperty().addListener((obs, oldV, newV) -> updateSelectionSummary());
                    }
                    applySorting(sortByComboBox.getValue());
                    scanStatusLabel.setText("Scan completed in " + FormatUtils.formatDuration(duration)
                            + ". Found " + results.size() + " files (" + FormatUtils.formatBytes(totalBytes) + ").");
                    setScanningUi(false);
                    updateSelectionSummary();
                });
            } catch (CancellationException ce) {
                Platform.runLater(() -> {
                    scanStatusLabel.setText("Scan cancelled by user.");
                    setScanningUi(false);
                });
            } catch (Exception e) {
                log.error("Scan failed: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    scanStatusLabel.setText("Scan error: " + e.getMessage());
                    setScanningUi(false);
                });
            }
        });
    }

    @FXML
    public void onCancelScan() {
        scanCancelFlag.set(true);
        scanStatusLabel.setText("Aborting scan...");
    }

    @FXML
    public void onSelectAll() {
        for (ScannedFileItem item : filteredFileList) {
            item.setSelected(true);
        }
        updateSelectionSummary();
    }

    @FXML
    public void onDeselectAll() {
        for (ScannedFileItem item : filteredFileList) {
            item.setSelected(false);
        }
        updateSelectionSummary();
    }

    @FXML
    public void onDeleteSelected() {
        List<ScannedFileItem> selectedItems = masterFileList.stream()
                .filter(ScannedFileItem::isSelected)
                .toList();

        if (selectedItems.isEmpty()) {
            showAlert("No Selection", "Please select one or more files to remove.");
            return;
        }

        long totalBytesToRemove = selectedItems.stream().mapToLong(ScannedFileItem::getSizeInBytes).sum();
        boolean preferTrash = preferencesService.isPreferTrash();
        boolean nativeTrashSupported = deletionService.isNativeTrashSupported();

        Window window = filesTable != null && filesTable.getScene() != null
                ? filesTable.getScene().getWindow() : null;

        boolean isIrreversible = !preferTrash || !nativeTrashSupported;
        OperationPreview preview = OperationPreview.builder()
                .operationType("LARGE_FILE_CLEANUP")
                .riskClassification(isIrreversible ? RiskClassification.IRREVERSIBLE : RiskClassification.MODERATE_RISK)
                .targetCount(selectedItems.size())
                .targetDescriptions(selectedItems.stream().map(ScannedFileItem::getAbsolutePathString).toList())
                .estimatedBytes(totalBytesToRemove)
                .exactByteEstimate(true)
                .recoveryMechanism(isIrreversible ? "Permanent Deletion (Unrecoverable)" : "Windows Recycle Bin (Restorable)")
                .irreversible(isIrreversible)
                .requiresElevation(false)
                .addWarning("CleanPilot strictly operates on authentic filesystem paths.")
                .build();

        if (OperationConfirmationDialog.confirm(window, preview)) {
            setScanningUi(true);
            scanStatusLabel.setText("Removing selected files...");

            submitAsync(() -> {
                List<FileDeletionService.DeletionResult> results =
                        deletionService.deleteFiles(selectedItems, preferTrash, !preferTrash || !nativeTrashSupported);

                long freedBytes = results.stream().filter(FileDeletionService.DeletionResult::success)
                        .mapToLong(FileDeletionService.DeletionResult::freedBytes).sum();
                long successCount = results.stream().filter(FileDeletionService.DeletionResult::success).count();

                if (currentScanRecordId != null) {
                    historyService.recordDeletion(currentScanRecordId, successCount, freedBytes);
                }

                Platform.runLater(() -> {
                    // Remove successfully deleted files from list
                    Set<Path> deletedPaths = new HashSet<>();
                    for (FileDeletionService.DeletionResult res : results) {
                        if (res.success()) {
                            deletedPaths.add(res.path());
                        }
                    }
                    masterFileList.removeIf(item -> deletedPaths.contains(item.getPath()));

                    setScanningUi(false);
                    scanStatusLabel.setText("Removal finished: " + successCount + "/" + selectedItems.size()
                            + " files removed. Freed: " + FormatUtils.formatBytes(freedBytes));
                    updateSelectionSummary();
                });
            });
        }
    }

    private void updateSelectionSummary() {
        long count = masterFileList.stream().filter(ScannedFileItem::isSelected).count();
        long bytes = masterFileList.stream().filter(ScannedFileItem::isSelected).mapToLong(ScannedFileItem::getSizeInBytes).sum();

        if (selectionSummaryLabel != null) {
            selectionSummaryLabel.setText("Selected: " + count + " files (" + FormatUtils.formatBytes(bytes) + ")");
        }
        if (deleteSelectedBtn != null) {
            deleteSelectedBtn.setDisable(count == 0);
        }
    }

    private void setScanningUi(boolean scanning) {
        scanBtn.setDisable(scanning);
        cancelBtn.setDisable(!scanning);
        scanProgressBar.setVisible(scanning);
    }

    private long parseSelectedMinBytes() {
        String val = minSizeComboBox.getValue();
        if (val == null) return 100L * 1024 * 1024;
        val = val.trim().toUpperCase(Locale.ROOT);
        if (val.contains("GB")) {
            long gb = Long.parseLong(val.replace("GB", "").trim());
            return gb * 1024 * 1024 * 1024;
        } else if (val.contains("MB")) {
            long mb = Long.parseLong(val.replace("MB", "").trim());
            return mb * 1024 * 1024;
        }
        return 100L * 1024 * 1024;
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void onOpenCachePresets() {
        com.cleanpilot.ui.SafeCacheCleanerDialog.show(
                targetPathField.getScene().getWindow(),
                cachePresetService,
                deletionService,
                executorService
        );
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
