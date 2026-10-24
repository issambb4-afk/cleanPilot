package com.cleanpilot.service;

import com.cleanpilot.model.StartupAppInfo;
import com.cleanpilot.util.ProcessExecutionUtils;
import com.cleanpilot.util.WindowsSecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Discovers and manages Windows startup applications configured via Registry Run keys
 * and Windows Startup Folders. Provides safe, non-destructive enable/disable toggling.
 */
@Service
public class StartupOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(StartupOptimizerService.class);

    private static final String RUN_KEY_HKCU = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String RUN_KEY_HKLM = "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String BACKUP_DISABLED_KEY = "HKCU\\Software\\CleanPilot\\DisabledStartup";
    private static final String BACKUP_DISABLED_HKLM_KEY = "HKLM\\Software\\CleanPilot\\DisabledStartup";
    private static final String CONTEXT_MENU_HKCR_ALL = "HKCR\\*\\shellex\\ContextMenuHandlers";
    private static final String CONTEXT_MENU_HKCR_DIR = "HKCR\\Directory\\shellex\\ContextMenuHandlers";
    private static final String BACKUP_CONTEXT_KEY = "HKCU\\Software\\CleanPilot\\DisabledContextMenu";

    // Set of protected Windows core context menu handlers that must NEVER be disabled
    private static final Set<String> PROTECTED_CONTEXT_HANDLERS = Set.of(
            "sharing", "open with", "open with encryptionmenu", "encryptionmenu",
            "workfolders", "offline files", "epp", "filesyncex"
    );

    private final boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /**
     * Scans and returns all startup programs across Registry, Startup Folders,
     * Context Menu Handlers, and Scheduled Tasks.
     */
    public List<StartupAppInfo> scanStartupApps() {
        List<StartupAppInfo> list = new ArrayList<>();
        if (!isWindows) {
            return list;
        }

        // 1. Scan Active HKCU Run Key
        scanRegistryKey(RUN_KEY_HKCU, true, list);

        // 2. Scan Disabled CleanPilot Backup Keys (HKCU and HKLM)
        scanRegistryKey(BACKUP_DISABLED_KEY, false, list);
        scanRegistryKey(BACKUP_DISABLED_HKLM_KEY, false, list);

        // 3. Scan Active HKLM Run Key
        scanRegistryKey(RUN_KEY_HKLM, true, list);

        // 4. Scan User Startup Folder
        scanStartupFolder(list);

        // 5. Scan Explorer Context Menu Handlers
        scanContextMenuHandlers(list);

        // 6. Scan Startup Scheduled Tasks
        scanScheduledTasks(list);

        return list;
    }

    private void scanRegistryKey(String keyPath, boolean isEnabled, List<StartupAppInfo> list) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", keyPath);
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 10);

            if (result.isSuccess()) {
                for (String line : result.getOutput().lines().toList()) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("HKEY_")) continue;

                    // Typical format: ValueName    REG_SZ    Command
                    String[] tokens = trimmed.split("\\s{4,}|\\t+");
                    if (tokens.length >= 2) {
                        String name = tokens[0].trim();
                        String command = tokens.length >= 3 ? tokens[2].trim() : tokens[1].trim();
                        String impact = calculateImpact(name, command);
                        String publisher = resolvePublisher(name, command);

                        list.add(new StartupAppInfo(name, command, isEnabled ? keyPath : "Disabled (CleanPilot Vault)", publisher, impact, isEnabled));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error querying registry key {}: {}", keyPath, e.getMessage());
        }
    }

    private void scanStartupFolder(List<StartupAppInfo> list) {
        String appData = System.getenv("APPDATA");
        if (appData == null) return;

        Path startupPath = Paths.get(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
        if (Files.exists(startupPath) && Files.isDirectory(startupPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(startupPath)) {
                for (Path p : stream) {
                    String fName = p.getFileName().toString();
                    if (fName.endsWith(".lnk")) {
                        String name = fName.substring(0, fName.length() - 4);
                        list.add(new StartupAppInfo(name, p.toString(), "Startup Folder", "Desktop Application", calculateImpact(name, p.toString()), true));
                    } else if (fName.endsWith(".lnk.disabled")) {
                        String name = fName.substring(0, fName.length() - 13);
                        list.add(new StartupAppInfo(name, p.toString(), "Startup Folder (Disabled)", "Desktop Application", calculateImpact(name, p.toString()), false));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void scanContextMenuHandlers(List<StartupAppInfo> list) {
        scanContextMenuKey(CONTEXT_MENU_HKCR_ALL, "Context Menu (Files)", list);
        scanContextMenuKey(CONTEXT_MENU_HKCR_DIR, "Context Menu (Folders)", list);
        scanDisabledContextMenuBackup(list);
    }

    private void scanContextMenuKey(String regKey, String locationLabel, List<StartupAppInfo> list) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", regKey);
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 10);
            if (result.isSuccess()) {
                for (String line : result.getOutput().lines().toList()) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || !trimmed.startsWith("HKEY_")) continue;
                    int lastSlash = trimmed.lastIndexOf('\\');
                    if (lastSlash > 0 && lastSlash < trimmed.length() - 1) {
                        String handlerName = trimmed.substring(lastSlash + 1).trim();
                        if (isProtectedContextHandler(handlerName)) {
                            continue;
                        }
                        String publisher = resolvePublisher(handlerName, handlerName);
                        String impact = calculateImpact(handlerName, locationLabel);
                        list.add(new StartupAppInfo(
                                handlerName,
                                trimmed,
                                locationLabel,
                                publisher,
                                impact,
                                true
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error querying context menu handlers {}: {}", regKey, e.getMessage());
        }
    }

    private void scanDisabledContextMenuBackup(List<StartupAppInfo> list) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", BACKUP_CONTEXT_KEY);
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 10);
            if (result.isSuccess()) {
                for (String line : result.getOutput().lines().toList()) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || !trimmed.startsWith("HKEY_")) continue;
                    int lastSlash = trimmed.lastIndexOf('\\');
                    if (lastSlash > 0 && lastSlash < trimmed.length() - 1) {
                        String handlerName = trimmed.substring(lastSlash + 1).trim();
                        list.add(new StartupAppInfo(
                                handlerName,
                                trimmed,
                                "Context Menu (Disabled in Vault)",
                                resolvePublisher(handlerName, handlerName),
                                "LOW",
                                false
                        ));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean isProtectedContextHandler(String name) {
        if (name == null) return true;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return PROTECTED_CONTEXT_HANDLERS.contains(lower) || lower.startsWith("{");
    }

    private void scanScheduledTasks(List<StartupAppInfo> list) {
        try {
            ProcessBuilder pb = new ProcessBuilder("schtasks", "/query", "/fo", "csv", "/nh");
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 10);
            if (result.isSuccess()) {
                for (String line : result.getOutput().lines().toList()) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || !trimmed.startsWith("\"\\")) continue;
                    String[] parts = trimmed.split("\",\"");
                    if (parts.length >= 3) {
                        String taskPath = parts[0].replace("\"", "").trim();
                        String status = parts[2].replace("\"", "").trim();

                        // Skip Microsoft Windows core internal operating system tasks
                        if (taskPath.toLowerCase(Locale.ROOT).startsWith("\\microsoft\\windows\\")) {
                            continue;
                        }

                        int lastSlash = taskPath.lastIndexOf('\\');
                        String taskName = lastSlash >= 0 ? taskPath.substring(lastSlash + 1) : taskPath;
                        if (taskName.isBlank()) continue;

                        boolean isEnabled = !status.toLowerCase(Locale.ROOT).contains("désactivé")
                                && !status.toLowerCase(Locale.ROOT).contains("disabled");

                        String publisher = resolvePublisher(taskName, taskPath);
                        String impact = calculateImpact(taskName, taskPath);

                        list.add(new StartupAppInfo(
                                taskName,
                                taskPath,
                                "Scheduled Task",
                                publisher,
                                impact,
                                isEnabled
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error querying scheduled tasks: {}", e.getMessage());
        }
    }

    /**
     * Toggles the enabled/disabled state of a startup app.
     */
    public boolean setStartupEnabled(StartupAppInfo app, boolean enable) {
        if (!isWindows || app == null) return false;

        try {
            if (app.getLocation().contains("Scheduled Task")) {
                String action = enable ? "/enable" : "/disable";
                ProcessBuilder pb = new ProcessBuilder("schtasks", "/change", "/tn", app.getCommand(), action);
                pb.redirectErrorStream(true);
                ProcessExecutionUtils.ProcessResult res = ProcessExecutionUtils.execute(pb, 10);
                if (res.isSuccess()) {
                    app.setEnabled(enable);
                    return true;
                } else {
                    log.warn("schtasks command failed with code {}: {}", res.getExitCode(), res.getOutput());
                    return false;
                }
            } else if (app.getLocation().contains("Context Menu")) {
                if (isProtectedContextHandler(app.getName())) {
                    log.warn("SAFETY REJECTION: Refusing to disable protected Windows shell handler: {}", app.getName());
                    return false;
                }
                if (enable) {
                    String origKey = app.getLocation().contains("Folders") ? CONTEXT_MENU_HKCR_DIR : CONTEXT_MENU_HKCR_ALL;
                    executeReg("reg", "copy", BACKUP_CONTEXT_KEY + "\\" + app.getName(), origKey + "\\" + app.getName(), "/s", "/f");
                    executeReg("reg", "delete", BACKUP_CONTEXT_KEY + "\\" + app.getName(), "/f");
                    app.setEnabled(true);
                    return true;
                } else {
                    executeReg("reg", "copy", app.getCommand(), BACKUP_CONTEXT_KEY + "\\" + app.getName(), "/s", "/f");
                    executeReg("reg", "delete", app.getCommand(), "/f");
                    app.setEnabled(false);
                    return true;
                }
            } else if (app.getLocation().contains("Startup Folder")) {
                // Rename shortcut file
                String appData = System.getenv("APPDATA");
                Path startupPath = Paths.get(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
                if (enable) {
                    Path disabledFile = startupPath.resolve(app.getName() + ".lnk.disabled");
                    Path activeFile = startupPath.resolve(app.getName() + ".lnk");
                    if (Files.exists(disabledFile)) {
                        Files.move(disabledFile, activeFile, StandardCopyOption.REPLACE_EXISTING);
                        app.setEnabled(true);
                        return true;
                    }
                } else {
                    Path activeFile = startupPath.resolve(app.getName() + ".lnk");
                    Path disabledFile = startupPath.resolve(app.getName() + ".lnk.disabled");
                    if (Files.exists(activeFile)) {
                        Files.move(activeFile, disabledFile, StandardCopyOption.REPLACE_EXISTING);
                        app.setEnabled(false);
                        return true;
                    }
                }
            } else {
                // Registry toggle
                boolean isHklm = (app.getLocation() != null && app.getLocation().toUpperCase(Locale.ROOT).contains("HKLM"))
                        || (app.getCommand() != null && app.getCommand().toUpperCase(Locale.ROOT).contains("HKLM"));
                if (isHklm) {
                    if (enable) {
                        String cmd = String.format("reg copy \"%s\" \"%s\" /v \"%s\" /f ; reg delete \"%s\" /v \"%s\" /f",
                                BACKUP_DISABLED_HKLM_KEY, RUN_KEY_HKLM, app.getName(), BACKUP_DISABLED_HKLM_KEY, app.getName());
                        boolean ok = WindowsSecurityUtils.runElevated(cmd);
                        if (ok) {
                            app.setEnabled(true);
                            return true;
                        }
                    } else {
                        String cmd = String.format("reg add \"%s\" /v \"%s\" /d \"%s\" /f ; reg delete \"%s\" /v \"%s\" /f",
                                BACKUP_DISABLED_HKLM_KEY, app.getName(), app.getCommand(), RUN_KEY_HKLM, app.getName());
                        boolean ok = WindowsSecurityUtils.runElevated(cmd);
                        if (ok) {
                            app.setEnabled(false);
                            return true;
                        }
                    }
                } else {
                    if (enable) {
                        // Restore from CleanPilot backup to HKCU\Run
                        executeReg("reg", "copy", BACKUP_DISABLED_KEY, RUN_KEY_HKCU, "/v", app.getName(), "/f");
                        executeReg("reg", "delete", BACKUP_DISABLED_KEY, "/v", app.getName(), "/f");
                        app.setEnabled(true);
                        return true;
                    } else {
                        // Backup to CleanPilot backup key and delete from HKCU\Run
                        executeReg("reg", "add", BACKUP_DISABLED_KEY, "/v", app.getName(), "/d", app.getCommand(), "/f");
                        executeReg("reg", "delete", RUN_KEY_HKCU, "/v", app.getName(), "/f");
                        app.setEnabled(false);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to toggle startup app {}: {}", app.getName(), e.getMessage(), e);
        }
        return false;
    }

    private void executeReg(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 10);
        if (!result.isSuccess()) {
            log.warn("Registry command failed with exit code {}: {}", result.getExitCode(), result.getOutput());
        }
    }

    private String calculateImpact(String name, String command) {
        String lower = (name + " " + command).toLowerCase(Locale.ROOT);
        if (lower.contains("teams") || lower.contains("discord") || lower.contains("spotify")
                || lower.contains("slack") || lower.contains("steam") || lower.contains("onedrive")) {
            return "HIGH";
        }
        if (lower.contains("chrome") || lower.contains("edge") || lower.contains("update")
                || lower.contains("helper") || lower.contains("service")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String resolvePublisher(String name, String command) {
        String lower = (name + " " + command).toLowerCase(Locale.ROOT);
        if (lower.contains("microsoft") || lower.contains("onedrive") || lower.contains("teams") || lower.contains("edge")) {
            return "Microsoft Corporation";
        }
        if (lower.contains("google") || lower.contains("chrome")) {
            return "Google LLC";
        }
        if (lower.contains("discord")) {
            return "Discord Inc.";
        }
        if (lower.contains("spotify")) {
            return "Spotify AB";
        }
        if (lower.contains("hp") || lower.contains("hpseu")) {
            return "HP Inc.";
        }
        return "Third-Party Developer";
    }
}
