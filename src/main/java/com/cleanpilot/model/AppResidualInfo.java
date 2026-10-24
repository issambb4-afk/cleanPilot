package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Represents an orphaned or leftover configuration/data directory in AppData or ProgramData.
 */
public class AppResidualInfo {

    private final String id;
    private final String appName;
    private final Path path;
    private final long sizeBytes;
    private final String sourceArea;
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    public AppResidualInfo(String appName, Path path, long sizeBytes, String sourceArea) {
        this.id = UUID.randomUUID().toString();
        this.appName = appName;
        this.path = path;
        this.sizeBytes = sizeBytes;
        this.sourceArea = sourceArea;
    }

    public String getId() {
        return id;
    }

    public String getAppName() {
        return appName;
    }

    public Path getPath() {
        return path;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSourceArea() {
        return sourceArea;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean val) {
        selected.set(val);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public String getFormattedSize() {
        return FormatUtils.formatBytes(sizeBytes);
    }
}
