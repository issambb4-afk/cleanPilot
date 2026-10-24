package com.cleanpilot.model;

import java.util.List;

/**
 * Operating system stability metrics: uptime, Windows Fast-Startup kernel bloat detection,
 * and recent crash history (BSODs, dirty shutdowns, disk hardware errors).
 */
public record SystemStabilityReport(
        long uptimeSeconds,
        String formattedUptime,
        boolean isUptimeExcessive,
        List<SystemStabilityEvent> recentCrashEvents,
        int totalCrashesLast14Days,
        String stabilityGrade
) {
    public boolean hasRecentCrashes() {
        return totalCrashesLast14Days > 0;
    }
}
