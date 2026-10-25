package com.cleanpilot.model;

import java.time.Instant;

/**
 * Metadata and cryptographic status for a CleanPilot Lemon Squeezy license.
 */
public record LicenseDetails(
        String key,
        String instanceId,
        String customerName,
        String customerEmail,
        String productName,
        LicenseStatus status,
        Instant activatedAt,
        Instant expiresAt
) {
    public static LicenseDetails free() {
        return new LicenseDetails(
                "",
                "",
                "Community User",
                "",
                "CleanPilot Community",
                LicenseStatus.FREE,
                null,
                null
        );
    }

    public boolean isPro() {
        return status == LicenseStatus.ACTIVE_PRO;
    }

    public boolean isLifetime() {
        return expiresAt == null && isPro();
    }

    public String formattedMaskedKey() {
        if (key == null || key.isBlank()) {
            return "None";
        }
        if (key.length() <= 8) {
            return "****" + key.substring(Math.max(0, key.length() - 4));
        }
        return key.substring(0, 4) + "-****-****-" + key.substring(key.length() - 4);
    }
}
