package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.time.LocalDateTime;

/**
 * Common presentation DTO unifying Tier A (App Quarantine Vault)
 * and Tier B (Native OS System Recycle Bin) items.
 */
public class RecoveryItemDTO {

    public enum RecoverySource {
        APP_QUARANTINE,
        SYSTEM_TRASH,
        DEEP_RECOVERY
    }

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Long databaseId; // non-null for App Quarantine
    private final String fileName;
    private final String originalPath;
    private final String currentStoragePath;
    private final long sizeBytes;
    private final LocalDateTime deletedAt;
    private final LocalDateTime expiresAt;
    private final RecoverySource source;
    private final String additionalDetails;

    public RecoveryItemDTO(
            Long databaseId,
            String fileName,
            String originalPath,
            String currentStoragePath,
            long sizeBytes,
            LocalDateTime deletedAt,
            LocalDateTime expiresAt,
            RecoverySource source,
            String additionalDetails
    ) {
        this.databaseId = databaseId;
        this.fileName = fileName;
        this.originalPath = originalPath;
        this.currentStoragePath = currentStoragePath;
        this.sizeBytes = Math.max(0, sizeBytes);
        this.deletedAt = deletedAt != null ? deletedAt : LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.source = source;
        this.additionalDetails = additionalDetails != null ? additionalDetails : "";
    }

    public static RecoveryItemDTO fromQuarantinedItem(QuarantinedItem item) {
        return new RecoveryItemDTO(
                item.getId(),
                item.getFileName(),
                item.getOriginalPath(),
                item.getQuarantinePath(),
                item.getFileSizeBytes() != null ? item.getFileSizeBytes() : 0,
                item.getDeletedAt(),
                item.getExpiresAt(),
                RecoverySource.APP_QUARANTINE,
                item.getSha256Hash() != null ? "SHA-256: " + item.getSha256Hash().substring(0, Math.min(12, item.getSha256Hash().length())) + "..." : "Vault Staged"
        );
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean val) {
        this.selected.set(val);
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getOriginalPath() {
        return originalPath;
    }

    public String getCurrentStoragePath() {
        return currentStoragePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getFormattedSize() {
        return FormatUtils.formatBytes(sizeBytes);
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public RecoverySource getSource() {
        return source;
    }

    public String getAdditionalDetails() {
        return additionalDetails;
    }

    public String getFormattedDaysRemaining() {
        if (source == RecoverySource.DEEP_RECOVERY) {
            return "Orphaned Artifact";
        }
        if (source == RecoverySource.SYSTEM_TRASH || expiresAt == null) {
            return "OS Managed";
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expiresAt);
        if (days < 0) return "Expired (Purge Pending)";
        if (days == 0) return "Expires Today";
        return days + " days left";
    }
}
