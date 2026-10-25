package com.cleanpilot.model;

/**
 * Status of the CleanPilot software license.
 */
public enum LicenseStatus {
    FREE("Community Edition", "Free"),
    ACTIVE_PRO("CleanPilot Pro", "Active"),
    EXPIRED("CleanPilot Pro (Expired)", "Expired"),
    INVALID("Invalid License", "Invalid");

    private final String displayName;
    private final String badgeText;

    LicenseStatus(String displayName, String badgeText) {
        this.displayName = displayName;
        this.badgeText = badgeText;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeText() {
        return badgeText;
    }
}
