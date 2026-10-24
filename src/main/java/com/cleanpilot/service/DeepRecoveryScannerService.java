package com.cleanpilot.service;

import com.cleanpilot.model.RecoveryItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance Deep Search engine for recovering files removed outside CleanPilot:
 * 1. Physical multi-drive Recycle Bin scanner (C:\$Recycle.Bin, D:\$Recycle.Bin, etc.)
 * 2. Application auto-save, shadow copy caches, and backup versions (.bak, .old, .tmp, .orig, *~)
 * 3. Linux/macOS orphan trash stages (~/.Trash, /tmp/.Trash*, ~/.local/share/Trash)
 */
@Service
public class DeepRecoveryScannerService {

    private static final Logger log = LoggerFactory.getLogger(DeepRecoveryScannerService.class);

    private final String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private final boolean isWindows = osName.contains("win");

    public interface DeepScanListener {
        void onProgress(String currentTarget, int itemsFound);
    }

    /**
     * Conducts a deep scan across all mounted drive recycle bins, user auto-save directories,
     * and orphaned backup stores.
     *
     * @param cancelFlag Cancellation atomic flag
     * @param listener Progress and findings callback
     * @return List of discoverable historical recovery items
     */
    public List<RecoveryItemDTO> performDeepRecoveryScan(AtomicBoolean cancelFlag, DeepScanListener listener) {
        List<RecoveryItemDTO> results = new ArrayList<>();

        if (isWindows) {
            scanWindowsPhysicalRecycleBins(results, cancelFlag, listener);
            scanWindowsAppAutoSaveAndBackups(results, cancelFlag, listener);
        } else {
            scanUnixTrashAndOrphanedFiles(results, cancelFlag, listener);
        }

        return results;
    }

