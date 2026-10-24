package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Represents a disk-space consuming developer artifact (node_modules, build caches, venv, WSL2 disk, etc.).
 */
public class DeveloperArtifactInfo {

    public enum ArtifactType {
        NODE_MODULES("Node.js", "badge-accent"),
        GRADLE_CACHE("Gradle Cache", "badge-subtle"),
        MAVEN_CACHE("Maven Cache", "badge-subtle"),
        RUST_TARGET("Rust (target)", "badge-warning"),
        PYTHON_VENV("Python (venv)", "badge-success"),
        BUILD_OUTPUT("Build Output", "badge-subtle"),
        WSL2_VHDX("WSL2 Virtual Disk", "badge-danger"),
        DOCKER_DATA("Docker Overlay", "badge-accent");

        private final String displayLabel;
        private final String badgeStyleClass;

        ArtifactType(String displayLabel, String badgeStyleClass) {
            this.displayLabel = displayLabel;
            this.badgeStyleClass = badgeStyleClass;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        public String getBadgeStyleClass() {
            return badgeStyleClass;
        }
    }

    private final String id;
    private final String name;
    private final ArtifactType type;
    private final Path path;
    private final Path projectRoot;
    private final long sizeBytes;
    private final int fileCount;
    private final LocalDateTime lastModified;
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public DeveloperArtifactInfo(
            String name,
            ArtifactType type,
            Path path,
            Path projectRoot,
            long sizeBytes,
            int fileCount,
            LocalDateTime lastModified
    ) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
        this.path = path;
        this.projectRoot = projectRoot;
        this.sizeBytes = sizeBytes;
        this.fileCount = fileCount;
        this.lastModified = lastModified;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ArtifactType getType() {
        return type;
    }

    public Path getPath() {
        return path;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getFileCount() {
        return fileCount;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
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

    public String getFormattedLastModified() {
        return lastModified != null ? lastModified.format(DATE_FORMATTER) : "Unknown";
    }

    public String getProjectName() {
        if (projectRoot != null && projectRoot.getFileName() != null) {
            return projectRoot.getFileName().toString();
        }
        return path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }
}
