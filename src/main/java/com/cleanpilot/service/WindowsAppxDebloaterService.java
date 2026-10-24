package com.cleanpilot.service;

import com.cleanpilot.model.AppxPackageInfo;
import com.cleanpilot.util.ProcessExecutionUtils;
import com.cleanpilot.util.WindowsSecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Discovers and safely uninstalls Universal Windows Platform (UWP / AppX) bloatware,
 * pre-installed promotional packages, and Xbox/Bing telemetry components.
 */
@Service
public class WindowsAppxDebloaterService {

    private static final Logger log = LoggerFactory.getLogger(WindowsAppxDebloaterService.class);

    private static final java.util.regex.Pattern PACKAGE_FULL_NAME_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._-]+$");

    // Critical packages that must NEVER be removed
    private static final Set<String> PROTECTED_PACKAGES = Set.of(
            "Microsoft.WindowsStore",
            "Microsoft.DesktopAppInstaller",
            "Microsoft.MicrosoftEdge",
            "Microsoft.Windows.Search",
            "Microsoft.Windows.ShellExperienceHost",
            "Microsoft.WindowsTerminal",
            "Microsoft.WindowsCamera",
            "Microsoft.WindowsCalculator"
    );

    public boolean isProtectedPackage(String packageFullName) {
        if (packageFullName == null || packageFullName.isBlank()) return true;
        for (String protectedName : PROTECTED_PACKAGES) {
            if (packageFullName.equalsIgnoreCase(protectedName)
                    || packageFullName.toLowerCase(Locale.ROOT).startsWith(protectedName.toLowerCase(Locale.ROOT) + "_")
                    || packageFullName.toLowerCase(Locale.ROOT).startsWith(protectedName.toLowerCase(Locale.ROOT) + ".")) {
                return true;
            }
        }
        return false;
    }

    public boolean isValidPackageFullName(String packageFullName) {
        return packageFullName != null && !packageFullName.isBlank()
                && PACKAGE_FULL_NAME_PATTERN.matcher(packageFullName.trim()).matches();
    }

    /**
     * Scans installed AppX packages for current user and filters out frameworks.
     */
    public List<AppxPackageInfo> scanDebloatablePackages() {
        List<AppxPackageInfo> list = new ArrayList<>();
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return list;
        }

        String script = "Get-AppxPackage | Where-Object IsFramework -eq $false | Where-Object NonRemovable -eq $false | " +
                "Select-Object Name, PackageFullName, Publisher, InstallLocation, SignatureKind | ConvertTo-Json -Compress";

        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 30);
            if (result.isSuccess() && result.getOutput().length() > 2) {
                list = parseAppxJson(result.getOutput());
            }
        } catch (Exception e) {
            log.error("Failed to query AppX packages: {}", e.getMessage(), e);
        }

        return list;
    }

    /**
     * Uninstalls a specific AppX package for the current user.
     */
    public boolean removePackage(String packageFullName) {
        if (!isValidPackageFullName(packageFullName)) {
            log.error("SECURITY VIOLATION: Invalid AppX package name syntax: {}", packageFullName);
            return false;
        }
        if (isProtectedPackage(packageFullName)) {
            log.error("FAIL-SAFE: Refusing to remove protected system AppX package: {}", packageFullName);
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Remove-AppxPackage -Package \"" + packageFullName + "\""
            );
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 30);
            return result.isSuccess();
        } catch (Exception e) {
            log.error("Failed to remove AppX package {}: {}", packageFullName, e.getMessage());
            return false;
        }
    }

    /**
     * Uninstalls package for all users and de-provisions it (requires elevation).
     */
    public boolean deprovisionPackageAllUsers(String packageFullName) {
        if (!isValidPackageFullName(packageFullName)) {
            log.error("SECURITY VIOLATION: Invalid AppX package name syntax: {}", packageFullName);
            return false;
        }
        if (isProtectedPackage(packageFullName)) {
            log.error("FAIL-SAFE: Refusing to de-provision protected system AppX package: {}", packageFullName);
            return false;
        }
        String cmd = "Remove-AppxPackage -Package \"" + packageFullName + "\" -AllUsers";
        if (WindowsSecurityUtils.isRunningAsAdmin()) {
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", cmd);
                pb.redirectErrorStream(true);
                ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 45);
                return result.isSuccess();
            } catch (Exception e) {
                return false;
            }
        } else {
            return WindowsSecurityUtils.runElevated(cmd);
        }
    }

    private List<AppxPackageInfo> parseAppxJson(String json) {
        List<AppxPackageInfo> results = new ArrayList<>();
        List<String> items = splitJson(json);

        for (String itemJson : items) {
            String name = extractString(itemJson, "Name");
            String fullName = extractString(itemJson, "PackageFullName");
            String publisher = extractString(itemJson, "Publisher");
            String installLoc = extractString(itemJson, "InstallLocation");

            if (name.isBlank() || PROTECTED_PACKAGES.contains(name)) {
                continue;
            }

            long sizeBytes = calculateFolderSize(installLoc);
            String category = classifyPackage(name);
            String desc = describePackage(name);

            results.add(new AppxPackageInfo(
                    name,
                    formatDisplayName(name),
                    fullName,
                    publisher,
                    installLoc,
                    sizeBytes,
                    true,
                    category,
                    desc
            ));
        }

        return results;
    }

    private String classifyPackage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("xbox") || lower.contains("game")) return "Gaming & Overlays";
        if (lower.contains("bing") || lower.contains("news") || lower.contains("weather")) return "Bing & News Feed";
        if (lower.contains("telemetry") || lower.contains("feedback") || lower.contains("diagnostics")) return "Diagnostics & Telemetry";
        if (lower.contains("zune") || lower.contains("groove") || lower.contains("media") || lower.contains("music")) return "Media & Entertainment";
        if (lower.contains("hp") || lower.contains("dell") || lower.contains("lenovo") || lower.contains("asus")) return "OEM Utilities";
        return "Windows Apps";
    }

    private String describePackage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("xboxgameoverlay")) return "Xbox in-game HUD overlay and widget service.";
        if (lower.contains("mixedreality")) return "Windows Mixed Reality VR Portal launcher.";
        if (lower.contains("onedrivesync")) return "Microsoft OneDrive synchronization background engine.";
        if (lower.contains("hpaudiocenter") || lower.contains("hpquickdrop")) return "Pre-installed OEM hardware companion tool.";
        return "Standard Windows UWP application package.";
    }

    private String formatDisplayName(String name) {
        if (name == null) return "App";
        if (name.startsWith("Microsoft.")) {
            return name.substring("Microsoft.".length());
        }
        if (name.startsWith("AD2F1837.")) {
            return name.substring("AD2F1837.".length());
        }
        return name;
    }

    private long calculateFolderSize(String path) {
        if (path == null || path.isBlank()) return 0;
        File f = new File(path);
        if (!f.exists() || !f.isDirectory()) return 0;
        long total = 0;
        File[] files = f.listFiles();
        if (files != null) {
            for (File child : files) {
                if (child.isFile()) total += child.length();
            }
        }
        return total;
    }

    private List<String> splitJson(String json) {
        List<String> list = new ArrayList<>();
        if (!json.startsWith("[")) {
            if (json.startsWith("{")) list.add(json);
            return list;
        }
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    list.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return list;
    }

    private String extractString(String json, String key) {
        String p = "\"" + key + "\":\"";
        int idx = json.indexOf(p);
        if (idx == -1) return "";
        int s = idx + p.length();
        int e = json.indexOf("\"", s);
        if (e == -1) return "";
        return json.substring(s, e);
    }
}
