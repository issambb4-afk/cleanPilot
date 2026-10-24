package com.cleanpilot.service;

import com.cleanpilot.CleanPilotApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Service to manage Windows File Explorer right-click context menu integration
 * under HKCU (no administrator elevation required).
 */
@Service
public class WindowsShellIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(WindowsShellIntegrationService.class);

    private static final String DIR_SHELL_KEY = "HKCU\\Software\\Classes\\Directory\\shell\\CleanPilot";
    private static final String DIR_BG_SHELL_KEY = "HKCU\\Software\\Classes\\Directory\\Background\\shell\\CleanPilot";
    private static final String DUPES_SHELL_KEY = "HKCU\\Software\\Classes\\Directory\\shell\\CleanPilotDuplicates";

    /**
     * Checks if the Explorer right-click context menu is currently registered.
     */
    public boolean isContextMenuEnabled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg.exe", "query", DIR_SHELL_KEY);
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception ex) {
            log.debug("Error checking context menu registration: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Registers CleanPilot context menu items in Windows Explorer.
     */
    public boolean enableContextMenu() {
        String baseCommand = resolveBaseCommand();
        if (baseCommand == null) {
            log.warn("Could not determine CleanPilot launcher executable path for shell integration.");
            return false;
        }

        try {
            // 1. Right-click on Folder -> "Analyze in CleanPilot"
            runRegAdd(DIR_SHELL_KEY, "", "Analyze in CleanPilot");
            runRegAdd(DIR_SHELL_KEY + "\\command", "", baseCommand + " --analyze=\"%1\"");

            // 2. Right-click inside Folder background -> "Analyze in CleanPilot"
            runRegAdd(DIR_BG_SHELL_KEY, "", "Analyze in CleanPilot");
            runRegAdd(DIR_BG_SHELL_KEY + "\\command", "", baseCommand + " --analyze=\"%V\"");

            // 3. Right-click on Folder -> "Find Duplicates in CleanPilot"
            runRegAdd(DUPES_SHELL_KEY, "", "Find Duplicates in CleanPilot");
            runRegAdd(DUPES_SHELL_KEY + "\\command", "", baseCommand + " --duplicates=\"%1\"");

            log.info("Windows Explorer context menu registered successfully.");
            return true;
        } catch (Exception ex) {
            log.error("Failed to register Windows Explorer context menu: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Removes CleanPilot context menu entries from Windows Explorer.
     */
    public boolean disableContextMenu() {
        try {
            runRegDelete(DIR_SHELL_KEY);
            runRegDelete(DIR_BG_SHELL_KEY);
            runRegDelete(DUPES_SHELL_KEY);
            log.info("Windows Explorer context menu unregistered successfully.");
            return true;
        } catch (Exception ex) {
            log.error("Failed to unregister Windows Explorer context menu: {}", ex.getMessage(), ex);
            return false;
        }
    }

    private void runRegAdd(String key, String valueName, String data) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "reg.exe", "add", key,
                "/ve", // set (Default) value
                "/d", data,
                "/f"   // force overwrite
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
    }

    private void runRegDelete(String key) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg.exe", "delete", key, "/f");
            Process p = pb.start();
            p.waitFor();
        } catch (Exception ignored) {}
    }

    private String resolveBaseCommand() {
        try {
            String exeCommand = ProcessHandle.current().info().command().orElse(null);
            if (exeCommand != null && exeCommand.toLowerCase().endsWith("cleanpilot.exe")) {
                return "\"" + exeCommand + "\"";
            }

            // Java fallback (run via javaw.exe with JAR)
            File jarFile = new File(CleanPilotApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jarFile.isFile() && jarFile.getName().endsWith(".jar")) {
                String javaw = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe";
                return "\"" + javaw + "\" -jar \"" + jarFile.getAbsolutePath() + "\"";
            }

            if (exeCommand != null) {
                return "\"" + exeCommand + "\"";
            }
        } catch (Exception ex) {
            log.debug("Could not resolve jar/exe path: {}", ex.getMessage());
        }
        return null;
    }
}
