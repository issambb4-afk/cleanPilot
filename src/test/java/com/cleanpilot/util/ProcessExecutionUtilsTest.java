package com.cleanpilot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessExecutionUtilsTest {

    @Test
    @DisplayName("Should successfully execute process and drain stdout without deadlocking")
    void testExecuteProcessSuccessfully() {
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "echo", "CleanPilotProcessExecutionTest");
        ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 5);

        assertThat(result.timedOut()).isFalse();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdout()).contains("CleanPilotProcessExecutionTest");
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should forcibly terminate process when timeout is exceeded")
    void testProcessTimeoutEnforcement() {
        // Spawns a process that sleeps longer than the 1-second timeout
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "Start-Sleep -Seconds 10");
        long start = System.currentTimeMillis();
        ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 1);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.timedOut()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        // Should terminate promptly around ~1-3s, not wait 10s
        assertThat(elapsed).isLessThan(5000);
    }

    @Test
    @DisplayName("Should detect valid native console charset")
    void testGetNativeCharset() {
        Charset charset = ProcessExecutionUtils.getNativeCharset();
        assertThat(charset).isNotNull();
    }
}
