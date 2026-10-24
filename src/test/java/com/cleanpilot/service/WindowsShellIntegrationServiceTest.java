package com.cleanpilot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsShellIntegrationServiceTest {

    private final WindowsShellIntegrationService service = new WindowsShellIntegrationService();

    @Test
    void testIsContextMenuEnabledDoesNotThrow() {
        boolean enabled = service.isContextMenuEnabled();
        // Just verify it queries registry safely without throwing exceptions
        assertThat(enabled).isIn(true, false);
    }
}
