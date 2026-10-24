package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single file discovered during storage scanning with metadata and hardlink awareness.
 */
public class ScannedFileItem {

    private final Path path;
    private final String fileName;
    private final String extension;
    private final long sizeInBytes;
    private final Instant lastModified;
    private final String category;
    private final Object fileKey; // Native OS inode / file identifier for hardlink detection
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    public ScannedFileItem(Path path, long sizeInBytes, Instant lastModified) {
        this(path, sizeInBytes, lastModified, null);
    }

    public ScannedFileItem(Path path, long sizeInBytes, Instant lastModified, Object fileKey) {
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.fileName = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        this.extension = FormatUtils.getFileExtension(this.fileName);
        this.sizeInBytes = Math.max(0, sizeInBytes);
        this.lastModified = lastModified != null ? lastModified : Instant.now();
        this.category = FormatUtils.categorize(this.extension, path);
        this.fileKey = fileKey;
    }

    public Path getPath() {
        return path;
    }

    public String getAbsolutePathString() {
        return path.toAbsolutePath().toString();
    }

    public String getFileName() {
        return fileName;
    }

    public String getExtension() {
        return extension;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public String getFormattedSize() {
        return FormatUtils.formatBytes(sizeInBytes);
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public String getFormattedLastModified() {
        return FormatUtils.formatInstant(lastModified);
    }

    public String getCategory() {
        return category;
    }

    public Object getFileKey() {
        return fileKey;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isStale(long daysThreshold) {
        if (lastModified == null) return false;
        long daysOld = Duration.between(lastModified, Instant.now()).toDays();
        return daysOld >= daysThreshold;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScannedFileItem that = (ScannedFileItem) o;
        return Objects.equals(path.toAbsolutePath().normalize(), that.path.toAbsolutePath().normalize());
    }

    @Override
    public int hashCode() {
        return Objects.hash(path.toAbsolutePath().normalize());
    }

    @Override
    public String toString() {
        return "ScannedFileItem{" +
                "path=" + path +
                ", size=" + getFormattedSize() +
                ", category='" + category + '\'' +
                '}';
    }
}
