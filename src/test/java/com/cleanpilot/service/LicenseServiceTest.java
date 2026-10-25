package com.cleanpilot.service;

import com.cleanpilot.model.LicenseDetails;
import com.cleanpilot.model.LicenseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseServiceTest {

    private LicenseService licenseService;

    @BeforeEach
    void setUp() throws Exception {
        // Clear test preferences before each run
        Preferences prefs = Preferences.userNodeForPackage(LicenseService.class);
        prefs.clear();

        licenseService = new LicenseService(
                "https://api.lemonsqueezy.com/v1/licenses",
                "https://cleanpilot.lemonsqueezy.com/checkout/buy/cleanpilot-pro"
        );
    }

    @Test
    void defaultLicense_isCommunityFree() {
        assertThat(licenseService.isPro()).isFalse();
        LicenseDetails details = licenseService.getLicenseDetails();
        assertThat(details.status()).isEqualTo(LicenseStatus.FREE);
        assertThat(details.isPro()).isFalse();
        assertThat(details.formattedMaskedKey()).isEqualTo("None");
    }

    @Test
    void maskedKey_formatsCorrectly() {
        LicenseDetails longKey = new LicenseDetails(
                "123e4567-e89b-12d3-a456-426614174000",
                "inst-1",
                "John",
                "john@example.com",
                "CleanPilot Pro",
                LicenseStatus.ACTIVE_PRO,
                Instant.now(),
                null
        );
        assertThat(longKey.formattedMaskedKey()).isEqualTo("123e-****-****-4000");

        LicenseDetails shortKey = new LicenseDetails(
                "ABCD1234",
                "inst-1",
                "John",
                "john@example.com",
                "CleanPilot Pro",
                LicenseStatus.ACTIVE_PRO,
                Instant.now(),
                null
        );
        assertThat(shortKey.formattedMaskedKey()).isEqualTo("****1234");
    }

    @Test
    void emptyKeyActivation_failsFastWithoutNetwork() {
        var result = licenseService.activateLicense("   ").join();
        assertThat(result.success()).isFalse();
        assertThat(result.message()).containsIgnoringCase("empty");
    }

    @Test
    void deactivation_whenFree_returnsTrue() {
        boolean ok = licenseService.deactivateLicense().join();
        assertThat(ok).isTrue();
        assertThat(licenseService.isPro()).isFalse();
    }
}
