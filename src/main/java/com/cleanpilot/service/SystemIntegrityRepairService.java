package com.cleanpilot.service;

import com.cleanpilot.model.SystemIntegrityReport;
import com.cleanpilot.util.ProcessExecutionUtils;
import com.cleanpilot.util.WindowsSecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Orchestrates Windows system file integrity scans (SFC) and DISM health verification.
 */
@Service
public class SystemIntegrityRepairService {

    private static final Logger log = LoggerFactory.getLogger(SystemIntegrityRepairService.class);

    /**
     * Non-destructive scan checking if Windows component store or system files are corrupt.
     */
    public SystemIntegrityReport scanSystemHealth(Consumer<String> progressListener) {
        if (progressListener != null) progressListener.accept("Checking Windows system integrity status...");

        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWin) {
            return new SystemIntegrityReport(
                    true, false, false,
                    "System integrity check is exclusive to Windows systems.",
                    "", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
        }

        // DISM /CheckHealth runs fast and verifies corruption markers
        boolean corruptionFound = false;
        boolean repairable = true;
        StringBuilder logSummary = new StringBuilder();

        try {
            ProcessBuilder pb = new ProcessBuilder("dism", "/Online", "/Cleanup-Image", "/CheckHealth");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), ProcessExecutionUtils.getNativeCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logSummary.append(line).append("\n");
                    String lower = line.toLowerCase();
                    if (lower.contains("corruption has been detected") || lower.contains("altération") || lower.contains("repaired")) {
                        corruptionFound = true;
                    }
                    if (lower.contains("the component store is not repairable") || lower.contains("non réparable")) {
                        repairable = false;
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            log.warn("CheckHealth invocation error: {}", e.getMessage());
        }

        // Also check CBS log recent entries if available
        String cbsSample = sampleCbsLog();

        String statusMsg = corruptionFound
                ? "Component store corruption detected. System file repair recommended."
                : "No component store corruption detected. System files appear healthy.";

        return new SystemIntegrityReport(
                !corruptionFound,
                corruptionFound,
                repairable,
                statusMsg,
                cbsSample,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }

    /**
     * Executes automatic repair via DISM /RestoreHealth followed by sfc /scannow.
     */
    public boolean repairSystemHealth(Consumer<String> progressListener) {
        if (progressListener != null) progressListener.accept("Initiating Windows component repair...");

        boolean isAdmin = WindowsSecurityUtils.isRunningAsAdmin();
        if (isAdmin) {
            try {
                // Step 1: DISM RestoreHealth
                if (progressListener != null) progressListener.accept("Step 1/2: DISM /Online /Cleanup-Image /RestoreHealth...");
                ProcessBuilder dismPb = new ProcessBuilder("dism", "/Online", "/Cleanup-Image", "/RestoreHealth");
                dismPb.redirectErrorStream(true);
                Process dismProc = dismPb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(dismProc.getInputStream(), ProcessExecutionUtils.getNativeCharset()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (progressListener != null && line.contains("%")) {
                            progressListener.accept("DISM Restore: " + line.trim());
                        }
                    }
                }
                dismProc.waitFor();

                // Step 2: SFC scannow
                if (progressListener != null) progressListener.accept("Step 2/2: Verifying protected system files (sfc /scannow)...");
                ProcessBuilder sfcPb = new ProcessBuilder("sfc", "/scannow");
                sfcPb.redirectErrorStream(true);
                Process sfcProc = sfcPb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(sfcProc.getInputStream(), ProcessExecutionUtils.getNativeCharset()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (progressListener != null && line.contains("%")) {
                            progressListener.accept("SFC: " + line.trim());
                        }
                    }
                }
                int code = sfcProc.waitFor();
                return code == 0;
            } catch (Exception e) {
                log.error("Failed to execute system integrity repair: {}", e.getMessage(), e);
                return false;
            }
        } else {
            // Request UAC elevation with extended 30-minute timeout for DISM and SFC operations
            if (progressListener != null) progressListener.accept("Requesting Administrator elevation for SFC/DISM repair...");
            return WindowsSecurityUtils.runElevated("dism /Online /Cleanup-Image /RestoreHealth ; sfc /scannow", 1800);
        }
    }

    private String sampleCbsLog() {
        File cbsFile = new File("C:\\Windows\\Logs\\CBS\\CBS.log");
        if (!cbsFile.exists()) return "CBS log not accessible.";
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(cbsFile.toPath());
            int start = Math.max(0, lines.size() - 25);
            return String.join("\n", lines.subList(start, lines.size()));
        } catch (Exception e) {
            return "Could not read CBS log: " + e.getMessage();
        }
    }
}
