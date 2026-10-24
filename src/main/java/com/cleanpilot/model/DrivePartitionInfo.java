package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

/**
 * Encapsulates physical or logical drive partition telemetry.
 */
public record DrivePartitionInfo(
        String name,
        String mountPoint,
        String type,
        long totalBytes,
        long freeBytes,
        long usableBytes,
        boolean readOnly
) {

    public long usedBytes() {
        return Math.max(0, totalBytes - freeBytes);
    }

    public double usedPercentage() {
        if (totalBytes <= 0) {
            return 0.0;
        }
        return (double) usedBytes() / (double) totalBytes;
    }

    public String formattedTotal() {
        return FormatUtils.formatBytes(totalBytes);
    }

    public String formattedFree() {
        return FormatUtils.formatBytes(freeBytes);
    }

    public String formattedUsed() {
        return FormatUtils.formatBytes(usedBytes());
    }

    public String formattedUsedPercentage() {
        return FormatUtils.formatPercentage(usedPercentage());
    }
}
