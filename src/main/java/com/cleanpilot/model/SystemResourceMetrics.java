package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

/**
 * Encapsulates real-time hardware telemetry: CPU load and RAM usage.
 */
public record SystemResourceMetrics(
        double cpuLoadPercentage,
        long usedMemoryBytes,
        long totalMemoryBytes
) {

    public double memoryUsagePercentage() {
        if (totalMemoryBytes <= 0) {
            return 0.0;
        }
        return (double) usedMemoryBytes / (double) totalMemoryBytes;
    }

    public String formattedCpu() {
        return FormatUtils.formatPercentage(cpuLoadPercentage);
    }

    public String formattedMemoryUsed() {
        return FormatUtils.formatBytes(usedMemoryBytes);
    }

    public String formattedMemoryTotal() {
        return FormatUtils.formatBytes(totalMemoryBytes);
    }

    public String formattedMemoryPercentage() {
        return FormatUtils.formatPercentage(memoryUsagePercentage());
    }
}
