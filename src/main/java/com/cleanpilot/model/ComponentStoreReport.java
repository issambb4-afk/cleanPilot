package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

/**
 * Diagnostic analysis report of the Windows Component Store (WinSxS).
 */
public record ComponentStoreReport(
        long actualStoreSizeBytes,
        long sharedWithWindowsBytes,
        long backupsAndDisabledFeaturesBytes,
        long reclaimableBytes,
        boolean cleanupRecommended,
        String summaryText
) {
    public String formattedActualStoreSize() {
        return FormatUtils.formatBytes(actualStoreSizeBytes);
    }

    public String formattedReclaimable() {
        return FormatUtils.formatBytes(reclaimableBytes);
    }

    public String formattedShared() {
        return FormatUtils.formatBytes(sharedWithWindowsBytes);
    }
}
