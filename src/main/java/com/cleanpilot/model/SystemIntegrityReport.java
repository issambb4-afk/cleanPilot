package com.cleanpilot.model;

/**
 * Health assessment of Windows system files and component integrity.
 */
public record SystemIntegrityReport(
        boolean healthy,
        boolean corruptionDetected,
        boolean repairPossible,
        String statusMessage,
        String cbsLogSummary,
        String scanTimestamp
) {}
