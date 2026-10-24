package com.cleanpilot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Windows security and elevation helper.
 * Safely inspects current process privileges and provides injection-safe elevated execution wrappers.
 */
public final class WindowsSecurityUtils {

    private static final Logger log = LoggerFactory.getLogger(WindowsSecurityUtils.class);

    private WindowsSecurityUtils() {}

    /**
     * Checks if current Java process is running with elevated Administrator privileges.
     */
    public static boolean isRunningAsAdmin() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)"
            );
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 5);
            return result.isSuccess() && result.stdout().trim().equalsIgnoreCase("True");
        } catch (Exception e) {
            log.warn("Could not check Windows administrator privilege: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Spawns an elevated PowerShell process with UAC prompt using injection-safe UTF-16LE Base64 encoding.
     * Defaults to 120 seconds timeout.
     */
    public static boolean runElevated(String command) {
        return runElevated(command, 120);
    }

    /**
     * Spawns an elevated PowerShell process with UAC prompt, custom timeout, and inner exit-code propagation.
     */
    public static boolean runElevated(String command, long timeoutSeconds) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return false;
        }
        try {
            byte[] utf16Bytes = command.getBytes(StandardCharsets.UTF_16LE);
            String base64Payload = Base64.getEncoder().encodeToString(utf16Bytes);

            // Capture inner process via -PassThru and exit with its return code
            String launcherScript = String.format(
                    "$proc = Start-Process powershell.exe -Verb RunAs -Wait -PassThru -ArgumentList '-NoProfile', '-NonInteractive', '-EncodedCommand', '%s'; " +
                    "if ($proc) { exit $proc.ExitCode } else { exit 1 }",
                    base64Payload
            );

            byte[] launcherBytes = launcherScript.getBytes(StandardCharsets.UTF_16LE);
            String launcherBase64 = Base64.getEncoder().encodeToString(launcherBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", launcherBase64
            );
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, timeoutSeconds);

            if (!result.isSuccess()) {
                log.warn("Elevated execution failed or was canceled by user (exitCode={}): {}",
                        result.exitCode(), result.stdout().trim());
            }
            return result.isSuccess();
        } catch (Exception e) {
            log.error("Failed to run elevated process: {}", e.getMessage(), e);
            return false;
        }
    }
}
