package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Represents a group of identical files sharing the exact same SHA-256 content hash.
 */
public class DuplicateFileGroup {

    private final String sha256Hash;
    private final long singleFileSize;
    private final List<ScannedFileItem> files = new ArrayList<>();

    public DuplicateFileGroup(String sha256Hash, long singleFileSize, List<ScannedFileItem> files) {
        this.sha256Hash = Objects.requireNonNull(sha256Hash, "sha256Hash must not be null");
        this.singleFileSize = Math.max(0, singleFileSize);
        if (files != null) {
            this.files.addAll(files);
            // Sort by oldest lastModified first so original copy is at index 0
            this.files.sort(Comparator.comparing(ScannedFileItem::getLastModified));
        }
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public long getSingleFileSize() {
        return singleFileSize;
    }

    public List<ScannedFileItem> getFiles() {
        return files;
    }

    public int getFileCount() {
        return files.size();
    }

    public long getTotalSize() {
        return singleFileSize * files.size();
    }

    public long getReclaimableSize() {
        if (files.size() <= 1) {
            return 0;
        }
        return singleFileSize * (files.size() - 1);
    }

    public String getFormattedSingleSize() {
        return FormatUtils.formatBytes(singleFileSize);
    }

    public String getFormattedTotalSize() {
        return FormatUtils.formatBytes(getTotalSize());
    }

    public String getFormattedReclaimableSize() {
        return FormatUtils.formatBytes(getReclaimableSize());
    }

    public ScannedFileItem getOriginalFile() {
        return files.isEmpty() ? null : files.get(0);
    }

    /**
     * Marks all duplicates (except the oldest/original copy) as selected for cleanup.
     */
    public void markDuplicatesForRemoval() {
        markKeepOldest();
    }

    /**
     * Keeps the oldest file (by lastModified) and marks all newer copies for cleanup.
     */
    public void markKeepOldest() {
        if (files.isEmpty()) return;
        ScannedFileItem oldest = files.stream()
                .min(Comparator.comparing(ScannedFileItem::getLastModified))
                .orElse(files.get(0));
        for (ScannedFileItem f : files) {
            f.setSelected(f != oldest);
        }
    }

    /**
     * Keeps the newest file (by lastModified) and marks all older copies for cleanup.
     */
    public void markKeepNewest() {
        if (files.isEmpty()) return;
        ScannedFileItem newest = files.stream()
                .max(Comparator.comparing(ScannedFileItem::getLastModified))
                .orElse(files.get(0));
        for (ScannedFileItem f : files) {
            f.setSelected(f != newest);
        }
    }

    /**
     * Keeps the copy with the shortest file path (avoids deeply nested redundant duplicates)
     * and marks the others for removal.
     */
    public void markKeepShortestPath() {
        if (files.isEmpty()) return;
        ScannedFileItem shortest = files.stream()
                .min(Comparator.comparingInt(f -> f.getPath().toString().length()))
                .orElse(files.get(0));
        for (ScannedFileItem f : files) {
            f.setSelected(f != shortest);
        }
    }

    /**
     * Clears selection on all items in this group.
     */
    public void clearSelection() {
        for (ScannedFileItem file : files) {
            file.setSelected(false);
        }
    }
}
