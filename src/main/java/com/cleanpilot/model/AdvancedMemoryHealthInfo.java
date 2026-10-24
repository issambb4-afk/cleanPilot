package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

import java.util.Locale;

/**
 * Deep memory diagnostics: physical RAM, swap/pagefile, commit limit saturation,
 * and kernel memory pools (Paged & Non-Paged pool leak detection).
 */
public record AdvancedMemoryHealthInfo(
        long physicalTotalBytes,
        long physicalUsedBytes,
        long physicalAvailableBytes,
        double physicalUsedPercent,
        long commitLimitBytes,
        long commitTotalBytes,
        double commitUsedPercent,
        long pagedPoolBytes,
        long nonPagedPoolBytes,
        boolean isNonPagedPoolLeak,
        boolean isCommitSaturationRisk,
        long swapTotalBytes,
        long swapUsedBytes,
        double swapUsedPercent,
        boolean isThrashingDetected,
        String memoryStatus
) {
    public String formattedPhysicalUsed() {
        return FormatUtils.formatBytes(physicalUsedBytes) + " / " + FormatUtils.formatBytes(physicalTotalBytes);
    }

    public String formattedPhysicalPercent() {
        return String.format(Locale.ROOT, "%.1f%%", physicalUsedPercent);
    }

    public String formattedCommit() {
        if (commitLimitBytes <= 0) return "Not monitored";
        return FormatUtils.formatBytes(commitTotalBytes) + " / " + FormatUtils.formatBytes(commitLimitBytes)
                + " (" + String.format(Locale.ROOT, "%.1f%%", commitUsedPercent) + ")";
    }

    public String formattedNonPagedPool() {
        if (nonPagedPoolBytes <= 0) return "Normal";
        return FormatUtils.formatBytes(nonPagedPoolBytes);
    }

    public String formattedPagedPool() {
        if (pagedPoolBytes <= 0) return "Normal";
        return FormatUtils.formatBytes(pagedPoolBytes);
    }
}
