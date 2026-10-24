package com.cleanpilot.model;

import java.util.Collections;
import java.util.List;

/**
 * Plain-language explanation of why a process was flagged by Threat Hunter,
 * explaining observed indicators, anomaly rationale, potential legitimate false-positive
 * contexts, and suggested investigation actions.
 */
public record ThreatExplanation(
        List<String> observedIndicators,
        String anomalyRationale,
        List<String> potentialLegitimateUses,
        List<String> recommendedActions
) {
    public ThreatExplanation {
        observedIndicators = observedIndicators != null ? List.copyOf(observedIndicators) : Collections.emptyList();
        potentialLegitimateUses = potentialLegitimateUses != null ? List.copyOf(potentialLegitimateUses) : Collections.emptyList();
        recommendedActions = recommendedActions != null ? List.copyOf(recommendedActions) : Collections.emptyList();
        if (anomalyRationale == null || anomalyRationale.isBlank()) {
            anomalyRationale = "No specific anomaly observed.";
        }
    }

    public static ThreatExplanation verifiedNormal(String processName) {
        return new ThreatExplanation(
                List.of("Authentic operating system or verified application binary", "Normal resource consumption"),
                "Process '" + processName + "' operates within expected directory bounds and system parameters.",
                List.of("Legitimate Windows operating system service, driver host, or recognized software"),
                List.of("No action required. Process is operating normally.")
        );
    }

    public static ThreatExplanation developerTool(String toolName, String path) {
        return new ThreatExplanation(
                List.of("Recognized developer / build execution tool (" + toolName + ")", "Local workspace activity"),
                "Tool is performing active local compilation, package indexing, or runtime execution.",
                List.of("Standard developer workflow: compilation, test suite run, bundler, or language server"),
                List.of("Safe to continue. If unexpected, verify the workspace project being built.")
        );
    }
}
