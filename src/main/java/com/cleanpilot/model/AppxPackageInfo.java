package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

/**
 * Universal Windows Platform (UWP / AppX) application metadata.
 */
public record AppxPackageInfo(
        String name,
        String displayName,
        String packageFullName,
        String publisher,
        String installLocation,
        long estimatedSizeBytes,
        boolean isRemovable,
        String category,
        String description
) {
    public String formattedSize() {
        return estimatedSizeBytes > 0 ? FormatUtils.formatBytes(estimatedSizeBytes) : "< 1 MB";
    }

    public String displayCategory() {
        return (category != null && !category.isBlank()) ? category : "General App";
    }
}
