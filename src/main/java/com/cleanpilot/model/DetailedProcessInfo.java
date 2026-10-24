package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Rich, authentic telemetry snapshot for deep inspection of a specific running process.
 */
public record DetailedProcessInfo(
        int pid,
        int parentPid,
        String parentName,
        String name,
        String path,
        String commandLine,
        String currentWorkingDirectory,
        String user,
        String userId,
        String group,
        String groupId,
        String state,
        int bitness,
        int priority,
        int threadCount,
        long startTimeEpochMs,
        long upTimeMs,
        long userTimeMs,
        long kernelTimeMs,
        double cpuPercent,
        long residentMemoryBytes,
        long virtualMemoryBytes,
        long bytesRead,
        long bytesWritten,
        long openFiles,
        long minorFaults,
        long majorFaults,
        List<Integer> childPids,
        boolean isCriticalSystemProcess,
        String impactLevel
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

    public String formattedDiskRead() {
        return FormatUtils.formatBytes(bytesRead);
    }

    public String formattedDiskWritten() {
        return FormatUtils.formatBytes(bytesWritten);
    }

    public String formattedTotalIo() {
        return FormatUtils.formatBytes(bytesRead + bytesWritten);
    }

    public String formattedUpTime() {
        return FormatUtils.formatDuration(upTimeMs);
    }

    public String formattedStartTime() {
        if (startTimeEpochMs <= 0) {
            return "-";
        }
        return FormatUtils.formatInstant(Instant.ofEpochMilli(startTimeEpochMs));
    }

    public String formattedUserTime() {
        return FormatUtils.formatDuration(userTimeMs);
    }

    public String formattedKernelTime() {
        return FormatUtils.formatDuration(kernelTimeMs);
    }

    public String formattedTotalCpuTime() {
        return FormatUtils.formatDuration(userTimeMs + kernelTimeMs);
    }

    public boolean isHighCpu() {
        return cpuPercent >= 15.0;
    }

    public boolean isHighMemory() {
        return residentMemoryBytes >= 1024L * 1024 * 1024;
    }

    public boolean isSystemHog() {
        return isHighCpu() || isHighMemory();
    }
}
