package com.cleanpilot.model;

/**
 * Metric snapshot of Windows system boot time and startup trace.
 */
public record BootMetricInfo(
        long totalBootTimeMs,
        long mainPathBootTimeMs,
        long postBootTimeMs,
        String lastBootTimestamp,
        int degradingAppCount,
        String bootEvaluationVerdict
) {
    public String formattedTotalBootTime() {
        return String.format("%.2f s", totalBootTimeMs / 1000.0);
    }

    public String formattedMainPath() {
        return String.format("%.2f s", mainPathBootTimeMs / 1000.0);
    }
}
