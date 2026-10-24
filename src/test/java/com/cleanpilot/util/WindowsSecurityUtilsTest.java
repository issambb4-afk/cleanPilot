package com.cleanpilot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsSecurityUtilsTest {

    @Test
    @DisplayName("Should check admin status without hanging or throwing exceptions")
    void testIsRunningAsAdminDoesNotThrow() {
        boolean isAdmin = WindowsSecurityUtils.isRunningAsAdmin();
        // Returns boolean without hanging or throwing
        assertThat(isAdmin).isIn(true, false);
    }
}
