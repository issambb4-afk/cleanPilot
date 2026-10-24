package com.cleanpilot.service;

import com.cleanpilot.model.HardwareDriverInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HardwareDriverServiceTest {

    private HardwareDriverService driverService;

    @BeforeEach
    void setUp() {
        driverService = new HardwareDriverService();
    }

    @Test
    @DisplayName("Should scan hardware devices and return non-empty list on current machine")
    void shouldScanHardwareAndDrivers() {
        List<HardwareDriverInfo> drivers = driverService.scanHardwareAndDrivers();
        assertThat(drivers).isNotNull();
        assertThat(drivers).isNotEmpty();

        // Every device should have a non-blank name and valid class
        for (HardwareDriverInfo d : drivers) {
            assertThat(d.name()).isNotBlank();
            assertThat(d.pnpClass()).isNotBlank();
            assertThat(d.compatibilityStatus()).isNotBlank();
            assertThat(d.problemDescription()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Should evaluate compatibility properly for healthy, disabled, and error devices")
    void shouldEvaluateCompatibility() {
        // Operational device
        String compat1 = driverService.evaluateCompatibility("OK", 0, true, true, "2024-01-01");
        assertThat(compat1).isEqualTo("Compatible & Operational");

        // Disabled device (Code 22)
        String compat2 = driverService.evaluateCompatibility("Error", 22, true, true, "2024-01-01");
        assertThat(compat2).contains("Disabled in Windows (Code 22)");

        // Missing driver (Code 28)
        String compat3 = driverService.evaluateCompatibility("Error", 28, true, true, "2024-01-01");
        assertThat(compat3).contains("Incompatible / Missing Driver (Code 28)");

        // Hardware / Driver failure (Code 43)
        String compat4 = driverService.evaluateCompatibility("Error", 43, true, true, "2024-01-01");
        assertThat(compat4).contains("Hardware / Driver Failure (Code 43)");

        // Disconnected peripheral (Code 45)
        String compat5 = driverService.evaluateCompatibility("Error", 45, false, true, "2024-01-01");
        assertThat(compat5).contains("Disconnected / Phantom (Code 45)");

        // Unsigned driver warning
        String compat6 = driverService.evaluateCompatibility("OK", 0, true, false, "2024-01-01");
        assertThat(compat6).contains("Unsigned Driver");
    }

    @Test
    @DisplayName("Should resolve human-readable problem descriptions accurately")
    void shouldResolveProblemDescriptions() {
        String desc0 = driverService.resolveProblemDescription(0, "OK", true);
        assertThat(desc0).contains("working properly");

        String desc22 = driverService.resolveProblemDescription(22, "Error", true);
        assertThat(desc22).contains("disabled by the user");

        String desc28 = driverService.resolveProblemDescription(28, "Error", true);
        assertThat(desc28).contains("drivers for this device are not installed");

        String desc43 = driverService.resolveProblemDescription(43, "Error", true);
        assertThat(desc43).contains("stopped this device because it has reported problems");
    }

    @Test
    @DisplayName("Should convert WMI /Date(millis)/ timestamps to standard ISO dates")
    void shouldParseDateString() {
        String parsed = driverService.parseDateString("/Date(1781654400000)/");
        assertThat(parsed).isEqualTo("2026-06-17");

        String plain = driverService.parseDateString("2024-05-10T12:00:00");
        assertThat(plain).isEqualTo("2024-05-10");

        String invalid = driverService.parseDateString("");
        assertThat(invalid).isEqualTo("N/A");
    }

    @Test
    @DisplayName("Should correctly classify outdated drivers (>5 years old)")
    void shouldDetectOutdatedDrivers() {
        HardwareDriverInfo legacy = new HardwareDriverInfo(
                "Legacy Device", "DEV_01", "OK", 0, "Vendor", "svc", true,
                "Display", "Vendor", "1.0", "2015-01-01", true, "Compatible", "Healthy",
                "oem1.inf", "Microsoft", "PCI\\VEN_1"
        );
        assertThat(legacy.isOutdated()).isTrue();
        assertThat(legacy.displayInfName()).isEqualTo("oem1.inf");
        assertThat(legacy.displaySigner()).isEqualTo("Microsoft");
        assertThat(legacy.displayHardwareId()).isEqualTo("PCI\\VEN_1");

        HardwareDriverInfo modern = new HardwareDriverInfo(
                "Modern Device", "DEV_02", "OK", 0, "Vendor", "svc", true,
                "Display", "Vendor", "2.0", "2026-01-01", true, "Compatible", "Healthy",
                "", "", ""
        );
        assertThat(modern.isOutdated()).isFalse();
        assertThat(modern.displayInfName()).isEqualTo("N/A");
        assertThat(modern.displaySigner()).isEqualTo("WHQL / Verified Signer");
    }
}
