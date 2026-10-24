package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

import java.util.Locale;

/**
 * Storage partition / volume metrics: space utilization, SSD TRIM headroom alerts,
 * and filesystem integrity status.
 */
public record StorageVolumeHealthInfo(
        String mountPoint,
        String volumeLabel,
        String fileSystemType,
        long totalBytes,
        long freeBytes,
        long usableBytes,
        double freePercent,
        boolean isLowSpaceAlert,
        boolean isCriticalSpaceAlert,
        String statusMessage
) {
    public long usedBytes() {
        return Math.max(0, totalBytes - freeBytes);
    }

    public double usedPercent() {
        if (totalBytes <= 0) return 0.0;
        return (double) usedBytes() / totalBytes * 100.0;
    }

    public String formattedUsed() {
        return FormatUtils.formatBytes(usedBytes()) + " / " + FormatUtils.formatBytes(totalBytes);
    }

    public String formattedFree() {
        return FormatUtils.formatBytes(freeBytes) + " free (" + String.format(Locale.ROOT, "%.1f%%", freePercent) + ")";
    }

    public String formattedTotal() {
        return FormatUtils.formatBytes(totalBytes);
    }
}
