package com.cleanpilot.service;

import com.cleanpilot.model.AppResidualInfo;
import com.cleanpilot.model.InstalledAppInfo;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.util.ProcessExecutionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for querying installed Windows software, executing uninstallers,
 * and scanning/cleaning orphaned residuals in AppData and ProgramData.
 */
@Service
public class AppUninstallerService {

    private static final Logger log = LoggerFactory.getLogger(AppUninstallerService.class);

    private final OperationLifecycleManager lifecycleManager;
    private final boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    public AppUninstallerService() {
        this(null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AppUninstallerService(@org.springframework.context.annotation.Lazy OperationLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }

    private static final String[] UNINSTALL_REG_KEYS = {
            "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKLM\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
    };

    private static final Set<String> SYSTEM_DIR_WHITELIST = Set.of(
            "microsoft", "windows", "package cache", "packages", "temp", "directx",
            "crashdumps", "nvidia", "intel", "amd", "system", "system32", "cleanpilot"
    );

    /**
     * Scans Windows Registry for all installed applications.
     */
    public List<InstalledAppInfo> scanInstalledApps() {
        Map<String, InstalledAppInfo> apps = new HashMap<>();

        for (String rootKey : UNINSTALL_REG_KEYS) {
            queryRegistryUninstallKey(rootKey, apps);
        }

        List<InstalledAppInfo> sorted = new ArrayList<>(apps.values());
        // Sort descending by estimated size, then by name
        sorted.sort((a, b) -> {
            int comp = Long.compare(b.getEstimatedSizeBytes(), a.getEstimatedSizeBytes());
            return comp != 0 ? comp : a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });
        return sorted;
    }

    private void queryRegistryUninstallKey(String rootKey, Map<String, InstalledAppInfo> appMap) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg.exe", "query", rootKey, "/s");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String currentSubKey = null;
            Map<String, String> currentValues = new HashMap<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), ProcessExecutionUtils.getNativeCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.startsWith("HKEY_")) {
                        // Flush previous subkey
                        processSubKey(currentSubKey, currentValues, rootKey.contains("WOW6432Node"), appMap);
                        currentSubKey = line;
                        currentValues.clear();
                    } else {
                        // Parse "ValueName REG_TYPE ValueData"
                        String[] parts = line.split("\\s{4,}|\\t+", 3);
                        if (parts.length >= 3) {
                            currentValues.put(parts[0].trim().toLowerCase(Locale.ROOT), parts[2].trim());
                        } else if (parts.length == 2 && parts[1].startsWith("REG_")) {
                            currentValues.put(parts[0].trim().toLowerCase(Locale.ROOT), "");
                        }
                    }
                }
                // Flush final subkey
                processSubKey(currentSubKey, currentValues, rootKey.contains("WOW6432Node"), appMap);
            }
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                log.warn("reg query {} exceeded 20s timeout and was terminated.", rootKey);
            }
        } catch (Exception ex) {
            log.debug("Failed querying uninstall registry key {}: {}", rootKey, ex.getMessage());
        }
    }

    private void processSubKey(String subKey, Map<String, String> values, boolean is32BitOn64, Map<String, InstalledAppInfo> appMap) {
        if (subKey == null || values.isEmpty()) return;

        String displayName = values.get("displayname");
        if (displayName == null || displayName.isBlank()) return;

        // Clean any non-breaking spaces or encoding artifacts from console output
        displayName = displayName.replace('\u00A0', ' ').replace('\uFFFD', ' ').replaceAll("\\s+", " ").trim();
        if (displayName.isBlank()) return;

        // Skip Windows system components and updates
        String systemComponent = values.get("systemcomponent");
        if ("0x1".equalsIgnoreCase(systemComponent) || "1".equals(systemComponent)) return;

        String parentKey = values.get("parentkeyname");
        if (parentKey != null && !parentKey.isBlank()) return;

        String uninstallString = values.get("uninstallstring");
        if (uninstallString == null || uninstallString.isBlank()) return;

        String displayVersion = values.get("displayversion");
        String publisher = values.get("publisher");
        String installDate = values.get("installdate");
        String installLocation = values.get("installlocation");
        String quietUninstall = values.get("quietuninstallstring");

        // Parse EstimatedSize (in KB in Windows registry)
        long sizeBytes = 0;
        String rawSize = values.get("estimatedsize");
        if (rawSize != null) {
            try {
                long sizeKb = rawSize.startsWith("0x")
                        ? Long.parseLong(rawSize.substring(2), 16)
                        : Long.parseLong(rawSize);
                sizeBytes = sizeKb * 1024L;
            } catch (Exception ignored) {}
        }

        // If estimated size was missing, check installLocation directory if it exists
        if (sizeBytes == 0 && installLocation != null && !installLocation.isBlank()) {
            Path loc = Paths.get(installLocation);
            if (Files.exists(loc) && Files.isDirectory(loc)) {
                sizeBytes = quickDirectorySize(loc, 3);
            }
        }

        InstalledAppInfo info = new InstalledAppInfo(
                displayName,
                displayVersion,
                publisher,
                installDate,
                installLocation,
                uninstallString,
                quietUninstall,
                sizeBytes,
                !is32BitOn64,
                subKey
        );

        appMap.put(displayName.toLowerCase(Locale.ROOT), info);
    }

    /**
     * Scans %APPDATA%, %LOCALAPPDATA%, and C:\ProgramData for orphaned residual folders
     * belonging to uninstalled software.
     */
    public List<AppResidualInfo> scanOrphanedResiduals(List<InstalledAppInfo> installedApps) {
        List<AppResidualInfo> residuals = new ArrayList<>();

        Set<String> activeAppTokens = new HashSet<>();
        for (InstalledAppInfo app : installedApps) {
            for (String token : app.getDisplayName().toLowerCase(Locale.ROOT).split("[\\s_.-]+")) {
                if (token.length() >= 3) {
                    activeAppTokens.add(token);
                }
            }
            if (app.getPublisher() != null) {
                for (String pubToken : app.getPublisher().toLowerCase(Locale.ROOT).split("[\\s_.-]+")) {
                    if (pubToken.length() >= 3) {
                        activeAppTokens.add(pubToken);
                    }
                }
            }
        }

        Map<String, String> areas = new LinkedHashMap<>();
        String appData = System.getenv("APPDATA");
        if (appData != null) areas.put("AppData (Roaming)", appData);

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) areas.put("AppData (Local)", localAppData);

        String programData = System.getenv("ProgramData");
        if (programData != null) areas.put("ProgramData", programData);

        for (Map.Entry<String, String> entry : areas.entrySet()) {
            String areaName = entry.getKey();
            Path dirPath = Paths.get(entry.getValue());
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) continue;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                for (Path folder : stream) {
                    if (!Files.isDirectory(folder)) continue;
                    String folderName = folder.getFileName().toString().toLowerCase(Locale.ROOT);

                    if (SYSTEM_DIR_WHITELIST.contains(folderName)) continue;

                    // Check if folderName matches any active app or publisher
                    boolean isKnownActive = false;
                    for (String token : folderName.split("[\\s_.-]+")) {
                        if (token.length() >= 3 && activeAppTokens.contains(token)) {
                            isKnownActive = true;
                            break;
                        }
                    }

                    if (!isKnownActive) {
                        long size = quickDirectorySize(folder, 5);
                        if (size > 50 * 1024L) { // Only flag folders > 50 KB
                            residuals.add(new AppResidualInfo(
                                    folder.getFileName().toString(),
                                    folder,
                                    size,
                                    areaName
                            ));
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Error inspecting residual directory {}: {}", dirPath, ex.getMessage());
            }
        }

        residuals.sort((a, b) -> Long.compare(b.getSizeBytes(), a.getSizeBytes()));
        return residuals;
    }

    private long quickDirectorySize(Path dir, int maxDepth) {
        AtomicLong size = new AtomicLong(0);
        try {
            Files.walkFileTree(dir, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {}
        return size.get();
    }

    public static record ParsedCommand(String executable, String arguments) {}

    /**
     * Parses Windows uninstall command lines, separating the executable path from arguments
     * while correctly handling unquoted or quoted paths with spaces.
     */
    public static ParsedCommand parseCommand(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedCommand("", "");
        }
        String trimmed = raw.trim();

        // 1. Quoted executable: "C:\Program Files\Vendor\App.exe" /arg1 /arg2
        if (trimmed.startsWith("\"")) {
            int closingQuote = trimmed.indexOf('"', 1);
            if (closingQuote > 0) {
                String exe = trimmed.substring(1, closingQuote).trim();
                String args = trimmed.substring(closingQuote + 1).trim();
                return new ParsedCommand(exe, args);
            }
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);

        // 2. MSI Installer: MsiExec.exe /X{...} or msiexec /i ...
        if (lower.startsWith("msiexec")) {
            int firstSpace = trimmed.indexOf(' ');
            if (firstSpace > 0) {
                return new ParsedCommand("msiexec.exe", trimmed.substring(firstSpace + 1).trim());
            }
            return new ParsedCommand("msiexec.exe", "");
        }

        // 3. Command starts with cmd /c or cmd.exe /c
        if (lower.startsWith("cmd /c ") || lower.startsWith("cmd.exe /c ")) {
            int cIdx = lower.indexOf("/c ");
            String afterC = trimmed.substring(cIdx + 3).trim();
            if (afterC.startsWith("\"") && afterC.endsWith("\"") && afterC.length() >= 2) {
                afterC = afterC.substring(1, afterC.length() - 1).trim();
            }
            return new ParsedCommand("cmd.exe", "/c " + afterC);
        }

        // 4. Unquoted path containing .exe / .cmd / .bat with arguments
        // e.g. C:/Program Files/App/uninstall.exe /quiet
        for (String ext : List.of(".exe", ".cmd", ".bat")) {
            int extIdx = lower.indexOf(ext);
            if (extIdx > 0) {
                int endOfExe = extIdx + ext.length();
                if (endOfExe == trimmed.length() || Character.isWhitespace(trimmed.charAt(endOfExe))) {
                    String exe = trimmed.substring(0, endOfExe).trim();
                    String args = trimmed.substring(endOfExe).trim();
                    return new ParsedCommand(exe, args);
                }
            }
        }

        // 5. Fallback: split on first whitespace
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace > 0) {
            return new ParsedCommand(trimmed.substring(0, firstSpace).trim(), trimmed.substring(firstSpace + 1).trim());
        }

        return new ParsedCommand(trimmed, "");
    }

    private static String escapePsSingleQuote(String s) {
        if (s == null) return "";
        return s.replace("'", "''");
    }

    /**
     * Executes the uninstaller for the given application.
     * Uses PowerShell Start-Process on Windows to invoke ShellExecuteExW under the hood,
     * which natively handles spaces in paths and arguments, and prompts for UAC elevation if required.
     */
    public boolean runUninstaller(InstalledAppInfo app, boolean quiet) {
        String rawCmd = (quiet && app.hasQuietUninstall())
                ? app.getQuietUninstallString()
                : app.getUninstallString();

        if (rawCmd == null || rawCmd.isBlank()) return false;

        try {
            ParsedCommand parsed = parseCommand(rawCmd);
            log.info("Launching uninstaller for {}: exe=[{}], args=[{}]", app.getDisplayName(), parsed.executable(), parsed.arguments());

            if (isWindows) {
                List<String> psCmd = new ArrayList<>();
                psCmd.add("powershell.exe");
                psCmd.add("-NoProfile");
                psCmd.add("-WindowStyle");
                psCmd.add("Hidden");
                psCmd.add("-Command");

                StringBuilder script = new StringBuilder();
                script.append("Start-Process -FilePath '").append(escapePsSingleQuote(parsed.executable())).append("'");
                if (parsed.arguments() != null && !parsed.arguments().isBlank()) {
                    script.append(" -ArgumentList '").append(escapePsSingleQuote(parsed.arguments())).append("'");
                }

                psCmd.add(script.toString());

                ProcessBuilder pb = new ProcessBuilder(psCmd);
                pb.redirectErrorStream(true);
                pb.start();
                return true;
            } else {
                List<String> cmdList = new ArrayList<>();
                cmdList.add(parsed.executable());
                if (!parsed.arguments().isBlank()) {
                    cmdList.addAll(Arrays.asList(parsed.arguments().split("\\s+")));
                }
                new ProcessBuilder(cmdList).start();
                return true;
            }
        } catch (Exception ex) {
            log.error("Failed to launch uninstaller for {}: {}", app.getDisplayName(), ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Safely deletes an orphaned residual directory with strict system root and depth guards.
     */
    public boolean deleteResidual(AppResidualInfo residual) {
        if (residual == null || residual.getPath() == null) {
            return false;
        }
        Path p = residual.getPath().toAbsolutePath().normalize();
        if (!Files.exists(p)) return true;

        if (!isSafeResidualPath(p)) {
            log.error("SAFETY VIOLATION: Refusing to delete unsafe residual directory: {}", p);
            return false;
        }

        String opId = lifecycleManager != null ?
                lifecycleManager.startOperation("APP_RESIDUAL_CLEANUP", RiskClassification.MODERATE_RISK, p.toString()) : null;
        if (opId != null) lifecycleManager.beginExecution(opId);

        try {
            long size = residual.getSizeBytes();
            Files.walkFileTree(p, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // Reparse point / junction safety: never enter junction targets; unlink the junction directly
                    if (!dir.equals(p) && (attrs.isSymbolicLink() || attrs.isOther() || Files.isSymbolicLink(dir))) {
                        Files.delete(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });

            if (opId != null) {
                lifecycleManager.recordItemProgress(opId, p.toString(), true, size, "App residual directory deleted");
                lifecycleManager.completeOperation(opId, size, Collections.emptyList());
            }
            return true;
        } catch (Exception ex) {
            log.error("Failed to clean residual directory {}: {}", p, ex.getMessage(), ex);
            if (opId != null) {
                lifecycleManager.recordItemProgress(opId, p.toString(), false, 0, ex.getMessage());
                lifecycleManager.failOperation(opId, ex.getMessage(), ex);
            }
            return false;
        }
    }

    /**
     * Validates that the path is an authentic application residual folder and not a system or root directory.
     */
    public boolean isSafeResidualPath(Path path) {
        if (path == null) return false;
        Path normalized = path.toAbsolutePath().normalize();

        // Must have at least 3 path segments (e.g. C:\Users\Username\AppData\...)
        if (normalized.getNameCount() < 3) {
            return false;
        }

        String lowerPath = normalized.toString().toLowerCase(Locale.ROOT);

        // Absolute blacklist against root and core directories
        if (lowerPath.matches("^[a-z]:\\\\?$")
                || lowerPath.equals("c:\\windows") || lowerPath.startsWith("c:\\windows\\")
                || lowerPath.equals("c:\\program files") || lowerPath.equals("c:\\program files (x86)")
                || lowerPath.equals("c:\\users") || lowerPath.matches("^[a-z]:\\\\users\\\\[^\\\\]+$")) {
            return false;
        }

        // Must reside within recognized AppData, ProgramData, or temporary directories
        boolean inAllowedParent = lowerPath.contains("\\appdata\\")
                || lowerPath.contains("\\programdata\\")
                || lowerPath.contains("\\temp\\");

        String javaTmp = System.getProperty("java.io.tmpdir", "").toLowerCase(Locale.ROOT);
        if (!javaTmp.isBlank() && lowerPath.startsWith(javaTmp)) {
            inAllowedParent = true;
        }

        return inAllowedParent;
    }
}
