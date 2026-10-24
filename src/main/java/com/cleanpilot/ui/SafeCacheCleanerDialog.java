package com.cleanpilot.ui;

import com.cleanpilot.model.CachePresetItem;
import com.cleanpilot.service.CachePresetService;
import com.cleanpilot.service.FileDeletionService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Dialog enabling safe one-click cleaning of software development & system caches
 * (.gradle/caches, .m2, npm-cache, pip, user temp) without risking source code or settings.
 */
public class SafeCacheCleanerDialog extends Stage {

    private static final Logger log = LoggerFactory.getLogger(SafeCacheCleanerDialog.class);

    private final CachePresetService cachePresetService;
    private final FileDeletionService deletionService;
    private final ExecutorService executorService;

    private final VBox listContainer = new VBox(8);
    private final Label summaryLabel = new Label("Scanning cache locations...");
    private final Button cleanSelectedBtn = new Button("🗑 Safe Clean Selected");
    private final List<CheckBox> checkBoxes = new ArrayList<>();
    private final Map<CheckBox, CachePresetItem> itemMap = new HashMap<>();

    public SafeCacheCleanerDialog(CachePresetService cachePresetService, FileDeletionService deletionService, ExecutorService executorService) {
        this.cachePresetService = cachePresetService;
        this.deletionService = deletionService;
        this.executorService = executorService;

        initModality(Modality.APPLICATION_MODAL);
        setTitle("Safe Development & System Cache Cleaner");
        setMinWidth(680);
        setMinHeight(520);

        buildUi();
        loadPresets();
    }

    public static void show(Window owner, CachePresetService cachePresetService, FileDeletionService deletionService, ExecutorService executorService) {
        SafeCacheCleanerDialog dialog = new SafeCacheCleanerDialog(cachePresetService, deletionService, executorService);
        if (owner != null) dialog.initOwner(owner);
        dialog.show();
    }

    private void buildUi() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.getStyleClass().add("app-root");

        VBox topBox = new VBox(4);
        topBox.setPadding(new Insets(0, 0, 12, 0));
        Label title = new Label("Smart Cache & Build Artifact Presets");
        title.setStyle("-fx-font-size: 1.3em; -fx-font-weight: bold;");
        Label subtitle = new Label("Safely reclaim gigabytes of orphaned build dependencies and temporary caches.");
        subtitle.getStyleClass().add("text-subtle");
        topBox.getChildren().addAll(title, subtitle);
        root.setTop(topBox);

        ScrollPane scrollPane = new ScrollPane(listContainer);
        scrollPane.setFitToWidth(true);
        root.setCenter(scrollPane);

        HBox bottomBox = new HBox(12);
        bottomBox.setAlignment(Pos.CENTER_RIGHT);
        bottomBox.setPadding(new Insets(12, 0, 0, 0));

        cleanSelectedBtn.getStyleClass().add("danger");
        cleanSelectedBtn.setDisable(true);
        cleanSelectedBtn.setOnAction(e -> handleCleanSelected());

        Button cancelBtn = new Button("Close");
        cancelBtn.setOnAction(e -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomBox.getChildren().addAll(summaryLabel, spacer, cleanSelectedBtn, cancelBtn);
        root.setBottom(bottomBox);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    private void loadPresets() {
        executorService.submit(() -> {
            List<CachePresetItem> presets = cachePresetService.scanPresets();
            Platform.runLater(() -> {
                listContainer.getChildren().clear();
                checkBoxes.clear();
                itemMap.clear();

                long totalBytes = 0;
                for (CachePresetItem item : presets) {
                    totalBytes += item.calculatedSizeBytes();

                    VBox card = new VBox(4);
                    card.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 10 14; -fx-background-radius: 6; -fx-border-color: -color-border-muted; -fx-border-radius: 6;");

                    HBox titleRow = new HBox(8);
                    titleRow.setAlignment(Pos.CENTER_LEFT);

                    CheckBox cb = new CheckBox();
                    cb.setSelected(true);
                    cb.setOnAction(e -> updateSummary());
                    checkBoxes.add(cb);
                    itemMap.put(cb, item);

                    Label nameLbl = new Label(item.name());
                    nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 1.05em;");

                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    Label sizeLbl = new Label(item.formattedSize() + " (" + item.fileCount() + " files)");
                    sizeLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-accent-emphasis;");

                    titleRow.getChildren().addAll(cb, nameLbl, sp, sizeLbl);

                    Label descLbl = new Label(item.description());
                    descLbl.getStyleClass().add("text-subtle");

                    Label pathLbl = new Label(item.path().toString());
                    pathLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 0.85em; -fx-text-fill: -color-fg-muted;");

                    card.getChildren().addAll(titleRow, descLbl, pathLbl);
                    listContainer.getChildren().add(card);
                }

                updateSummary();
            });
        });
    }

    private void updateSummary() {
        long selectedBytes = 0;
        int count = 0;
        for (CheckBox cb : checkBoxes) {
            if (cb.isSelected()) {
                CachePresetItem item = itemMap.get(cb);
                if (item != null) {
                    selectedBytes += item.calculatedSizeBytes();
                    count++;
                }
            }
        }
        summaryLabel.setText("Selected: " + count + " caches (" + FormatUtils.formatBytes(selectedBytes) + ")");
        cleanSelectedBtn.setDisable(count == 0);
    }

    private void handleCleanSelected() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Safe Cache Cleanup");
        confirm.setHeaderText("Purge Selected Cache Targets?");
        confirm.setContentText("The contents of the selected cache directories will be safely cleared. Tools will repopulate missing files automatically when needed.");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            cleanSelectedBtn.setDisable(true);
            summaryLabel.setText("Purging cache directories...");

            executorService.submit(() -> {
                for (CheckBox cb : checkBoxes) {
                    if (cb.isSelected()) {
                        CachePresetItem item = itemMap.get(cb);
                        if (item != null && Files.exists(item.path())) {
                            try {
                                deletionService.deleteFile(item.path(), true, true);
                            } catch (Exception ex) {
                                log.warn("Failed to delete cache {}: {}", item.path(), ex.getMessage());
                            }
                        }
                    }
                }

                Platform.runLater(() -> {
                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setTitle("Cleanup Complete");
                    done.setHeaderText("Selected Caches Safely Purged");
                    done.setContentText("Disk space has been recovered successfully.");
                    done.showAndWait();
                    close();
                });
            });
        }
    }
}
