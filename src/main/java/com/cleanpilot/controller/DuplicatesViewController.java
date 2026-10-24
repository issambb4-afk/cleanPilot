package com.cleanpilot.controller;

import com.cleanpilot.model.DuplicateFileGroup;
import com.cleanpilot.model.ScanHistoryEntity;
import com.cleanpilot.model.ScannedFileItem;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.service.DuplicateDetectionService;
import com.cleanpilot.service.FileDeletionService;
import com.cleanpilot.service.ScanHistoryService;
import com.cleanpilot.service.UserPreferencesService;
import com.cleanpilot.ui.FormatUtils;
import com.cleanpilot.ui.OperationConfirmationDialog;
import com.cleanpilot.ui.PrefixTrie;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeTableCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DuplicatesViewController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(DuplicatesViewController.class);

    @FXML private TextField targetPathField;
    @FXML private ComboBox<String> minSizeComboBox;
    @FXML private ComboBox<String> sortByComboBox;
    @FXML private TextField searchField;
    @FXML private Button scanBtn;
    @FXML private Button cancelBtn;
    @FXML private ProgressBar scanProgressBar;
    @FXML private Label scanStatusLabel;
    @FXML private TreeTableView<DuplicateTreeRow> duplicatesTreeTable;
    @FXML private TreeTableColumn<DuplicateTreeRow, Boolean> selectCol;
    @FXML private TreeTableColumn<DuplicateTreeRow, String> nameCol;
    @FXML private TreeTableColumn<DuplicateTreeRow, String> sizeCol;
    @FXML private TreeTableColumn<DuplicateTreeRow, String> dateCol;
    @FXML private TreeTableColumn<DuplicateTreeRow, String> pathCol;

    private final PrefixTrie<DuplicateFileGroup> prefixTrie = new PrefixTrie<>();
    @FXML private Label reclaimableSummaryLabel;
    @FXML private Button cleanDuplicatesBtn;

    private final DuplicateDetectionService duplicateService;
    private final FileDeletionService deletionService;
    private final ScanHistoryService historyService;
    private final UserPreferencesService preferencesService;
    private final ExecutorService executorService;

    private final List<DuplicateFileGroup> detectedGroups = new ArrayList<>();
    private final AtomicBoolean scanCancelFlag = new AtomicBoolean(false);
    private Long currentScanRecordId = null;

    public DuplicatesViewController(
            DuplicateDetectionService duplicateService,
            FileDeletionService deletionService,
            ScanHistoryService historyService,
            UserPreferencesService preferencesService,
            ExecutorService executorService
    ) {
        this.duplicateService = duplicateService;
        this.deletionService = deletionService;
        this.historyService = historyService;
        this.preferencesService = preferencesService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        targetPathField.setText(System.getProperty("user.home", "."));

        minSizeComboBox.getItems().setAll("1 KB", "100 KB", "1 MB", "10 MB", "50 MB", "100 MB");
        minSizeComboBox.setValue("1 MB");

        sortByComboBox.getItems().setAll(
                "Reclaimable Space (Largest First)",
                "Reclaimable Space (Smallest First)",
                "Single File Size (Largest First)",
                "Duplicate Count (Most Copies First)",
                "Group Hash (A-Z)"
        );
        sortByComboBox.setValue("Reclaimable Space (Largest First)");
        sortByComboBox.valueProperty().addListener((obs, oldV, newV) -> buildTree());
        searchField.textProperty().addListener((obs, oldV, newV) -> buildTree());

        duplicatesTreeTable.setShowRoot(false);
        duplicatesTreeTable.setPlaceholder(new Label("No duplicates discovered. Choose a directory and begin scanning."));

        selectCol.setCellValueFactory(param -> {
            if (param.getValue() != null && param.getValue().getValue() != null) {
                return param.getValue().getValue().selectedProperty();
            }
            return new SimpleBooleanProperty(false);
        });
        selectCol.setCellFactory(CheckBoxTreeTableCell.forTreeTableColumn(selectCol));
        selectCol.setEditable(true);

        nameCol.setCellValueFactory(param -> new SimpleStringProperty(
                param.getValue() != null && param.getValue().getValue() != null
                        ? param.getValue().getValue().getName() : ""));

        sizeCol.setCellValueFactory(param -> new SimpleStringProperty(
                param.getValue() != null && param.getValue().getValue() != null
                        ? param.getValue().getValue().getSize() : ""));

        dateCol.setCellValueFactory(param -> new SimpleStringProperty(
                param.getValue() != null && param.getValue().getValue() != null
                        ? param.getValue().getValue().getDate() : ""));

        pathCol.setCellValueFactory(param -> new SimpleStringProperty(
                param.getValue() != null && param.getValue().getValue() != null
                        ? param.getValue().getValue().getPath() : ""));

        duplicatesTreeTable.setEditable(true);
        updateSummary();
    }

    @FXML
    public void onBrowseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder for Duplicate Detection");
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
            showAlert("Directory Missing", "Please select a directory to analyze for duplicate files.");
            return;
        }

        Path target = Paths.get(pathStr);
        if (!Files.exists(target)) {
            showAlert("Path Does Not Exist", "The selected path does not exist on disk: " + pathStr);
            return;
        }

        long minBytes = parseMinBytes();
        Set<String> exclusions = preferencesService.getExcludedPaths();

        scanCancelFlag.set(false);
        setScanningUi(true);
        detectedGroups.clear();
        prefixTrie.clear();
        buildTree();
        updateSummary();

        long startTime = System.currentTimeMillis();

        submitAsync(() -> {
            try {
                List<DuplicateFileGroup> groups = duplicateService.detectDuplicates(
                        target,
                        minBytes,
                        exclusions,
                        scanCancelFlag,
                        new com.cleanpilot.service.ScanProgressListener() {
                            @Override
                            public void onProgress(long filesScanned, long bytesScanned, String currentPath) {
                                Platform.runLater(() -> scanStatusLabel.setText(
                                        "Scanned: " + FormatUtils.formatNumber(filesScanned)
                                                + " files (" + FormatUtils.formatBytes(bytesScanned) + ")"));
                            }

                            @Override
                            public void onThroughput(long filesScanned, long bytesScanned, String currentPath, double filesPerSecond, double bytesPerSecond) {
                                Platform.runLater(() -> scanStatusLabel.setText(
                                        "Scanned: " + FormatUtils.formatNumber(filesScanned)
                                                + " (" + FormatUtils.formatBytes(bytesScanned) + ") | "
                                                + FormatUtils.formatThroughput(filesPerSecond) + " • "
                                                + FormatUtils.formatSpeed(bytesPerSecond)));
                            }

                            @Override
                            public void onStatus(String statusMessage) {
                                Platform.runLater(() -> scanStatusLabel.setText(statusMessage));
                            }
                        }
                );

                long duration = System.currentTimeMillis() - startTime;
                long totalReclaimable = groups.stream().mapToLong(DuplicateFileGroup::getReclaimableSize).sum();
                int totalCopies = groups.stream().mapToInt(DuplicateFileGroup::getFileCount).sum();

                // Record history
                ScanHistoryEntity history = new ScanHistoryEntity("DUPLICATES", target.toString());
                history.setFilesScanned(totalCopies);
                history.setItemsIdentified(totalCopies);
                history.setBytesIdentified(totalReclaimable);
                history.setDurationMillis(duration);
                history.setStatus("COMPLETED");
                ScanHistoryEntity saved = historyService.saveScan(history);
                currentScanRecordId = saved.getId();

                Platform.runLater(() -> {
                    detectedGroups.addAll(groups);
                    prefixTrie.clear();
                    for (DuplicateFileGroup g : detectedGroups) {
                        prefixTrie.insert(g.getSha256Hash(), g);
                        for (ScannedFileItem f : g.getFiles()) {
                            prefixTrie.insertTokens(f.getFileName(), g);
                            prefixTrie.insertTokens(f.getAbsolutePathString(), g);
                        }
                    }
                    buildTree();
                    setScanningUi(false);
                    scanStatusLabel.setText("Found " + groups.size() + " duplicate groups ("
                            + FormatUtils.formatBytes(totalReclaimable) + " reclaimable) in "
                            + FormatUtils.formatDuration(duration));
                    updateSummary();
                });
            } catch (CancellationException ce) {
                Platform.runLater(() -> {
                    scanStatusLabel.setText("Duplicate detection aborted.");
                    setScanningUi(false);
                });
            } catch (Exception e) {
                log.error("Duplicate detection failed: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    scanStatusLabel.setText("Detection error: " + e.getMessage());
                    setScanningUi(false);
                });
            }
        });
    }

    @FXML
    public void onCancelScan() {
        scanCancelFlag.set(true);
        scanStatusLabel.setText("Stopping duplicate search...");
    }

    @FXML
    public void onSelectAllDuplicates() {
        onSelectKeepOldest();
    }

    @FXML
    public void onSelectKeepOldest() {
        for (DuplicateFileGroup g : detectedGroups) {
            g.markKeepOldest();
        }
        updateSummary();
        duplicatesTreeTable.refresh();
    }

    @FXML
    public void onSelectKeepNewest() {
        for (DuplicateFileGroup g : detectedGroups) {
            g.markKeepNewest();
        }
        updateSummary();
        duplicatesTreeTable.refresh();
    }

    @FXML
    public void onSelectKeepShortestPath() {
        for (DuplicateFileGroup g : detectedGroups) {
            g.markKeepShortestPath();
        }
        updateSummary();
        duplicatesTreeTable.refresh();
    }

    @FXML
    public void onDeselectAll() {
        for (DuplicateFileGroup g : detectedGroups) {
            g.clearSelection();
        }
        updateSummary();
        duplicatesTreeTable.refresh();
    }

    @FXML
    public void onCleanDuplicates() {
        List<ScannedFileItem> toDelete = new ArrayList<>();
        for (DuplicateFileGroup g : detectedGroups) {
            for (ScannedFileItem file : g.getFiles()) {
                if (file.isSelected()) {
                    toDelete.add(file);
                }
            }
        }

        if (toDelete.isEmpty()) {
            showAlert("No Duplicates Selected", "Please select the redundant copies you want to remove.");
            return;
        }

        long totalBytes = toDelete.stream().mapToLong(ScannedFileItem::getSizeInBytes).sum();
        boolean preferTrash = preferencesService.isPreferTrash();
        boolean nativeTrashSupported = deletionService.isNativeTrashSupported();

        Window window = duplicatesTreeTable != null && duplicatesTreeTable.getScene() != null
                ? duplicatesTreeTable.getScene().getWindow() : null;

        boolean isIrreversible = !preferTrash || !nativeTrashSupported;
        OperationPreview preview = OperationPreview.builder()
                .operationType("DUPLICATE_CLEANUP")
                .riskClassification(isIrreversible ? RiskClassification.IRREVERSIBLE : RiskClassification.MODERATE_RISK)
                .targetCount(toDelete.size())
                .targetDescriptions(toDelete.stream().map(ScannedFileItem::getAbsolutePathString).toList())
                .estimatedBytes(totalBytes)
                .exactByteEstimate(true)
                .recoveryMechanism(isIrreversible ? "Permanent Deletion (Unrecoverable)" : "Windows Recycle Bin (Restorable)")
                .irreversible(isIrreversible)
                .requiresElevation(false)
                .addWarning("Original copies of each duplicate group will be strictly preserved.")
                .build();

        if (OperationConfirmationDialog.confirm(window, preview)) {
            setScanningUi(true);
            scanStatusLabel.setText("Deleting duplicate files...");

            submitAsync(() -> {
                List<FileDeletionService.DeletionResult> results =
                        deletionService.deleteFiles(toDelete, preferTrash, !preferTrash || !nativeTrashSupported);

                long freed = results.stream().filter(FileDeletionService.DeletionResult::success)
                        .mapToLong(FileDeletionService.DeletionResult::freedBytes).sum();
                long count = results.stream().filter(FileDeletionService.DeletionResult::success).count();

                if (currentScanRecordId != null) {
                    historyService.recordDeletion(currentScanRecordId, count, freed);
                }

                Platform.runLater(() -> {
                    Set<Path> deleted = new HashSet<>();
                    for (FileDeletionService.DeletionResult r : results) {
                        if (r.success()) deleted.add(r.path());
                    }

                    // Remove deleted files from groups
                    for (DuplicateFileGroup g : detectedGroups) {
                        g.getFiles().removeIf(item -> deleted.contains(item.getPath()));
                    }
                    detectedGroups.removeIf(g -> g.getFiles().size() <= 1);

                    prefixTrie.clear();
                    for (DuplicateFileGroup g : detectedGroups) {
                        prefixTrie.insert(g.getSha256Hash(), g);
                        for (ScannedFileItem f : g.getFiles()) {
                            prefixTrie.insertTokens(f.getFileName(), g);
                            prefixTrie.insertTokens(f.getAbsolutePathString(), g);
                        }
                    }

                    buildTree();
                    setScanningUi(false);
                    scanStatusLabel.setText("Cleaned " + count + " duplicates. Recovered: " + FormatUtils.formatBytes(freed));
                    updateSummary();
                });
            });
        }
    }

    private void buildTree() {
        TreeItem<DuplicateTreeRow> root = new TreeItem<>(new DuplicateTreeRow("ROOT", "", "", "", false, null));

        String sortBy = sortByComboBox != null ? sortByComboBox.getValue() : "Reclaimable Space (Largest First)";
        if (sortBy == null) sortBy = "Reclaimable Space (Largest First)";

        List<DuplicateFileGroup> sortedGroups = new ArrayList<>(detectedGroups);
        switch (sortBy) {
            case "Reclaimable Space (Largest First)" ->
                sortedGroups.sort(Comparator.comparingLong(DuplicateFileGroup::getReclaimableSize).reversed());
            case "Reclaimable Space (Smallest First)" ->
                sortedGroups.sort(Comparator.comparingLong(DuplicateFileGroup::getReclaimableSize));
            case "Single File Size (Largest First)" ->
                sortedGroups.sort(Comparator.comparingLong(DuplicateFileGroup::getSingleFileSize).reversed());
            case "Duplicate Count (Most Copies First)" ->
                sortedGroups.sort(Comparator.comparingInt(DuplicateFileGroup::getFileCount).reversed());
            case "Group Hash (A-Z)" ->
                sortedGroups.sort(Comparator.comparing(DuplicateFileGroup::getSha256Hash));
        }

        String query = (searchField != null && searchField.getText() != null)
                ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        Set<DuplicateFileGroup> trieMatches = query.isEmpty() ? null : new HashSet<>(prefixTrie.searchByPrefix(query));

        for (DuplicateFileGroup g : sortedGroups) {
            boolean groupMatchesTrie = trieMatches != null && trieMatches.contains(g);
            boolean groupMatchesHash = !query.isEmpty() && g.getSha256Hash().toLowerCase(Locale.ROOT).contains(query);

            List<ScannedFileItem> matchingFiles = new ArrayList<>();
            for (ScannedFileItem file : g.getFiles()) {
                if (query.isEmpty() || groupMatchesTrie || groupMatchesHash
                        || file.getFileName().toLowerCase(Locale.ROOT).contains(query)
                        || file.getAbsolutePathString().toLowerCase(Locale.ROOT).contains(query)) {
                    matchingFiles.add(file);
                }
            }

            if (matchingFiles.isEmpty()) {
                continue;
            }

            String shortHash = g.getSha256Hash().length() > 16
                    ? g.getSha256Hash().substring(0, 16) + "..."
                    : g.getSha256Hash();

            DuplicateTreeRow groupRow = new DuplicateTreeRow(
                    "Group: " + shortHash + " (" + g.getFileCount() + " copies)",
                    g.getFormattedSingleSize() + " each",
                    "",
                    "Reclaimable: " + g.getFormattedReclaimableSize(),
                    true,
                    null
            );

            TreeItem<DuplicateTreeRow> groupNode = new TreeItem<>(groupRow);
            groupNode.setExpanded(true);

            for (int i = 0; i < matchingFiles.size(); i++) {
                ScannedFileItem file = matchingFiles.get(i);
                boolean isOriginal = (g.getFiles().indexOf(file) == 0);
                String label = isOriginal ? "⭐ [Original] " + file.getFileName() : "📄 [Copy] " + file.getFileName();

                DuplicateTreeRow fileRow = new DuplicateTreeRow(
                        label,
                        file.getFormattedSize(),
                        file.getFormattedLastModified(),
                        file.getAbsolutePathString(),
                        false,
                        file
                );

                // Bind selection changes to update summary
                file.selectedProperty().addListener((obs, oldV, newV) -> updateSummary());

                TreeItem<DuplicateTreeRow> fileNode = new TreeItem<>(fileRow);
                groupNode.getChildren().add(fileNode);
            }

            root.getChildren().add(groupNode);
        }
        duplicatesTreeTable.setRoot(root);
    }

    private void updateSummary() {
        long count = 0;
        long bytes = 0;
        for (DuplicateFileGroup g : detectedGroups) {
            for (ScannedFileItem f : g.getFiles()) {
                if (f.isSelected()) {
                    count++;
                    bytes += f.getSizeInBytes();
                }
            }
        }

        if (reclaimableSummaryLabel != null) {
            reclaimableSummaryLabel.setText("Selected to clean: " + count + " files (" + FormatUtils.formatBytes(bytes) + ")");
        }
        if (cleanDuplicatesBtn != null) {
            cleanDuplicatesBtn.setDisable(count == 0);
        }
    }

    private void setScanningUi(boolean scanning) {
        scanBtn.setDisable(scanning);
        cancelBtn.setDisable(!scanning);
        scanProgressBar.setVisible(scanning);
    }

    private long parseMinBytes() {
        String val = minSizeComboBox.getValue();
        if (val == null) return 1024 * 1024;
        val = val.trim().toUpperCase(Locale.ROOT);
        if (val.contains("MB")) {
            return Long.parseLong(val.replace("MB", "").trim()) * 1024 * 1024;
        } else if (val.contains("KB")) {
            return Long.parseLong(val.replace("KB", "").trim()) * 1024;
        }
        return 1024 * 1024;
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Data carrier for the TreeTableView representation.
     */
    public static class DuplicateTreeRow {
        private final String name;
        private final String size;
        private final String date;
        private final String path;
        private final boolean header;
        private final ScannedFileItem item;

        public DuplicateTreeRow(String name, String size, String date, String path, boolean header, ScannedFileItem item) {
            this.name = name;
            this.size = size;
            this.date = date;
            this.path = path;
            this.header = header;
            this.item = item;
        }

        public String getName() { return name; }
        public String getSize() { return size; }
        public String getDate() { return date; }
        public String getPath() { return path; }
        public boolean isHeader() { return header; }

        public BooleanProperty selectedProperty() {
            if (item != null) {
                return item.selectedProperty();
            }
            return new SimpleBooleanProperty(false);
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
