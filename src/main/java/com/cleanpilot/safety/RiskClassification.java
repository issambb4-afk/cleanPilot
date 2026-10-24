package com.cleanpilot.safety;

/**
 * Central risk model classifying the severity, elevation requirements,
 * confirmation policy, and reversibility of maintenance operations.
 */
public enum RiskClassification {

    READ_ONLY(
            "Read Only",
            "badge-subtle",
            false,
            false,
            false,
            "Non-invasive inspection and diagnostics with zero filesystem or process side-effects."
    ),

    LOW_RISK(
            "Low Risk",
            "badge-success",
            false,
            false,
            false,
            "Safe cleaning of verified temporary directories and regeneratable system caches."
    ),

    MODERATE_RISK(
            "Moderate Risk",
            "badge-warning",
            true,
            false,
            false,
            "Targeted developer artifact removal, startup app toggles, and quarantine moves."
    ),

    HIGH_RISK(
            "High Risk",
            "badge-danger",
            true,
            true,
            false,
            "Process termination, thread suspension, network winsock reset, and system repairs."
    ),

    IRREVERSIBLE(
            "Irreversible",
            "badge-danger",
            true,
            false,
            true,
            "Permanent unrecoverable file deletion or irreversible DISM component base reset."
    );

    private final String displayLabel;
    private final String badgeStyleClass;
    private final boolean confirmationRequired;
    private final boolean requiresElevation;
    private final boolean irreversible;
    private final String description;

    RiskClassification(
            String displayLabel,
            String badgeStyleClass,
            boolean confirmationRequired,
            boolean requiresElevation,
            boolean irreversible,
            String description
    ) {
        this.displayLabel = displayLabel;
        this.badgeStyleClass = badgeStyleClass;
        this.confirmationRequired = confirmationRequired;
        this.requiresElevation = requiresElevation;
        this.irreversible = irreversible;
        this.description = description;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public String getBadgeStyleClass() {
        return badgeStyleClass;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public boolean isRequiresElevation() {
        return requiresElevation;
    }

    public boolean isIrreversible() {
        return irreversible;
    }

    public String getDescription() {
        return description;
    }
}