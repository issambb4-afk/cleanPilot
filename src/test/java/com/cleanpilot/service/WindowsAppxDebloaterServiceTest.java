package com.cleanpilot.service;

import com.cleanpilot.model.AppxPackageInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsAppxDebloaterServiceTest {

    private final WindowsAppxDebloaterService service = new WindowsAppxDebloaterService();

    @Test
    @DisplayName("Should query installed AppX packages without throwing and filter frameworks")
    void shouldScanDebloatablePackages() {
        List<AppxPackageInfo> packages = service.scanDebloatablePackages();
        assertThat(packages).isNotNull();

        // If running on Windows, verify package fields are populated
        for (AppxPackageInfo pkg : packages) {
            assertThat(pkg.name()).isNotBlank();
            assertThat(pkg.packageFullName()).isNotBlank();
            assertThat(pkg.category()).isNotBlank();
            assertThat(pkg.formattedSize()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Should reject blank package removal safely")
    void shouldHandleInvalidRemovalSafely() {
        boolean res = service.removePackage("");
        assertThat(res).isFalse();
    }

    @Test
    @DisplayName("isValidPackageFullName must validate strictly against command injection tokens")
    void testIsValidPackageFullName() {
        // Valid package full names
        assertThat(service.isValidPackageFullName("Microsoft.BingWeather_4.53.61432.0_x64__8wekyb3d8bbwe")).isTrue();
        assertThat(service.isValidPackageFullName("SpotifyAB.SpotifyMusic_1.222.982.0_x86__zpdnekdrzrea0")).isTrue();

        // Malicious shell command injection payloads
        assertThat(service.isValidPackageFullName("test\" ; Start-Process calc.exe ; \"")).isFalse();
        assertThat(service.isValidPackageFullName("pkg & dir")).isFalse();
        assertThat(service.isValidPackageFullName("pkg | powershell")).isFalse();
        assertThat(service.isValidPackageFullName("pkg`whoami`")).isFalse();
        assertThat(service.isValidPackageFullName("pkg$(whoami)")).isFalse();
        assertThat(service.isValidPackageFullName("pkg\nRemove-Item")).isFalse();
        assertThat(service.isValidPackageFullName("")).isFalse();
        assertThat(service.isValidPackageFullName(null)).isFalse();
    }

    @Test
    @DisplayName("isProtectedPackage must identify core Windows components")
    void testIsProtectedPackage() {
        assertThat(service.isProtectedPackage("Microsoft.WindowsStore")).isTrue();
        assertThat(service.isProtectedPackage("Microsoft.WindowsStore_12107.1001.15.0_x64__8wekyb3d8bbwe")).isTrue();
        assertThat(service.isProtectedPackage("Microsoft.DesktopAppInstaller")).isTrue();
        assertThat(service.isProtectedPackage("Microsoft.Windows.Search")).isTrue();
        assertThat(service.isProtectedPackage("Microsoft.WindowsCalculator")).isTrue();

        // Unprotected third-party app
        assertThat(service.isProtectedPackage("ByteDance.TikTok_1.0.0.0_x64__test123")).isFalse();
    }

    @Test
    @DisplayName("removePackage and deprovisionPackageAllUsers must refuse protected packages")
    void testRefusesRemovalOfProtectedPackage() {
        boolean remRes = service.removePackage("Microsoft.WindowsStore_12107.1001.15.0_x64__8wekyb3d8bbwe");
        assertThat(remRes).isFalse();

        boolean deprRes = service.deprovisionPackageAllUsers("Microsoft.WindowsStore_12107.1001.15.0_x64__8wekyb3d8bbwe");
        assertThat(deprRes).isFalse();
    }

    @Test
    @DisplayName("removePackage and deprovisionPackageAllUsers must refuse injection payloads")
    void testRefusesRemovalOfInjectionPayload() {
        boolean remRes = service.removePackage("Microsoft.ZuneVideo\" ; calc.exe ; \"");
        assertThat(remRes).isFalse();

        boolean deprRes = service.deprovisionPackageAllUsers("Microsoft.ZuneVideo | Remove-Item C:\\ -Recurse");
        assertThat(deprRes).isFalse();
    }
}