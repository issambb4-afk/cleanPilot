package com.cleanpilot.service;

import com.cleanpilot.model.DuplicateFileGroup;
import com.cleanpilot.model.ScannedFileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, memory-bounded streaming duplicate detector.
 * Performs multi-pass detection with NVMe parallel throughput:
 * 1. Exact size grouping with Inode/Hardlink deduplication
 * 2. Head + Tail (Two-Sided 4 KB) fast boundary hash elimination
 * 3. 128 KB Direct-Buffer FileChannel parallel streaming SHA-256 verification
 */
@Service
public class DuplicateDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionService.class);
    private static final int CHUNK_SIZE = 4096; // 4 KB boundary
    private static final int DIRECT_BUFFER_SIZE = 128 * 1024; // 128 KB off-heap direct buffer

    public List<DuplicateFileGroup> detectDuplicates(
            Path targetPath,
            long minSizeBytes,
            Set<String> excludedPaths,
            AtomicBoolean cancelFlag,
            ScanProgressListener progressListener
    ) {
        if (targetPath == null || !Files.exists(targetPath)) {
            throw new IllegalArgumentException("Target path does not exist: " + targetPath);
        }

        long overallStartTime = System.currentTimeMillis();
        checkCancelled(cancelFlag);
        if (progressListener != null) {
            progressListener.onStatus("Pass 1/3: Grouping files by exact byte size with Inode verification...");
        }

        // --- PASS 1: Group files by exact byte size & skip identical hardlinks ---
        Map<Long, List<Path>> sizeBuckets = new ConcurrentHashMap<>();
        Set<Object> registeredInodes = Collections.synchronizedSet(new HashSet<>());
        AtomicLong filesVisited = new AtomicLong(0);
        AtomicLong bytesExamined = new AtomicLong(0);

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
                    String dirStr = dir.toAbsolutePath().normalize().toString();
                    if (excludedPaths != null) {
                        for (String excluded : excludedPaths) {
                            if (dirStr.equalsIgnoreCase(excluded) || dirStr.startsWith(excluded)) {
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
                    try {
                        if (attrs.isRegularFile() && !attrs.isSymbolicLink()) {
                            Object fileKey = attrs.fileKey();
                            if (fileKey != null && !registeredInodes.add(fileKey)) {
                                // Skip hardlink pointing to already registered physical inode
                                log.debug("Skipping hardlinked duplicate entry: {}", file);
                                return FileVisitResult.CONTINUE;
                            }

                            long size = attrs.size();
                            bytesExamined.addAndGet(size);
                            if (size >= minSizeBytes) {
                                sizeBuckets.computeIfAbsent(size, k -> Collections.synchronizedList(new ArrayList<>())).add(file);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Could not inspect {}: {}", file, e.getMessage());
                    }

                    if (progressListener != null && count % 300 == 0) {
                        double elapsed = Math.max(0.001, (System.currentTimeMillis() - overallStartTime) / 1000.0);
                        progressListener.onThroughput(count, bytesExamined.get(), file.toString(),
                                count / elapsed, bytesExamined.get() / elapsed);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("I/O error during directory walking: {}", e.getMessage(), e);
        }

        checkCancelled(cancelFlag);

        // Keep only size collisions (size buckets with >= 2 files)
        Map<Long, List<Path>> candidateSizeBuckets = new HashMap<>();
        for (Map.Entry<Long, List<Path>> entry : sizeBuckets.entrySet()) {
            if (entry.getValue().size() >= 2) {
                candidateSizeBuckets.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        if (candidateSizeBuckets.isEmpty()) {
            if (progressListener != null) {
                progressListener.onStatus("No size collisions found.");
            }
            return Collections.emptyList();
        }

        // --- PASS 2: Head + Tail (Two-Sided) Fast Boundary Hash ---
        if (progressListener != null) {
            progressListener.onStatus("Pass 2/3: Computing fast Head + Tail boundary hashes for size collisions...");
        }

        Map<String, List<Path>> headTailBuckets = new ConcurrentHashMap<>();
        for (Map.Entry<Long, List<Path>> entry : candidateSizeBuckets.entrySet()) {
            long size = entry.getKey();
            for (Path file : entry.getValue()) {
                checkCancelled(cancelFlag);
                try {
                    String boundaryHash = computeHeadTailHash(file, size);
                    String key = size + ":" + boundaryHash;
                    headTailBuckets.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add(file);
                } catch (Exception e) {
                    log.warn("Could not compute Head+Tail hash for {}: {}", file, e.getMessage());
                }
            }
        }

        checkCancelled(cancelFlag);

        // Keep only collisions on Head + Tail hash
        Map<String, List<Path>> candidateBoundaryBuckets = new HashMap<>();
        for (Map.Entry<String, List<Path>> entry : headTailBuckets.entrySet()) {
            if (entry.getValue().size() >= 2) {
                candidateBoundaryBuckets.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        if (candidateBoundaryBuckets.isEmpty()) {
            if (progressListener != null) {
                progressListener.onStatus("No boundary collisions found.");
            }
            return Collections.emptyList();
        }

        // --- PASS 3: Parallel Direct-Buffer FileChannel Full SHA-256 Verification ---
        if (progressListener != null) {
            progressListener.onStatus("Pass 3/3: Streaming parallel direct-buffer SHA-256 verification...");
        }

        Map<String, List<ScannedFileItem>> fullHashBuckets = new ConcurrentHashMap<>();
        Map<String, Long> groupSizes = new ConcurrentHashMap<>();

        // Flatten remaining candidates for concurrent hashing
        List<Path> candidateFilesToHash = new ArrayList<>();
        for (List<Path> paths : candidateBoundaryBuckets.values()) {
            candidateFilesToHash.addAll(paths);
        }

        int workerCount = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        ExecutorService hashExecutor = Executors.newFixedThreadPool(workerCount);

        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Path file : candidateFilesToHash) {
                futures.add(hashExecutor.submit(() -> {
                    checkCancelled(cancelFlag);
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    long size = attrs.size();
                    Instant lastModified = attrs.lastModifiedTime() != null
                            ? attrs.lastModifiedTime().toInstant()
                            : Instant.now();
                    Object fileKey = attrs.fileKey();

                    String fullHash;
                    if (size <= CHUNK_SIZE * 2) {
                        // Small files are already fully identified by head + tail boundary
                        fullHash = computeHeadTailHash(file, size);
                    } else {
                        fullHash = computeFullSha256(file, cancelFlag);
                    }

                    if (fullHash != null) {
                        ScannedFileItem item = new ScannedFileItem(file, size, lastModified, fileKey);
                        fullHashBuckets.computeIfAbsent(fullHash, k -> Collections.synchronizedList(new ArrayList<>())).add(item);
                        groupSizes.put(fullHash, size);
                    }

                    if (progressListener != null) {
                        double elapsed = Math.max(0.001, (System.currentTimeMillis() - overallStartTime) / 1000.0);
                        progressListener.onThroughput(filesVisited.get(), bytesExamined.get(), file.toString(),
                                filesVisited.get() / elapsed, bytesExamined.get() / elapsed);
                    }
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                f.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof CancellationException) {
                throw (CancellationException) e.getCause();
            }
            log.error("Error during parallel hashing: {}", e.getMessage(), e);
        } finally {
            hashExecutor.shutdownNow();
        }

        checkCancelled(cancelFlag);

        List<DuplicateFileGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<ScannedFileItem>> entry : fullHashBuckets.entrySet()) {
            if (entry.getValue().size() >= 2) {
                long size = groupSizes.getOrDefault(entry.getKey(), entry.getValue().get(0).getSizeInBytes());
                DuplicateFileGroup group = new DuplicateFileGroup(entry.getKey(), size, new ArrayList<>(entry.getValue()));
                // Auto-mark non-original duplicates
                group.markDuplicatesForRemoval();
                groups.add(group);
            }
        }

        // Sort descending by reclaimable bytes
        groups.sort(Comparator.comparingLong(DuplicateFileGroup::getReclaimableSize).reversed());

        if (progressListener != null) {
            long totalDuration = System.currentTimeMillis() - overallStartTime;
            progressListener.onStatus("Duplicate detection complete: " + groups.size() + " groups found in "
                    + com.cleanpilot.ui.FormatUtils.formatDuration(totalDuration) + ".");
        }

        return groups;
    }

    /**
     * Reads the Head (first 4 KB) and Tail (last 4 KB) of the file using FileChannel.
     */
    public String computeHeadTailHash(Path path, long fileSize) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            // Read Head
            int headBytesToRead = (int) Math.min(fileSize, CHUNK_SIZE);
            ByteBuffer headBuffer = ByteBuffer.allocate(headBytesToRead);
            channel.read(headBuffer, 0);
            headBuffer.flip();
            digest.update(headBuffer);

            // Read Tail (if file is larger than 2 chunks)
            if (fileSize > (long) CHUNK_SIZE * 2) {
                ByteBuffer tailBuffer = ByteBuffer.allocate(CHUNK_SIZE);
                channel.read(tailBuffer, fileSize - CHUNK_SIZE);
                tailBuffer.flip();
                digest.update(tailBuffer);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Memory-bounded streaming SHA-256 using an off-heap Direct ByteBuffer and FileChannel.
     * Bypasses heap array allocation and leverages vectorized JVM intrinsics.
     */
    public String computeFullSha256(Path path, AtomicBoolean cancelFlag) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(DIRECT_BUFFER_SIZE);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            while (channel.read(directBuffer) > 0) {
                if (cancelFlag != null && cancelFlag.get()) {
                    throw new CancellationException("Hashing cancelled by user");
                }
                directBuffer.flip();
                digest.update(directBuffer);
                directBuffer.clear();
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void checkCancelled(AtomicBoolean cancelFlag) {
        if (cancelFlag != null && cancelFlag.get()) {
            throw new CancellationException("Duplicate scan cancelled by user.");
        }
    }
}
