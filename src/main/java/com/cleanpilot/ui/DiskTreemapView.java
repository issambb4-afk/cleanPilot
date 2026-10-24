package com.cleanpilot.ui;

import com.cleanpilot.model.ScannedFileItem;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.*;

/**
 * Interactive Treemap canvas displaying files by proportional byte area with squarified layout
 * and category-based color styling (similar to WinDirStat / DaisyDisk).
 */
public class DiskTreemapView extends Pane {

    private record TreemapNode(
            ScannedFileItem item,
            double x,
            double y,
            double width,
            double height,
            Color color
    ) {
        boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    private final Canvas canvas = new Canvas();
    private final List<TreemapNode> renderedNodes = new ArrayList<>();
    private List<ScannedFileItem> fileItems = Collections.emptyList();
    private final Tooltip tooltip = new Tooltip();

    public DiskTreemapView() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        widthProperty().addListener((obs, o, n) -> render());
        heightProperty().addListener((obs, o, n) -> render());

        Tooltip.install(canvas, tooltip);

        canvas.setOnMouseMoved(this::handleMouseHover);
        canvas.setOnMouseClicked(this::handleMouseClicked);
    }

    public void setFiles(List<ScannedFileItem> files) {
        this.fileItems = files != null ? files : Collections.emptyList();
        render();
    }

    private void handleMouseHover(MouseEvent event) {
        for (TreemapNode node : renderedNodes) {
            if (node.contains(event.getX(), event.getY())) {
                tooltip.setText(node.item.getFileName() + "\n" +
                        "Size: " + node.item.getFormattedSize() + "\n" +
                        "Category: " + node.item.getCategory() + "\n" +
                        "Path: " + node.item.getAbsolutePathString() + "\n" +
                        (node.item.isSelected() ? "Status: [SELECTED]" : "Click to select"));
                return;
            }
        }
        tooltip.hide();
    }

    private void handleMouseClicked(MouseEvent event) {
        for (TreemapNode node : renderedNodes) {
            if (node.contains(event.getX(), event.getY())) {
                if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    node.item.setSelected(!node.item.isSelected());
                    render();
                } else if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
                    javafx.scene.control.MenuItem openItem = new javafx.scene.control.MenuItem("📁 Reveal in File Explorer");
                    openItem.setOnAction(e -> {
                        try {
                            java.awt.Desktop.getDesktop().open(new java.io.File(node.item.getAbsolutePathString()).getParentFile());
                        } catch (Exception ignored) {}
                    });

                    javafx.scene.control.MenuItem toggleItem = new javafx.scene.control.MenuItem(
                            node.item.isSelected() ? "Deselect File" : "Select File"
                    );
                    toggleItem.setOnAction(e -> {
                        node.item.setSelected(!node.item.isSelected());
                        render();
                    });

                    menu.getItems().addAll(openItem, toggleItem);
                    menu.show(canvas, event.getScreenX(), event.getScreenY());
                }
                return;
            }
        }
    }

    public void render() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        renderedNodes.clear();

        if (fileItems.isEmpty()) {
            gc.setFill(Color.web("#8c959f", 0.5));
            gc.fillText("Scan a directory to generate visual storage treemap", w / 2 - 140, h / 2);
            return;
        }

        // Take top 80 largest files to prevent microscopic visual clutter
        List<ScannedFileItem> topFiles = fileItems.stream()
                .sorted((a, b) -> Long.compare(b.getSizeInBytes(), a.getSizeInBytes()))
                .limit(80)
                .toList();

        long totalSize = topFiles.stream().mapToLong(ScannedFileItem::getSizeInBytes).sum();
        if (totalSize == 0) return;

        layoutSlice(topFiles, 0, 0, w, h, totalSize, true);

        // Draw rendered nodes
        for (TreemapNode node : renderedNodes) {
            gc.setFill(node.color);
            gc.fillRect(node.x + 1, node.y + 1, Math.max(1, node.width - 2), Math.max(1, node.height - 2));

            if (node.item.isSelected()) {
                gc.setStroke(Color.web("#f78166"));
                gc.setLineWidth(2.5);
                gc.strokeRect(node.x + 1.5, node.y + 1.5, Math.max(1, node.width - 3), Math.max(1, node.height - 3));
                gc.setLineWidth(1.0);
            } else {
                gc.setStroke(Color.web("#ffffff", 0.25));
                gc.strokeRect(node.x + 1, node.y + 1, Math.max(1, node.width - 2), Math.max(1, node.height - 2));
            }

            // Draw label if box is large enough
            if (node.width > 50 && node.height > 24) {
                gc.setFill(Color.WHITE);
                String label = (node.item.isSelected() ? "✓ " : "") + node.item.getFileName();
                if (label.length() > (int) (node.width / 7)) {
                    label = label.substring(0, Math.max(3, (int) (node.width / 7) - 3)) + "...";
                }
                gc.fillText(label, node.x + 4, node.y + 16);
            }
        }
    }

    private void layoutSlice(List<ScannedFileItem> items, double x, double y, double w, double h, long totalSize, boolean vertical) {
        if (items.isEmpty() || w <= 2 || h <= 2) return;

        if (items.size() == 1) {
            ScannedFileItem item = items.get(0);
            Color color = resolveCategoryColor(item.getCategory());
            renderedNodes.add(new TreemapNode(item, x, y, w, h, color));
            return;
        }

        // Split items into two roughly equal halves by byte weight
        long half = totalSize / 2;
        long current = 0;
        int splitIdx = 0;

        for (int i = 0; i < items.size(); i++) {
            current += items.get(i).getSizeInBytes();
            if (current >= half || i == items.size() - 2) {
                splitIdx = i + 1;
                break;
            }
        }

        List<ScannedFileItem> group1 = items.subList(0, splitIdx);
        List<ScannedFileItem> group2 = items.subList(splitIdx, items.size());

        long size1 = group1.stream().mapToLong(ScannedFileItem::getSizeInBytes).sum();
        long size2 = group2.stream().mapToLong(ScannedFileItem::getSizeInBytes).sum();
        double ratio = (double) size1 / (double) totalSize;

        if (vertical) {
            double splitW = w * ratio;
            layoutSlice(group1, x, y, splitW, h, size1, false);
            layoutSlice(group2, x + splitW, y, w - splitW, h, size2, false);
        } else {
            double splitH = h * ratio;
            layoutSlice(group1, x, y, w, splitH, size1, true);
            layoutSlice(group2, x, y + splitH, w, h - splitH, size2, true);
        }
    }

    private Color resolveCategoryColor(String category) {
        if (category == null) return Color.web("#6e7781");
        return switch (category.toLowerCase()) {
            case "video", "media", "audio" -> Color.web("#8250df"); // Purple
            case "archive", "compressed", "zip" -> Color.web("#cf222e"); // Red
            case "disk image", "iso", "vmdk" -> Color.web("#d4a72c"); // Orange
            case "development", "binary", "executable" -> Color.web("#0969da"); // Blue
            case "document", "pdf" -> Color.web("#1a7f37"); // Green
            default -> Color.web("#57606a"); // Neutral gray
        };
    }
}