    /**
     * Windows: Scans physical \$Recycle.Bin directories across all accessible root drives (C:\, D:\, etc.)
     */
    private void scanWindowsPhysicalRecycleBins(List<RecoveryItemDTO> results, AtomicBoolean cancelFlag, DeepScanListener listener) {
        File[] roots = File.listRoots();
        if (roots == null) return;

        for (File root : roots) {
            if (cancelFlag != null && cancelFlag.get()) break;

            Path rbPath = root.toPath().resolve("$Recycle.Bin");
            if (Files.exists(rbPath) && Files.isDirectory(rbPath)) {
                if (listener != null) listener.onProgress("Inspecting physical Recycle Bin on " + root.getPath(), results.size());

                try {
                    Files.walkFileTree(rbPath, EnumSet.noneOf(FileVisitOption.class), 4, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (cancelFlag != null && cancelFlag.get()) return FileVisitResult.TERMINATE;
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (cancelFlag != null && cancelFlag.get()) return FileVisitResult.TERMINATE;

                            String fileName = file.getFileName().toString();
                            // Look for payload files in Recycle.Bin ($R...)
                            if (fileName.startsWith("$R") || fileName.startsWith("$r")) {
                                LocalDateTime lastModified = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                                results.add(new RecoveryItemDTO(
                                        null,
                                        fileName,
                                        file.toAbsolutePath().toString(),
                                        file.toAbsolutePath().toString(),
                                        attrs.size(),
                                        lastModified,
                                        null,
                                        RecoveryItemDTO.RecoverySource.DEEP_RECOVERY,
                                        "Physical Drive Recycle Bin: " + root.getPath()
                                ));
                                if (listener != null) listener.onProgress(file.toString(), results.size());
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (Exception e) {
                    log.debug("Could not scan physical recycle bin at {}: {}", rbPath, e.getMessage());
                }
            }
        }
    }

    /**
     * Windows: Discovers orphaned backup files, auto-save drafts, and editor swap files
     * (.bak, .old, .orig, .draft) in common document and user project folders.
     */
    private void scanWindowsAppAutoSaveAndBackups(List<RecoveryItemDTO> results, AtomicBoolean cancelFlag, DeepScanListener listener) {
        String userHome = System.getProperty("user.home", ".");
        List<Path> targets = List.of(
                Paths.get(userHome, "Documents"),
                Paths.get(userHome, "Desktop"),
                Paths.get(userHome, "Downloads"),
                Paths.get(userHome, "AppData", "Roaming", "Microsoft", "Word"),
                Paths.get(userHome, "AppData", "Roaming", "Microsoft", "Excel")
        );

        for (Path targetDir : targets) {
            if (cancelFlag != null && cancelFlag.get()) break;
            if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) continue;

            if (listener != null) listener.onProgress("Scanning backup artifacts in " + targetDir.getFileName(), results.size());

            try {
                Files.walkFileTree(targetDir, EnumSet.noneOf(FileVisitOption.class), 4, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (cancelFlag != null && cancelFlag.get()) return FileVisitResult.TERMINATE;
                        String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        // Skip huge dependency dirs
                        if (dirName.equalsIgnoreCase("node_modules") || dirName.equalsIgnoreCase(".git")
                                || dirName.equalsIgnoreCase(".gradle") || dirName.equalsIgnoreCase("target")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (cancelFlag != null && cancelFlag.get()) return FileVisitResult.TERMINATE;

                        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (name.endsWith(".bak") || name.endsWith(".old") || name.endsWith(".orig")
                                || name.endsWith(".asd") || name.endsWith(".wbk") || name.endsWith("~")
                                || (name.startsWith("~") && attrs.size() > 1024)) {
                            LocalDateTime lastModified = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                            results.add(new RecoveryItemDTO(
                                    null,
                                    file.getFileName().toString(),
                                    file.toAbsolutePath().toString(),
                                    file.toAbsolutePath().toString(),
                                    attrs.size(),
                                    lastModified,
                                    null,
                                    RecoveryItemDTO.RecoverySource.DEEP_RECOVERY,
                                    "App Auto-Save / Backup Copy"
                            ));
                            if (listener != null) listener.onProgress(file.toString(), results.size());
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (Exception e) {
                log.debug("Error scanning directory {}: {}", targetDir, e.getMessage());
            }
        }
    }

    /**
     * Linux / macOS: Traverses trash stages and backup directories.
     */
    private void scanUnixTrashAndOrphanedFiles(List<RecoveryItemDTO> results, AtomicBoolean cancelFlag, DeepScanListener listener) {
        String home = System.getProperty("user.home", ".");
        List<Path> targets = List.of(
                Paths.get(home, ".local", "share", "Trash", "files"),
                Paths.get(home, ".Trash"),
                Paths.get(home, "Documents")
        );

        for (Path targetDir : targets) {
            if (cancelFlag != null && cancelFlag.get()) break;
            if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) continue;

            if (listener != null) listener.onProgress("Scanning Unix trash stage: " + targetDir, results.size());

            try {
                Files.walkFileTree(targetDir, EnumSet.noneOf(FileVisitOption.class), 4, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (cancelFlag != null && cancelFlag.get()) return FileVisitResult.TERMINATE;

                        String name = file.getFileName().toString();
                        LocalDateTime lastModified = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                        results.add(new RecoveryItemDTO(
                                null,
                                name,
                                file.toAbsolutePath().toString(),
                                file.toAbsolutePath().toString(),
                                attrs.size(),
                                lastModified,
                                null,
                                RecoveryItemDTO.RecoverySource.DEEP_RECOVERY,
                                "Orphaned Unix Artifact"
                        ));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (Exception ignored) {}
        }
    }

    /**
     * Recovers a deep search item to a safe chosen destination directory.
     *
     * @param item Recovery item
     * @param targetDirectory Directory where file will be extracted/restored
     * @return Resulting destination Path
     * @throws IOException If copy/move fails
     */
    public Path recoverToDirectory(RecoveryItemDTO item, Path targetDirectory) throws IOException {
        if (item == null || item.getCurrentStoragePath() == null) {
            throw new IllegalArgumentException("Invalid recovery item");
        }
        Path source = Paths.get(item.getCurrentStoragePath());
        if (!Files.exists(source)) {
            throw new NoSuchFileException("Source file no longer exists: " + source);
        }

        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory);
        }

        String rawName = item.getFileName();
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Invalid file name in recovery item");
        }
        // Path traversal defense: strip any directory segments or traversal tokens
        String targetName = Paths.get(rawName).getFileName().toString();
        Path normalizedTargetDir = targetDirectory.toAbsolutePath().normalize();
        Path destination = normalizedTargetDir.resolve(targetName).normalize();
        if (!destination.startsWith(normalizedTargetDir)) {
            throw new SecurityException("Path traversal attempt detected: " + rawName);
        }

        // Handle collision
        if (Files.exists(destination)) {
            int dotIdx = targetName.lastIndexOf('.');
            String base = (dotIdx > 0) ? targetName.substring(0, dotIdx) : targetName;
            String ext = (dotIdx > 0) ? targetName.substring(dotIdx) : "";
            int count = 1;
            do {
                destination = normalizedTargetDir.resolve(base + "_recovered_" + count + ext).normalize();
                count++;
            } while (Files.exists(destination));
        }

        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        log.info("Successfully recovered {} to {}", source, destination);
        return destination;
    }
}
