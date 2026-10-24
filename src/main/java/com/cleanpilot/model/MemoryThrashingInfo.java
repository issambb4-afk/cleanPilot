package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

import java.util.Locale;

/**
 * Memory paging, swap file utilization, and virtual memory thrashing diagnostics.
 */
public record MemoryThrashingInfo(
        long physicalTotalBytes,
        long physicalUsedBytes,
        long physicalAvailableBytes,
        double physicalUsedPercent,
        long swapTotalBytes,
        long swapUsedBytes,
        double swapUsedPercent,
        long swapPagesIn,
        long swapPagesOut,
        boolean isThrashingDetected,
        String memoryHealthStatus
) {

    public String formattedPhysicalUsed() {
        return FormatUtils.formatBytes(physicalUsedBytes) + " / " + FormatUtils.formatBytes(physicalTotalBytes);
    }

    public String formattedPhysicalPercent() {
        return String.format(Locale.ROOT, "%.1f%%", physicalUsedPercent);
    }

    public String formattedSwapUsed() {
        if (swapTotalBytes <= 0) return "No Pagefile / Swap active";
        return FormatUtils.formatBytes(swapUsedBytes) + " / " + FormatUtils.formatBytes(swapTotalBytes);
    }

    public String formattedSwapPercent() {
        if (swapTotalBytes <= 0) return "0.0%";
        return String.format(Locale.ROOT, "%.1f%%", swapUsedPercent);
    }

    public String formattedPagesInOut() {
        return "Pages In: " + FormatUtils.formatNumber(swapPagesIn) + " | Pages Out: " + FormatUtils.formatNumber(swapPagesOut);
    }
}
