package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scan_history")
public class ScanHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant scanTimestamp;

    @Column(nullable = false, length = 32)
    private String scanType;

    @Column(nullable = false, length = 1024)
    private String targetDirectory;

    private long filesScanned;
    private long bytesScanned;
    private long itemsIdentified;
    private long bytesIdentified;
    private long itemsDeleted;
    private long bytesRecovered;
    private long durationMillis;

    @Column(nullable = false, length = 32)
    private String status;

    public ScanHistoryEntity() {
    }

    public ScanHistoryEntity(String scanType, String targetDirectory) {
        this.scanTimestamp = Instant.now();
        this.scanType = scanType;
        this.targetDirectory = targetDirectory;
        this.status = "COMPLETED";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getScanTimestamp() {
        return scanTimestamp;
    }

    public Instant getTimestamp() {
        return scanTimestamp;
    }

    public void setScanTimestamp(Instant scanTimestamp) {
        this.scanTimestamp = scanTimestamp;
    }

    public String getScanType() {
        return scanType;
    }

    public void setScanType(String scanType) {
        this.scanType = scanType;
    }

    public String getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public long getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(long filesScanned) {
        this.filesScanned = filesScanned;
    }

    public long getBytesScanned() {
        return bytesScanned;
    }

    public void setBytesScanned(long bytesScanned) {
        this.bytesScanned = bytesScanned;
    }

    public long getItemsIdentified() {
        return itemsIdentified;
    }

    public void setItemsIdentified(long itemsIdentified) {
        this.itemsIdentified = itemsIdentified;
    }

    public long getBytesIdentified() {
        return bytesIdentified;
    }

    public void setBytesIdentified(long bytesIdentified) {
        this.bytesIdentified = bytesIdentified;
    }

    public long getItemsDeleted() {
        return itemsDeleted;
    }

    public void setItemsDeleted(long itemsDeleted) {
        this.itemsDeleted = itemsDeleted;
    }

    public long getBytesRecovered() {
        return bytesRecovered;
    }

    public void setBytesRecovered(long bytesRecovered) {
        this.bytesRecovered = bytesRecovered;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFormattedTimestamp() {
        return FormatUtils.formatInstant(scanTimestamp);
    }

    public String getFormattedBytesRecovered() {
        return FormatUtils.formatBytes(bytesRecovered);
    }

    public String getFormattedBytesIdentified() {
        return FormatUtils.formatBytes(bytesIdentified);
    }

    public String getFormattedDuration() {
        return FormatUtils.formatDuration(durationMillis);
    }
}
