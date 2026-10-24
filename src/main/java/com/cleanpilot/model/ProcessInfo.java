package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

import java.util.Locale;

/**
 * Encapsulates authentic process telemetry: CPU load, physical RAM (RSS), disk I/O, and execution state.
 */
public record ProcessInfo(
        int pid,
        String name,
        double cpuPercent,
        long residentMemoryBytes,
        long virtualMemoryBytes,
        long bytesRead,
        long bytesWritten,
        int threadCount,
        String state,
        String user,
        String commandLine
) {

    public String formattedCpu() {
        return String.format(Locale.ROOT, "%.1f%%", Math.max(0.0, cpuPercent));
    }

    public String formattedRam() {
        return FormatUtils.formatBytes(residentMemoryBytes);
    }

    public String formattedVirtualRam() {
        return FormatUtils.formatBytes(virtualMemoryBytes);
    }

    public String formattedDiskIo() {
        long totalIo = bytesRead + bytesWritten;
        return FormatUtils.formatBytes(totalIo);
    }

    public long memoryBytes() {
        return residentMemoryBytes;
    }

    public long totalIoBytes() {
        return bytesRead + bytesWritten;
    }

    public boolean isHighCpu() {
        return cpuPercent >= 15.0;
    }

    public boolean isHighMemory() {
        return residentMemoryBytes >= 1024L * 1024 * 1024; // >= 1 GB
    }

    public boolean isSystemHog() {
        return isHighCpu() || isHighMemory();
    }

    public String getImpactLevel() {
        if (cpuPercent >= 40.0 && isHighMemory()) {
            return "Critical Blocker";
        }
        if (cpuPercent >= 25.0) {
            return "High CPU Load";
        }
        if (isHighMemory()) {
            return "Heavy Memory";
        }
        if (cpuPercent >= 10.0) {
            return "Moderate Load";
        }
        return "Normal";
    }
}
