package com.cleanpilot.service;

import com.cleanpilot.model.RecoveryItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service managing Tier B: Native OS Recycle Bin / Trash Reader.
 * Inspects external historical deletions residing in the operating system's native trash:
 * - Windows: Shell.Application COM Namespace(10) query
 * - Linux: FreeDesktop trash specification (~/.local/share/Trash/info/*.trashinfo and files/)
 * - macOS: ~/.Trash and .Trashes
 * Provides seamless OS handoff via Desktop.getDesktop().open() or OS command fallbacks.
 */
@Service
public class SystemTrashReaderService {

    private static final Logger log = LoggerFactory.getLogger(SystemTrashReaderService.class);

    private final String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private final boolean isWindows = osName.contains("win");
    private final boolean isMac = osName.contains("mac");
    private final boolean isLinux = osName.contains("linux") || osName.contains("nix") || osName.contains("nux");

    /**
     * Reads items currently present in the system trash/recycle bin.
     */
    public List<RecoveryItemDTO> readSystemTrashItems() {
        if (isWindows) {
            return readWindowsRecycleBin();
        } else if (isLinux) {
            return readLinuxFreeDesktopTrash();
        } else if (isMac) {
            return readMacTrash();
        }
        return Collections.emptyList();
    }

    /**
     * Opens the native OS Recycle Bin / Trash window.
     */
    public boolean openSystemRecycleBin() {
        try {
            if (isWindows) {
                // explorer.exe shell:RecycleBinFolder
                new ProcessBuilder("explorer.exe", "shell:RecycleBinFolder").start();
                return true;
            } else if (isMac) {
                String trashPath = System.getProperty("user.home") + "/.Trash";
                File trashFile = new File(trashPath);
                if (trashFile.exists() && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(trashFile);
                    return true;
                }
                new ProcessBuilder("open", trashPath).start();
                return true;
            } else if (isLinux) {
                String trashUri = "trash:///";
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    try {
                        Desktop.getDesktop().browse(new URI(trashUri));
                        return true;
                    } catch (Exception ignored) {}
                }
                new ProcessBuilder("xdg-open", "trash:///").start();
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to open system recycle bin: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * Windows: Queries Shell.Application COM Namespace(10) for true original paths, dates, and sizes.
     */
    private List<RecoveryItemDTO> readWindowsRecycleBin() {
        List<RecoveryItemDTO> list = new ArrayList<>();
        String script = """
            $shell = New-Object -ComObject Shell.Application
            $recycleBin = $shell.Namespace(10)
            $results = foreach ($item in $recycleBin.Items()) {
                [PSCustomObject]@{
                    Name = $item.Name
                    OriginalLocation = $recycleBin.GetDetailsOf($item, 1)
                    DeletionDate = $recycleBin.GetDetailsOf($item, 2)
                    Size = [int64]$item.Size
                    Path = $item.Path
                }
            }
            $results | ConvertTo-Json -Compress
            """;

        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && sb.length() > 2) {
                list = parseWindowsJson(sb.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to query Windows recycle bin via PowerShell: {}", e.getMessage());
        }

        return list;
    }

    private List<RecoveryItemDTO> parseWindowsJson(String json) {
        List<RecoveryItemDTO> items = new ArrayList<>();
        if (!json.startsWith("[")) {
            if (json.startsWith("{")) {
                RecoveryItemDTO single = parseWindowsJsonObject(json);
                if (single != null) items.add(single);
            }
            return items;
        }

        int depth = 0;
        int startIndex = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) startIndex = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && startIndex != -1) {
                    String obj = json.substring(startIndex, i + 1);
                    RecoveryItemDTO item = parseWindowsJsonObject(obj);
                    if (item != null) items.add(item);
                    startIndex = -1;
                }
            }
        }
        return items;
    }

    private RecoveryItemDTO parseWindowsJsonObject(String objJson) {
        String name = extractJsonString(objJson, "Name");
        String originalLoc = extractJsonString(objJson, "OriginalLocation");
        String delDateStr = extractJsonString(objJson, "DeletionDate");
        long size = extractJsonLong(objJson, "Size", 0);
        String storagePath = extractJsonString(objJson, "Path");

        if (name.isBlank() && !storagePath.isBlank()) {
            name = Paths.get(storagePath).getFileName().toString();
        }

        String fullOriginal = !originalLoc.isBlank() ? originalLoc + File.separator + name : "Unknown Original Path";

        LocalDateTime delDate = parseFuzzyDate(delDateStr);

        return new RecoveryItemDTO(
                null,
                name,
                fullOriginal,
                storagePath,
                size,
                delDate,
                null,
                RecoveryItemDTO.RecoverySource.SYSTEM_TRASH,
                "OS Recycle Bin"
        );
    }

    /**
     * Linux: Parses FreeDesktop.org Trash specification:
     * $XDG_DATA_HOME/Trash or ~/.local/share/Trash
     * - info/*.trashinfo contains Path= and DeletionDate=
     * - files/* contains the actual data
     */
    private List<RecoveryItemDTO> readLinuxFreeDesktopTrash() {
        List<RecoveryItemDTO> items = new ArrayList<>();
        String home = System.getProperty("user.home", ".");
        Path trashInfoDir = Paths.get(home, ".local", "share", "Trash", "info");
        Path trashFilesDir = Paths.get(home, ".local", "share", "Trash", "files");

        if (!Files.exists(trashInfoDir) || !Files.isDirectory(trashInfoDir)) {
            return items;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(trashInfoDir, "*.trashinfo")) {
            for (Path infoFile : stream) {
                try {
                    String fileName = infoFile.getFileName().toString();
                    String baseName = fileName.endsWith(".trashinfo")
                            ? fileName.substring(0, fileName.length() - ".trashinfo".length())
                            : fileName;

                    Path payloadPath = trashFilesDir.resolve(baseName);
                    long size = Files.exists(payloadPath) ? Files.size(payloadPath) : 0;

                    List<String> lines = Files.readAllLines(infoFile, StandardCharsets.UTF_8);
                    String origPath = "";
                    LocalDateTime delDate = LocalDateTime.now();

                    for (String line : lines) {
                        if (line.startsWith("Path=")) {
                            String encoded = line.substring("Path=".length()).trim();
                            try {
                                origPath = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                            } catch (Exception ignored) {
                                origPath = encoded;
                            }
                        } else if (line.startsWith("DeletionDate=")) {
                            String dateStr = line.substring("DeletionDate=".length()).trim();
                            try {
                                delDate = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            } catch (Exception ignored) {}
                        }
                    }

                    if (origPath.isBlank()) {
                        origPath = home + File.separator + baseName;
                    }

                    items.add(new RecoveryItemDTO(
                            null,
                            baseName,
                            origPath,
                            payloadPath.toString(),
                            size,
                            delDate,
                            null,
                            RecoveryItemDTO.RecoverySource.SYSTEM_TRASH,
                            "FreeDesktop Trash"
                    ));
                } catch (Exception ex) {
                    log.debug("Could not parse trashinfo file {}: {}", infoFile, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enumerate Linux Trash: {}", e.getMessage());
        }

        return items;
    }

    /**
     * macOS: Inspects ~/.Trash
     */
    private List<RecoveryItemDTO> readMacTrash() {
        List<RecoveryItemDTO> items = new ArrayList<>();
        String home = System.getProperty("user.home", ".");
        Path macTrash = Paths.get(home, ".Trash");

        if (Files.exists(macTrash) && Files.isDirectory(macTrash)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(macTrash)) {
                for (Path p : stream) {
                    try {
                        long size = Files.size(p);
                        LocalDateTime date = LocalDateTime.now();
                        try {
                            date = LocalDateTime.ofInstant(
                                    Files.getLastModifiedTime(p).toInstant(),
                                    java.time.ZoneId.systemDefault()
                            );
                        } catch (Exception ignored) {}

                        items.add(new RecoveryItemDTO(
                                null,
                                p.getFileName().toString(),
                                "macOS Trash (" + p.getFileName() + ")",
                                p.toAbsolutePath().toString(),
                                size,
                                date,
                                null,
                                RecoveryItemDTO.RecoverySource.SYSTEM_TRASH,
                                "macOS Trash"
                        ));
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log.warn("Failed to enumerate macOS Trash: {}", e.getMessage());
            }
        }
        return items;
    }

    private LocalDateTime parseFuzzyDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDateTime.now();
        }
        // Remove invisible Unicode characters (like LTR/RTL marks often in Windows output)
        String clean = dateStr.replaceAll("[^0-9/:\\-\\s]", "").trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("M/d/yyyy h:mm a"),
                DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a")
        );

        for (DateTimeFormatter dtf : formatters) {
            try {
                return LocalDateTime.parse(clean, dtf);
            } catch (Exception ignored) {}
        }
        return LocalDateTime.now();
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return "";
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end).replace("\\\\", "\\").replace("\\\"", "\"");
    }

    private long extractJsonLong(String json, String key, long defaultVal) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultVal;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
