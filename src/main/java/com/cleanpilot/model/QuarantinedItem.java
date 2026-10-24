package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a file moved to the CleanPilot Application Quarantine Vault
 * for safe soft-delete and 1-click restoration.
 */
@Entity
@Table(name = "quarantine_items")
public class QuarantinedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1024)
    private String originalPath;

    @Column(nullable = false, length = 1024)
    private String quarantinePath;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(length = 64)
    private String sha256Hash;

    @Column(nullable = false)
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public QuarantinedItem() {
    }

    public QuarantinedItem(
            String originalPath,
            String quarantinePath,
            String fileName,
            Long fileSizeBytes,
            String sha256Hash,
            LocalDateTime deletedAt,
            LocalDateTime expiresAt
    ) {
        this.originalPath = originalPath;
        this.quarantinePath = quarantinePath;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.sha256Hash = sha256Hash;
        this.deletedAt = deletedAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    public String getQuarantinePath() {
        return quarantinePath;
    }

    public void setQuarantinePath(String quarantinePath) {
        this.quarantinePath = quarantinePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getFormattedSize() {
        return FormatUtils.formatBytes(fileSizeBytes != null ? fileSizeBytes : 0);
    }

    public long getDaysLeftUntilExpiry() {
        if (expiresAt == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expiresAt);
    }
}
