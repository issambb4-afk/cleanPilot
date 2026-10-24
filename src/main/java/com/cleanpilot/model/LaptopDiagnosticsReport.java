package com.cleanpilot.model;

import java.util.Collections;
import java.util.List;

/**
 * Aggregated hardware and system diagnostic snapshot combining battery, thermals,
 * physical storage disks, logical volumes, advanced memory (kernel pools),
 * system stability (uptime & crash history), and active bottleneck warnings.
 */
public record LaptopDiagnosticsReport(
        int overallHealthScore, // 0 to 100
        String scoreGrade,      // "OPTIMAL", "GOOD", "ATTENTION", "CRITICAL"
        BatteryHealthInfo battery,
        ThermalMetricsInfo thermals,
        List<StorageHealthInfo> storageDrives,
        List<StorageVolumeHealthInfo> storageVolumes,
        MemoryThrashingInfo memory,
        AdvancedMemoryHealthInfo advancedMemory,
        SystemStabilityReport stability,
        List<String> detectedIssues,
        List<String> recommendations
) {

    /**
     * Backward-compatible constructor for existing tests and callers.
     */
    public LaptopDiagnosticsReport(
            int overallHealthScore,
            String scoreGrade,
            BatteryHealthInfo battery,
            ThermalMetricsInfo thermals,
            List<StorageHealthInfo> storageDrives,
            MemoryThrashingInfo memory,
            List<String> detectedIssues,
            List<String> recommendations
    ) {
        this(
                overallHealthScore,
                scoreGrade,
                battery,
                thermals,
                storageDrives,
                Collections.emptyList(),
                memory,
                new AdvancedMemoryHealthInfo(
                        memory.physicalTotalBytes(),
                        memory.physicalUsedBytes(),
                        memory.physicalAvailableBytes(),
                        memory.physicalUsedPercent(),
                        0L,
                        0L,
                        0.0,
                        0L,
                        0L,
                        false,
                        false,
                        memory.swapTotalBytes(),
                        memory.swapUsedBytes(),
                        memory.swapUsedPercent(),
                        memory.isThrashingDetected(),
                        memory.memoryHealthStatus()
                ),
                new SystemStabilityReport(0L, "Unknown", false, Collections.emptyList(), 0, "OPTIMAL"),
                detectedIssues,
                recommendations
        );
    }

    public String formattedScore() {
        return overallHealthScore + "/100";
    }

    public boolean hasIssues() {
        return detectedIssues != null && !detectedIssues.isEmpty();
    }
}
