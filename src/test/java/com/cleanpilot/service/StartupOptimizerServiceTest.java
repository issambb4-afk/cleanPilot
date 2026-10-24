package com.cleanpilot.service;

import com.cleanpilot.model.StartupAppInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupOptimizerServiceTest {

    private StartupOptimizerService startupService;

    @BeforeEach
    void setUp() {
        startupService = new StartupOptimizerService();
    }

    @Test
    @DisplayName("Should scan startup programs and return non-null list")
    void testScanStartupApps() {
        List<StartupAppInfo> apps = startupService.scanStartupApps();
        assertThat(apps).isNotNull();

        for (StartupAppInfo app : apps) {
            assertThat(app.getName()).isNotBlank();
            assertThat(app.getLocation()).isNotBlank();
            assertThat(app.getImpact()).isIn("HIGH", "MEDIUM", "LOW");
        }
    }

    @Test
    @DisplayName("StartupAppInfo model getters and property binding")
    void testStartupAppInfoModel() {
        StartupAppInfo app = new StartupAppInfo(
                "Discord",
                "C:\\Users\\User\\AppData\\Local\\Discord\\Update.exe",
                "HKCU\\Run",
                "Discord Inc.",
                "HIGH",
                true
        );

        assertThat(app.getName()).isEqualTo("Discord");
        assertThat(app.getImpact()).isEqualTo("HIGH");
        assertThat(app.isEnabled()).isTrue();

        app.setEnabled(false);
        assertThat(app.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should refuse to disable protected core Windows shell context menu handlers")
    void testProtectedContextMenuSafety() {
        StartupAppInfo openWithHandler = new StartupAppInfo(
                "Open With",
                "HKEY_CLASSES_ROOT\\*\\shellex\\ContextMenuHandlers\\Open With",
                "Context Menu (Files)",
                "Microsoft Corporation",
                "LOW",
                true
        );

        boolean result = startupService.setStartupEnabled(openWithHandler, false);
        // Safety invariant: Protected handlers must never be disabled
        assertThat(result).isFalse();
        assertThat(openWithHandler.isEnabled()).isTrue();
    }
}
