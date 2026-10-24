package com.cleanpilot.service;

import com.cleanpilot.model.ScannedFileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance NIO.2 large file scanner service with real-time throughput tracking
 * and inode / fileKey hardlink preservation.
 */
@Service
public class LargeFileScannerService {

    private static final Logger log = LoggerFactory.getLogger(LargeFileScannerService.class);

    /**
     * Traverses targetPath recursively and returns all files >= minSizeBytes.
     *
     * @param targetPath       Directory path to scan
     * @param minSizeBytes     Minimum file size threshold in bytes
     * @param excludedPaths    Set of paths or substrings to ignore
     * @param cancelFlag       Atomic flag to abort scan gracefully
     * @param progressListener Progress and throughput reporting callback
     * @return List of matching ScannedFileItem, sorted by size descending
     */
    public List<ScannedFileItem> scanLargeFiles(
            Path targetPath,
            long minSizeBytes,
            Set<String> excludedPaths,
            AtomicBoolean cancelFlag,
            ScanProgressListener progressListener
    ) {
        if (targetPath == null || !Files.exists(targetPath)) {
            throw new IllegalArgumentException("Target path does not exist: " + targetPath);
        }

        List<ScannedFileItem> matchedFiles = new ArrayList<>();
        AtomicLong filesVisited = new AtomicLong(0);
        AtomicLong bytesExamined = new AtomicLong(0);
        long startTime = System.currentTimeMillis();
        long[] lastReportTime = new long[]{startTime};

        try {
            Files.walkFileTree(targetPath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (cancelFlag != null && cancelFlag.get()) {
                        return FileVisitResult.TERMINATE;
                    }

                    // Reparse point / junction safety: skip symlinks and junctions to avoid loop / escaping
                    if (!dir.equals(targetPath) && (attrs.isSymbolicLink() || attrs.isOther() || Files.isSymbolicLink(dir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // Check exclusion rules
                    String dirStr = dir.toAbsolutePath().normalize().toString();
                    if (excludedPaths != null) {
                        for (String excluded : excludedPaths) {
                            if (dirStr.equalsIgnoreCase(excluded) || dirStr.startsWith(excluded)) {
                                log.debug("Skipping excluded directory: {}", dirStr);
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (cancelFlag != null && cancelFlag.get()) {
                        return FileVisitResult.TERMINATE;
                    }

                    long count = filesVisited.incrementAndGet();
                    long fileSize = 0;
                    try {
                        if (attrs.isRegularFile() && !attrs.isSymbolicLink()) {
                            fileSize = attrs.size();
                            bytesExamined.addAndGet(fileSize);

                            if (fileSize >= minSizeBytes) {
                                Instant lastModified = attrs.lastModifiedTime() != null
                                        ? attrs.lastModifiedTime().toInstant()
                                        : Instant.now();
                                Object fileKey = attrs.fileKey();
                                synchronized (matchedFiles) {
                                    matchedFiles.add(new ScannedFileItem(file, fileSize, lastModified, fileKey));
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Could not inspect file attributes for {}: {}", file, e.getMessage());
                    }

                    // Throttle progress updates to avoid flooding UI thread
                    long now = System.currentTimeMillis();
                    if (progressListener != null && (count % 250 == 0 || now - lastReportTime[0] > 150)) {
                        lastReportTime[0] = now;
                        double elapsedSeconds = Math.max(0.001, (now - startTime) / 1000.0);
                        double filesPerSec = count / elapsedSeconds;
                        double bytesPerSec = bytesExamined.get() / elapsedSeconds;
                        progressListener.onThroughput(count, bytesExamined.get(), file.toString(), filesPerSec, bytesPerSec);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.debug("Access denied or failed to visit: {} ({})", file, exc != null ? exc.getMessage() : "Unknown");
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("I/O error during file tree traversal: {}", e.getMessage(), e);
        }

        if (cancelFlag != null && cancelFlag.get()) {
            throw new CancellationException("Scan cancelled by user.");
        }

        if (progressListener != null) {
            long totalElapsed = Math.max(1, System.currentTimeMillis() - startTime);
            double filesPerSec = (filesVisited.get() * 1000.0) / totalElapsed;
            double bytesPerSec = (bytesExamined.get() * 1000.0) / totalElapsed;
            progressListener.onThroughput(filesVisited.get(), bytesExamined.get(), "Scan complete", filesPerSec, bytesPerSec);
        }

        // Sort descending by size
        matchedFiles.sort(Comparator.comparingLong(ScannedFileItem::getSizeInBytes).reversed());
        return matchedFiles;
    }
}
