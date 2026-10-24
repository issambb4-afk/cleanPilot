package com.cleanpilot.controller;

import com.cleanpilot.model.ScanHistoryEntity;
import com.cleanpilot.service.ScanHistoryService;
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
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Component
public class HistoryViewController implements Initializable {

    @FXML private ComboBox<String> typeFilterComboBox;
    @FXML private ComboBox<String> sortByComboBox;
    @FXML private TextField searchField;

    @FXML private TableView<ScanHistoryEntity> historyTable;
    @FXML private TableColumn<ScanHistoryEntity, Instant> dateCol;
    @FXML private TableColumn<ScanHistoryEntity, String> typeCol;
    @FXML private TableColumn<ScanHistoryEntity, String> targetCol;
    @FXML private TableColumn<ScanHistoryEntity, Long> identifiedCol;
    @FXML private TableColumn<ScanHistoryEntity, Long> recoveredCol;
    @FXML private TableColumn<ScanHistoryEntity, Long> durationCol;
    @FXML private TableColumn<ScanHistoryEntity, String> statusCol;

    @FXML private Label totalRecoveredLabel;
    @FXML private Label totalItemsLabel;
    @FXML private Label totalScansLabel;

    private final ScanHistoryService historyService;
    private final ExecutorService executorService;
    private final ObservableList<ScanHistoryEntity> historyList = FXCollections.observableArrayList();
    private FilteredList<ScanHistoryEntity> filteredHistoryList;
    private final PrefixTrie<ScanHistoryEntity> prefixTrie = new PrefixTrie<>();

    public HistoryViewController(ScanHistoryService historyService, ExecutorService executorService) {
        this.historyService = historyService;
        this.executorService = executorService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        historyTable.setPlaceholder(new Label("No previous scan history found."));

        // Setup filter dropdown
        typeFilterComboBox.getItems().setAll("All Operations", "LARGE_FILES", "DUPLICATE_FILES");
        typeFilterComboBox.setValue("All Operations");
        typeFilterComboBox.valueProperty().addListener((obs, oldV, newV) -> applyFilters());

        // Setup sort dropdown
        sortByComboBox.getItems().setAll(
                "Timestamp (Newest First)",
                "Timestamp (Oldest First)",
                "Space Recovered (Highest First)",
                "Items Identified (Highest First)",
                "Duration (Longest First)",
                "Target Directory (A-Z)"
        );
        sortByComboBox.setValue("Timestamp (Newest First)");
        sortByComboBox.valueProperty().addListener((obs, oldV, newV) -> updateTableSort(newV));

        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilters());

        // Column cell value & render factories
        dateCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getTimestamp()));
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Instant item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatInstant(item));
                }
            }
        });

        typeCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getScanType()));
        targetCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getTargetDirectory()));

        identifiedCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getItemsIdentified()));
        identifiedCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    ScanHistoryEntity row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) {
                        setText(FormatUtils.formatNumber(item) + " (" + row.getFormattedBytesIdentified() + ")");
                    } else {
                        setText(FormatUtils.formatNumber(item));
                    }
                }
            }
        });

        recoveredCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getBytesRecovered()));
        recoveredCol.setCellFactory(col -> new TableCell<>() {
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

        durationCol.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getDurationMillis()));
        durationCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(FormatUtils.formatDuration(item));
                }
            }
        });

        statusCol.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getStatus()));

        filteredHistoryList = new FilteredList<>(historyList, p -> true);
        SortedList<ScanHistoryEntity> sortedHistoryList = new SortedList<>(filteredHistoryList);
        sortedHistoryList.comparatorProperty().bind(historyTable.comparatorProperty());
        historyTable.setItems(sortedHistoryList);

        updateTableSort(sortByComboBox.getValue());
        loadHistory();
    }

    private void updateTableSort(String sortCriteria) {
        if (sortCriteria == null) return;
        historyTable.getSortOrder().clear();
        switch (sortCriteria) {
            case "Timestamp (Newest First)" -> {
                dateCol.setSortType(TableColumn.SortType.DESCENDING);
                historyTable.getSortOrder().add(dateCol);
            }
            case "Timestamp (Oldest First)" -> {
                dateCol.setSortType(TableColumn.SortType.ASCENDING);
                historyTable.getSortOrder().add(dateCol);
            }
            case "Space Recovered (Highest First)" -> {
                recoveredCol.setSortType(TableColumn.SortType.DESCENDING);
                historyTable.getSortOrder().add(recoveredCol);
            }
            case "Items Identified (Highest First)" -> {
                identifiedCol.setSortType(TableColumn.SortType.DESCENDING);
                historyTable.getSortOrder().add(identifiedCol);
            }
            case "Duration (Longest First)" -> {
                durationCol.setSortType(TableColumn.SortType.DESCENDING);
                historyTable.getSortOrder().add(durationCol);
            }
            case "Target Directory (A-Z)" -> {
                targetCol.setSortType(TableColumn.SortType.ASCENDING);
                historyTable.getSortOrder().add(targetCol);
            }
        }
    }

    private void applyFilters() {
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String opType = typeFilterComboBox.getValue();
        Set<ScanHistoryEntity> trieMatches = query.isEmpty() ? null : new HashSet<>(prefixTrie.searchByPrefix(query));

        filteredHistoryList.setPredicate(record -> {
            boolean matchesSearch = query.isEmpty()
                    || (trieMatches != null && trieMatches.contains(record))
                    || (record.getTargetDirectory() != null && record.getTargetDirectory().toLowerCase(Locale.ROOT).contains(query))
                    || (record.getStatus() != null && record.getStatus().toLowerCase(Locale.ROOT).contains(query));

            boolean matchesType = (opType == null || "All Operations".equals(opType) || opType.equalsIgnoreCase(record.getScanType()));

            return matchesSearch && matchesType;
        });
    }

    @FXML
    public void loadHistory() {
        submitAsync(() -> {
            List<ScanHistoryEntity> records = historyService.getAllHistory();
            long totalRecovered = historyService.getTotalBytesRecovered();
            long totalItems = historyService.getTotalItemsDeleted();

            Platform.runLater(() -> {
                prefixTrie.clear();
                for (ScanHistoryEntity e : records) {
                    if (e.getTargetDirectory() != null) {
                        prefixTrie.insertTokens(e.getTargetDirectory(), e);
                    }
                    if (e.getStatus() != null) {
                        prefixTrie.insert(e.getStatus(), e);
                    }
                }
                historyList.setAll(records);
                applyFilters();
                updateTableSort(sortByComboBox.getValue());

                if (totalRecoveredLabel != null) {
                    totalRecoveredLabel.setText(FormatUtils.formatBytes(totalRecovered));
                }
                if (totalItemsLabel != null) {
                    totalItemsLabel.setText(FormatUtils.formatNumber(totalItems));
                }
                if (totalScansLabel != null) {
                    totalScansLabel.setText(String.valueOf(records.size()));
                }
            });
        });
    }

    @FXML
    public void onClearHistory() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear Audit Logs");
        confirm.setHeaderText("Clear all past scan and deletion history?");
        confirm.setContentText("This only removes past activity records from the local SQLite database. It does not affect any filesystem files.");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            submitAsync(() -> {
                historyService.clearHistory();
                Platform.runLater(this::loadHistory);
            });
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
