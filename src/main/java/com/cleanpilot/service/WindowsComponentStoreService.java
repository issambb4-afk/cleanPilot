package com.cleanpilot.service;

import com.cleanpilot.model.ComponentStoreReport;
import com.cleanpilot.util.ProcessExecutionUtils;
import com.cleanpilot.util.WindowsSecurityUtils;
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
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Manages Windows Component Store (WinSxS), Windows Update caches, and superseded rollback backups.
 */
@Service
public class WindowsComponentStoreService {

    private static final Logger log = LoggerFactory.getLogger(WindowsComponentStoreService.class);

    private static final String SFT_DIST_DOWNLOAD = "C:\\Windows\\SoftwareDistribution\\Download";
    private static final String WINDOWS_OLD = "C:\\Windows.old";
    private static final String DELIVERY_OPTIMIZATION = "C:\\Windows\\SoftwareDistribution\\DeliveryOptimization";

    /**
     * Analyzes WinSxS component store using DISM.
     * If unprivileged or on timeout, falls back to direct filesystem estimation.
     */
    public ComponentStoreReport analyzeComponentStore() {
        boolean isAdmin = WindowsSecurityUtils.isRunningAsAdmin();
        if (isAdmin) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "dism", "/Online", "/Cleanup-Image", "/AnalyzeComponentStore"
                );
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                long actualSize = 0;
                long sharedSize = 0;
                long backupsSize = 0;
                boolean cleanupRecommended = false;
                StringBuilder sb = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                        String lower = line.toLowerCase();
                        if (lower.contains("actual size of component store") || lower.contains("taille réelle du magasin de composants")) {
                            actualSize = parseDismSizeBytes(line);
                        } else if (lower.contains("shared with windows") || lower.contains("partagé avec windows")) {
                            sharedSize = parseDismSizeBytes(line);
                        } else if (lower.contains("backups and disabled features") || lower.contains("sauvegardes et fonctionnalités désactivées")) {
                            backupsSize = parseDismSizeBytes(line);
                        } else if (lower.contains("component store cleanup recommended : yes") || lower.contains("nettoyage du magasin de composants recommandé : oui")) {
                            cleanupRecommended = true;
                        }
                    }
                }

                proc.waitFor();
                long reclaimable = backupsSize + calculateCacheFootprint();
                return new ComponentStoreReport(
                        actualSize > 0 ? actualSize : estimateWinSxSSize(),
                        sharedSize,
                        backupsSize,
                        reclaimable,
                        cleanupRecommended || reclaimable > 1_000_000_000L,
                        "Component Store analyzed via DISM: " + (cleanupRecommended ? "Cleanup recommended." : "Healthy condition.")
                );
            } catch (Exception e) {
                log.warn("DISM analyze failed: {}", e.getMessage());
            }
        }

        // Fallback filesystem estimation
        long estimatedWinSxS = estimateWinSxSSize();
        long cacheBytes = calculateCacheFootprint();
        long reclaimable = (long) (estimatedWinSxS * 0.15) + cacheBytes;
        return new ComponentStoreReport(
                estimatedWinSxS,
                (long) (estimatedWinSxS * 0.70),
                (long) (estimatedWinSxS * 0.15),
                reclaimable,
                reclaimable > 1_000_000_000L,
                "Estimated via filesystem analysis (Standard User Mode)."
        );
    }

    /**
     * Executes safe component store cleanup.
     * Purges SoftwareDistribution download cache and triggers DISM /StartComponentCleanup /ResetBase.
     */
    public boolean cleanupComponentStore(Consumer<String> progressListener) {
        return cleanupComponentStore(progressListener, false);
    }

    public boolean cleanupComponentStore(Consumer<String> progressListener, boolean dryRun) {
        if (progressListener != null) progressListener.accept("Purging Windows Update download caches...");

        if (dryRun) {
            if (progressListener != null) {
                progressListener.accept("Dry run mode: skipped physical file deletion and DISM execution.");
            }
            return true;
        }

        // 1. Purge download cache files
        if (WindowsSecurityUtils.isRunningAsAdmin()) {
            purgeDirectoryContents(new File(SFT_DIST_DOWNLOAD));
            purgeDirectoryContents(new File(DELIVERY_OPTIMIZATION));

            if (progressListener != null) progressListener.accept("Running DISM /StartComponentCleanup /ResetBase...");
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "dism", "/Online", "/Cleanup-Image", "/StartComponentCleanup", "/ResetBase"
                );
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), ProcessExecutionUtils.getNativeCharset()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (progressListener != null && line.contains("%")) {
                            progressListener.accept("DISM progress: " + line.trim());
                        }
                    }
                }
                int code = proc.waitFor();
                return code == 0;
            } catch (Exception e) {
                log.error("Failed to run DISM cleanup: {}", e.getMessage(), e);
                return false;
            }
        } else {
            // Elevated execution prompt
            if (progressListener != null) progressListener.accept("Requesting Administrator elevation for DISM cleanup...");
            return WindowsSecurityUtils.runElevated("dism /Online /Cleanup-Image /StartComponentCleanup /ResetBase");
        }
    }

    /**
     * Calculates size of unneeded Windows Update and rollback caches.
     */
    public long calculateCacheFootprint() {
        long total = 0;
        total += calculateDirectorySize(new File(SFT_DIST_DOWNLOAD));
        total += calculateDirectorySize(new File(DELIVERY_OPTIMIZATION));
        total += calculateDirectorySize(new File(WINDOWS_OLD));
        return total;
    }

    private long estimateWinSxSSize() {
        File dir = new File("C:\\Windows\\WinSxS");
        if (!dir.exists()) return 0;
        return calculateDirectorySize(dir);
    }

    private long calculateDirectorySize(File directory) {
        if (directory == null || !directory.exists()) return 0;
        AtomicLong size = new AtomicLong(0);
        try {
            Files.walk(directory.toPath(), 2)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            size.addAndGet(Files.size(p));
                        } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {}
        return size.get();
    }

    private void purgeDirectoryContents(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            try {
                if (f.isDirectory()) {
                    deleteRecursively(f.toPath());
                } else {
                    f.delete();
                }
            } catch (Exception ignored) {}
        }
    }

    private void deleteRecursively(Path path) {
        try {
            Files.walkFileTree(path, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // Reparse point / junction safety: unlink junction directly without descending into targets
                    if (!dir.equals(path) && (attrs.isSymbolicLink() || attrs.isOther() || Files.isSymbolicLink(dir))) {
                        Files.deleteIfExists(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {}
    }

    private long parseDismSizeBytes(String line) {
        try {
            String[] parts = line.split(":");
            if (parts.length > 1) {
                String valStr = parts[1].trim();
                double val = Double.parseDouble(valStr.replaceAll("[^0-9.]", ""));
                if (valStr.toUpperCase().contains("GB")) {
                    return (long) (val * 1024 * 1024 * 1024);
                } else if (valStr.toUpperCase().contains("MB")) {
                    return (long) (val * 1024 * 1024);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
