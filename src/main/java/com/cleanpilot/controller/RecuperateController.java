package com.cleanpilot.controller;

import com.cleanpilot.model.QuarantinedItem;
import com.cleanpilot.model.RecoveryItemDTO;
import com.cleanpilot.service.QuarantineService;
import com.cleanpilot.service.SystemTrashReaderService;
import com.cleanpilot.ui.FormatUtils;
import com.cleanpilot.ui.PrefixTrie;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Controller for the "Recuperate & Trash Manager" subsystem.
 * Handles Tier A (Application Quarantine Vault) and Tier B (Native OS System Recycle Bin).
 */
@Component
public class RecuperateController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(RecuperateController.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private Button openSystemTrashBtn;
    @FXML private Button refreshBtn;
    @FXML private Label quarantineCountLabel;
    @FXML private Label quarantineSizeLabel;
    @FXML private Label systemTrashCountLabel;

    @FXML private ToggleButton quarantineTabBtn;
    @FXML private ToggleButton systemTrashTabBtn;
    @FXML private ToggleButton deepRecoveryTabBtn;
    @FXML private Button runDeepScanBtn;
    private ToggleGroup sourceToggleGroup;

    @FXML private TextField searchField;
    @FXML private TableView<RecoveryItemDTO> recoveryTable;
    @FXML private TableColumn<RecoveryItemDTO, Boolean> selectCol;
    @FXML private TableColumn<RecoveryItemDTO, String> nameCol;
    @FXML private TableColumn<RecoveryItemDTO, String> originalPathCol;
    @FXML private TableColumn<RecoveryItemDTO, Long> sizeCol;
    @FXML private TableColumn<RecoveryItemDTO, String> deletedDateCol;
    @FXML private TableColumn<RecoveryItemDTO, String> retentionCol;

    @FXML private Label selectionSummaryLabel;
    @FXML private Button restoreSelectedBtn;
    @FXML private Button recoverToFolderBtn;
    @FXML private Button purgeExpiredBtn;
    @FXML private Button permanentDeleteBtn;

    private final QuarantineService quarantineService;
    private final SystemTrashReaderService systemTrashService;
    private final com.cleanpilot.service.DeepRecoveryScannerService deepRecoveryService;
    private final com.cleanpilot.service.FileDeletionService fileDeletionService;
    private final ExecutorService executorService;

    private final ObservableList<RecoveryItemDTO> currentDisplayedList = FXCollections.observableArrayList();
    private FilteredList<RecoveryItemDTO> filteredList;
    private final PrefixTrie<RecoveryItemDTO> prefixTrie = new PrefixTrie<>();

    private List<RecoveryItemDTO> quarantineCache = new ArrayList<>();
    private List<RecoveryItemDTO> systemTrashCache = new ArrayList<>();
    private List<RecoveryItemDTO> deepRecoveryCache = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicBoolean deepScanCancelFlag = new java.util.concurrent.atomic.AtomicBoolean(false);

    public RecuperateController(
            QuarantineService quarantineService,
            SystemTrashReaderService systemTrashService,
            com.cleanpilot.service.DeepRecoveryScannerService deepRecoveryService,
            com.cleanpilot.service.FileDeletionService fileDeletionService,
            ExecutorService executorService
    ) {
        this.quarantineService = quarantineService;
        this.systemTrashService = systemTrashService;
        this.deepRecoveryService = deepRecoveryService;
        this.fileDeletionService = fileDeletionService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSourceToggles();
        setupTableColumns();
        setupSearchAndFilter();
        loadAllData();
    }

    private void setupSourceToggles() {
        sourceToggleGroup = new ToggleGroup();
        quarantineTabBtn.setToggleGroup(sourceToggleGroup);
        systemTrashTabBtn.setToggleGroup(sourceToggleGroup);
        if (deepRecoveryTabBtn != null) {
            deepRecoveryTabBtn.setToggleGroup(sourceToggleGroup);
        }

        sourceToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                // Prevent un-toggling
                sourceToggleGroup.selectToggle(oldVal);
                return;
            }
            updateActiveTableSource();
        });
    }

    private void setupTableColumns() {
        selectCol.setCellValueFactory(cd -> cd.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));

        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFileName()));
        originalPathCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getOriginalPath()));

        sizeCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getSizeBytes()));
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

        deletedDateCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getDeletedAt() != null ? cd.getValue().getDeletedAt().format(DATE_FORMAT) : "-"
        ));

        retentionCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedDaysRemaining()));
        retentionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("Expired")) {
                        setStyle("-fx-text-fill: -color-danger-emphasis; -fx-font-weight: bold;");
                    } else if (item.contains("OS Managed")) {
                        setStyle("-fx-text-fill: -color-fg-muted;");
                    } else {
                        setStyle("-fx-text-fill: -color-success-emphasis;");
                    }
                }
            }
        });
    }

    private void setupSearchAndFilter() {
        filteredList = new FilteredList<>(currentDisplayedList, p -> true);
        SortedList<RecoveryItemDTO> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(recoveryTable.comparatorProperty());
        recoveryTable.setItems(sortedList);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }

    @FXML
    public void onRefresh() {
        loadAllData();
    }

    @FXML
    public void onOpenSystemTrash() {
        systemTrashService.openSystemRecycleBin();
    }

    private void submitAsync(Runnable task) {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            return;
        }
        try {
            executorService.submit(task);
        } catch (RejectedExecutionException ex) {
            log.debug("Recuperate task rejected: executor is shut down.");
        }
    }

    public void loadAllData() {
        if (refreshBtn != null) refreshBtn.setDisable(true);

        submitAsync(() -> {
            try {
                // Tier A: App Quarantine
                List<QuarantinedItem> qItems = quarantineService.listAllQuarantined();
                long totalQBytes = quarantineService.getTotalVaultBytes();
                List<RecoveryItemDTO> qDtos = qItems.stream().map(RecoveryItemDTO::fromQuarantinedItem).toList();

                // Tier B: System Trash
                List<RecoveryItemDTO> sysItems = systemTrashService.readSystemTrashItems();

                Platform.runLater(() -> {
                    quarantineCache = qDtos;
                    systemTrashCache = sysItems;

                    if (quarantineCountLabel != null) {
                        quarantineCountLabel.setText(String.valueOf(qDtos.size()) + " files");
                    }
                    if (quarantineSizeLabel != null) {
                        quarantineSizeLabel.setText(FormatUtils.formatBytes(totalQBytes) + " held in staging vault");
                    }
                    if (systemTrashCountLabel != null) {
                        systemTrashCountLabel.setText(String.valueOf(sysItems.size()) + " items");
                    }

                    updateActiveTableSource();
                    if (refreshBtn != null) refreshBtn.setDisable(false);
                });
            } catch (Exception e) {
                log.error("Failed to load recovery data: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    if (refreshBtn != null) refreshBtn.setDisable(false);
                });
            }
        });
    }

    private void updateActiveTableSource() {
        boolean isQuarantine = quarantineTabBtn.isSelected();
        boolean isDeepRecovery = deepRecoveryTabBtn != null && deepRecoveryTabBtn.isSelected();

        List<RecoveryItemDTO> target;
        if (isQuarantine) {
            target = quarantineCache;
        } else if (isDeepRecovery) {
            target = deepRecoveryCache;
        } else {
            target = systemTrashCache;
        }

        currentDisplayedList.setAll(target);
        prefixTrie.clear();
        for (RecoveryItemDTO item : target) {
            prefixTrie.insertTokens(item.getFileName(), item);
            prefixTrie.insertTokens(item.getOriginalPath(), item);
            item.selectedProperty().addListener((obs, oldV, newV) -> updateSelectionSummary());
        }

        // Adjust UI buttons based on source
        if (restoreSelectedBtn != null) {
            restoreSelectedBtn.setVisible(isQuarantine);
            restoreSelectedBtn.setManaged(isQuarantine);
        }
        if (recoverToFolderBtn != null) {
            recoverToFolderBtn.setVisible(isDeepRecovery || !isQuarantine);
            recoverToFolderBtn.setManaged(isDeepRecovery || !isQuarantine);
        }
        if (purgeExpiredBtn != null) {
            purgeExpiredBtn.setVisible(isQuarantine);
            purgeExpiredBtn.setManaged(isQuarantine);
        }

        applyFilter();
        updateSelectionSummary();
    }

    private void applyFilter() {
        String query = searchField.getText() != null ? searchField.getText().trim() : "";
        Set<RecoveryItemDTO> trieMatches = null;
        if (!query.isBlank()) {
            trieMatches = new HashSet<>(prefixTrie.searchByPrefix(query));
        }

        final Set<RecoveryItemDTO> finalMatches = trieMatches;
        filteredList.setPredicate(item -> {
            if (query.isBlank()) return true;
            if (finalMatches != null && finalMatches.contains(item)) return true;
            String lower = query.toLowerCase(Locale.ROOT);
            return item.getFileName().toLowerCase(Locale.ROOT).contains(lower)
                    || item.getOriginalPath().toLowerCase(Locale.ROOT).contains(lower);
        });
    }

    private void updateSelectionSummary() {
        long count = currentDisplayedList.stream().filter(RecoveryItemDTO::isSelected).count();
        long bytes = currentDisplayedList.stream().filter(RecoveryItemDTO::isSelected).mapToLong(RecoveryItemDTO::getSizeBytes).sum();

        if (selectionSummaryLabel != null) {
            selectionSummaryLabel.setText("Selected: " + count + " files (" + FormatUtils.formatBytes(bytes) + ")");
        }
        if (restoreSelectedBtn != null) {
            restoreSelectedBtn.setDisable(count == 0);
        }
        if (recoverToFolderBtn != null) {
            recoverToFolderBtn.setDisable(count == 0);
        }
        if (permanentDeleteBtn != null) {
            permanentDeleteBtn.setDisable(count == 0);
        }
    }

    @FXML
    public void onSelectAll() {
        for (RecoveryItemDTO item : filteredList) {
            item.setSelected(true);
        }
        updateSelectionSummary();
    }

    @FXML
    public void onDeselectAll() {
        for (RecoveryItemDTO item : currentDisplayedList) {
            item.setSelected(false);
        }
        updateSelectionSummary();
    }

    @FXML
    public void onRestoreSelected() {
        List<RecoveryItemDTO> toRestore = currentDisplayedList.stream()
                .filter(RecoveryItemDTO::isSelected)
                .filter(item -> item.getSource() == RecoveryItemDTO.RecoverySource.APP_QUARANTINE && item.getDatabaseId() != null)
                .toList();

        if (toRestore.isEmpty()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Restore");
        confirm.setHeaderText("Restore " + toRestore.size() + " file(s) to original location?");
        confirm.setContentText("Files will be moved back from the quarantine vault. If a file with the same name already exists at the destination, '_restored' will be appended to prevent overwriting.");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            submitAsync(() -> {
                int restoredCount = 0;
                List<String> restoredPaths = new ArrayList<>();

                for (RecoveryItemDTO item : toRestore) {
                    try {
                        Path restoredTo = quarantineService.restoreItem(item.getDatabaseId());
                        restoredPaths.add(restoredTo.toString());
                        restoredCount++;
                    } catch (Exception ex) {
                        log.error("Failed to restore item {}: {}", item.getFileName(), ex.getMessage(), ex);
                    }
                }

                int finalRestored = restoredCount;
                Platform.runLater(() -> {
                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setTitle("Restore Complete");
                    done.setHeaderText("Successfully restored " + finalRestored + " of " + toRestore.size() + " files.");
                    done.setContentText("Restored files have been safely placed back in their original folders.");
                    done.showAndWait();
                    loadAllData();
                });
            });
        }
    }

    @FXML
    public void onPurgeExpired() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Auto-Purge");
        confirm.setHeaderText("Purge Quarantined Items Older Than 30 Days?");
        confirm.setContentText("Files that have been kept in quarantine past their retention expiration date will be permanently deleted from the vault.");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            submitAsync(() -> {
                int count = quarantineService.purgeExpired();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Purge Complete");
                    alert.setHeaderText("Purged " + count + " expired items.");
                    alert.showAndWait();
                    loadAllData();
                });
            });
        }
    }

    @FXML
    public void onRunDeepScan() {
        if (runDeepScanBtn != null) {
            runDeepScanBtn.setDisable(true);
            runDeepScanBtn.setText("⏳ Deep Scanning Drives & Backups...");
        }

        deepScanCancelFlag.set(false);

        submitAsync(() -> {
            try {
                List<RecoveryItemDTO> deepItems = deepRecoveryService.performDeepRecoveryScan(
                        deepScanCancelFlag,
                        (target, count) -> {
                            // Status / progress updates
                        }
                );

                Platform.runLater(() -> {
                    deepRecoveryCache = deepItems;
                    if (deepRecoveryTabBtn != null) {
                        deepRecoveryTabBtn.setSelected(true);
                    }
                    updateActiveTableSource();

                    if (runDeepScanBtn != null) {
                        runDeepScanBtn.setDisable(false);
                        runDeepScanBtn.setText("⚡ Run Deep Recovery Scan");
                    }

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Deep Recovery Scan Completed");
                    alert.setHeaderText("Discovered " + deepItems.size() + " recoverable item(s) outside CleanPilot");
                    alert.setContentText("Discovered files include physical Recycle Bin payloads across mounted drives, application auto-saves, and backup artifacts (.bak, .old, .orig, .asd, ~).");
                    alert.showAndWait();
                });
            } catch (Exception e) {
                log.error("Deep recovery scan failed: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    if (runDeepScanBtn != null) {
                        runDeepScanBtn.setDisable(false);
                        runDeepScanBtn.setText("⚡ Run Deep Recovery Scan");
                    }
                });
            }
        });
    }

    @FXML
    public void onRecoverToFolder() {
        List<RecoveryItemDTO> toRecover = currentDisplayedList.stream()
                .filter(RecoveryItemDTO::isSelected)
                .toList();

        if (toRecover.isEmpty()) return;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Destination Folder to Save Recovered Files");
        File targetDirFile = chooser.showDialog(recoveryTable != null && recoveryTable.getScene() != null ? recoveryTable.getScene().getWindow() : null);
        if (targetDirFile == null) return;

        Path targetDir = targetDirFile.toPath();

        submitAsync(() -> {
            int recoveredCount = 0;
            long recoveredBytes = 0;
            List<String> failedFiles = new ArrayList<>();

            for (RecoveryItemDTO item : toRecover) {
                try {
                    deepRecoveryService.recoverToDirectory(item, targetDir);
                    recoveredCount++;
                    recoveredBytes += item.getSizeBytes();
                } catch (Exception ex) {
                    log.error("Failed to recover item {}: {}", item.getFileName(), ex.getMessage(), ex);
                    failedFiles.add(item.getFileName());
                }
            }

            int finalCount = recoveredCount;
            long finalBytes = recoveredBytes;
            Platform.runLater(() -> {
                Alert done = new Alert(Alert.AlertType.INFORMATION);
                done.setTitle("Files Extracted / Recovered");
                done.setHeaderText("Successfully saved " + finalCount + " of " + toRecover.size() + " files.");
                String content = "Destination: " + targetDir.toAbsolutePath() + "\nTotal recovered size: " + FormatUtils.formatBytes(finalBytes);
                if (!failedFiles.isEmpty()) {
                    content += "\nFailed files: " + String.join(", ", failedFiles);
                }
                done.setContentText(content);
                done.showAndWait();
            });
        });
    }

    @FXML
    public void onPermanentlyDeleteSelected() {
        List<RecoveryItemDTO> toDelete = currentDisplayedList.stream().filter(RecoveryItemDTO::isSelected).toList();
        if (toDelete.isEmpty()) return;

        Alert confirm = new Alert(Alert.AlertType.WARNING);
        confirm.setTitle("Permanent Deletion Warning");
        confirm.setHeaderText("PERMANENTLY DELETE " + toDelete.size() + " SELECTED ITEM(S)?");
        confirm.setContentText("This action is completely IRREVERSIBLE and CANNOT BE UNDONE. The files will be unrecoverable.");

        ButtonType deleteBtnType = new ButtonType("Yes, Permanently Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtnType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(deleteBtnType, cancelBtnType);

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == deleteBtnType) {
            submitAsync(() -> {
                for (RecoveryItemDTO item : toDelete) {
                    try {
                        if (item.getSource() == RecoveryItemDTO.RecoverySource.APP_QUARANTINE && item.getDatabaseId() != null) {
                            quarantineService.permanentlyDeleteItem(item.getDatabaseId());
                        } else if (item.getSource() == RecoveryItemDTO.RecoverySource.SYSTEM_TRASH || item.getSource() == RecoveryItemDTO.RecoverySource.DEEP_RECOVERY) {
                            Path p = Paths.get(item.getCurrentStoragePath());
                            if (java.nio.file.Files.exists(p)) {
                                fileDeletionService.deleteFile(p, false, true);
                            }
                        }
                    } catch (Exception ex) {
                        log.error("Failed to permanently delete item {}: {}", item.getFileName(), ex.getMessage(), ex);
                    }
                }

                deepRecoveryCache.removeAll(toDelete);
                Platform.runLater(this::loadAllData);
            });
        }
    }
}
