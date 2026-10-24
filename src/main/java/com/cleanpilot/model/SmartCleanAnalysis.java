package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;
import java.util.List;

/**
 * Read-only analysis snapshot produced before 1-Click Smart Clean execution.
 * Provides categorized findings for the CCleaner-style Health Check view.
 */
public record SmartCleanAnalysis(
        long temporaryFilesCount,
        long temporaryBytes,
        long crashDumpsCount,
        long crashDumpsBytes,
        long browserCacheBytes,
        int expiredQuarantineCount,
        long expiredQuarantineBytes,
        long estimatedMemoryReclaimableBytes,
        long totalBytesReclaimable,
        long totalItemsReclaimable,
        List<SmartCleanTargetItem> targetDetails
) {
    public String formattedTotalReclaimable() {
        return FormatUtils.formatBytes(totalBytesReclaimable);
    }

    public String formattedMemoryReclaimable() {
        return FormatUtils.formatBytes(estimatedMemoryReclaimableBytes);
    }

    public String formattedPrivacyBytes() {
        return FormatUtils.formatBytes(browserCacheBytes);
    }

    public String formattedSpaceBytes() {
        return FormatUtils.formatBytes(temporaryBytes + crashDumpsBytes + expiredQuarantineBytes);
    }

    public record SmartCleanTargetItem(
            String category,
            String description,
            String path,
            long sizeBytes,
            long itemCount
    ) {
        public String formattedSize() {
            return FormatUtils.formatBytes(sizeBytes);
        }
    }
}
