package com.cleanpilot.model;

import java.util.List;

/**
 * Detailed threat assessment for an active Windows process.
 * Analyzes CPU velocity, outbound beaconing, masquerading, Authenticode signatures, and binary entropy.
 * Adheres to strict zero-telemetry and unverified claim prevention principles.
 */
public record ProcessThreatInfo(
        int pid,
        String processName,
        String executablePath,
        String commandLine,
        double cpuPercent,
        long memoryBytes,
        int outboundSocketCount,
        List<String> foreignConnections,
        int riskScore,
        ThreatLevel threatLevel,
        List<String> threatReasons,
        boolean isProtectedSystem,
        boolean isSigned,
        String signer,
        double entropy,
        boolean isSuspended,
        ThreatExplanation explanation
) {
    public ProcessThreatInfo(
            int pid,
            String processName,
            String executablePath,
            String commandLine,
            double cpuPercent,
            long memoryBytes,
            int outboundSocketCount,
            List<String> foreignConnections,
            int riskScore,
            ThreatLevel threatLevel,
            List<String> threatReasons,
            boolean isProtectedSystem,
            boolean isSigned,
            String signer,
            double entropy,
            boolean isSuspended
    ) {
        this(pid, processName, executablePath, commandLine, cpuPercent, memoryBytes,
                outboundSocketCount, foreignConnections, riskScore, threatLevel, threatReasons,
                isProtectedSystem, isSigned, signer, entropy, isSuspended, null);
    }

    public enum ThreatLevel {
        SAFE("Verified / Normal", "badge-success"),
        SUSPICIOUS("Investigate (Suspicious)", "badge-warning"),
        HIGH("High-Confidence Anomaly", "badge-danger"),
        CRITICAL("Critical Anomaly", "badge-critical");

        private final String displayName;
        private final String styleClass;

        ThreatLevel(String displayName, String styleClass) {
            this.displayName = displayName;
            this.styleClass = styleClass;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getStyleClass() {
            return styleClass;
        }
    }

    public ThreatExplanation explanation() {
        return explanation != null ? explanation : ThreatExplanation.verifiedNormal(processName);
    }

    public String formattedMemory() {
        if (memoryBytes < 1024 * 1024) {
            return (memoryBytes / 1024) + " KB";
        } else if (memoryBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", memoryBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", memoryBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public String formattedCpu() {
        return String.format("%.1f%%", cpuPercent);
    }

    public String formattedEntropy() {
        if (entropy <= 0.0) return "N/A";
        return String.format("%.2f / 8.00", entropy);
    }

    public String displayProcess() {
        return (processName != null && !processName.isBlank()) ? processName + " (PID " + pid + ")" : "PID " + pid;
    }
}
