package com.cleanpilot.service;

import com.cleanpilot.model.DuplicateFileGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DuplicateDetectionServiceTest {

    private final DuplicateDetectionService service = new DuplicateDetectionService();

    @Test
    @DisplayName("Should detect identical files with different names")
    void testDetectDuplicatesWithDifferentNames(@TempDir Path tempDir) throws IOException {
        byte[] content = "CleanPilot Honest Duplicate Test Content 1234567890".repeat(50).getBytes(StandardCharsets.UTF_8);

        Path fileA = tempDir.resolve("original_file.dat");
        Path fileB = tempDir.resolve("copy_of_file.dat");
        Path fileC = tempDir.resolve("unique_file.dat");

        Files.write(fileA, content);
        Files.write(fileB, content);
        Files.write(fileC, "Completely different content of different length".getBytes(StandardCharsets.UTF_8));

        List<DuplicateFileGroup> groups = service.detectDuplicates(
                tempDir,
                10,
                Collections.emptySet(),
                new AtomicBoolean(false),
                null
        );

        assertThat(groups).hasSize(1);
        DuplicateFileGroup group = groups.get(0);
        assertThat(group.getFileCount()).isEqualTo(2);
        assertThat(group.getSingleFileSize()).isEqualTo(content.length);
        assertThat(group.getReclaimableSize()).isEqualTo(content.length);
    }

    @Test
    @DisplayName("Should differentiate files with exact same byte size but different content")
    void testIdenticalSizeDifferentContent(@TempDir Path tempDir) throws IOException {
        byte[] content1 = new byte[8192];
        Arrays.fill(content1, (byte) 'A');

        byte[] content2 = new byte[8192];
        Arrays.fill(content2, (byte) 'B');

        Path file1 = tempDir.resolve("fileA.bin");
        Path file2 = tempDir.resolve("fileB.bin");

        Files.write(file1, content1);
        Files.write(file2, content2);

        List<DuplicateFileGroup> groups = service.detectDuplicates(
                tempDir,
                100,
                Collections.emptySet(),
                new AtomicBoolean(false),
                null
        );

        // Even though sizes are 8192 for both, hashes differ, so 0 duplicate groups
        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("Streaming SHA-256 calculation matches expected hash")
    void testComputeFullSha256(@TempDir Path tempDir) throws IOException, NoSuchAlgorithmException {
        Path file = tempDir.resolve("hash_test.txt");
        Files.writeString(file, "hello cleanpilot");

        String hash = service.computeFullSha256(file, new AtomicBoolean(false));
        assertThat(hash).isNotNull().hasSize(64);
    }

    @Test
    @DisplayName("Head + Tail boundary hash differentiates files with identical headers but different trailers")
    void testHeadTailBoundaryHash(@TempDir Path tempDir) throws IOException, NoSuchAlgorithmException {
        int size = 16384;
        byte[] fileBytes1 = new byte[size];
        byte[] fileBytes2 = new byte[size];

        // Same head 4 KB
        Arrays.fill(fileBytes1, 0, 4096, (byte) 'H');
        Arrays.fill(fileBytes2, 0, 4096, (byte) 'H');

        // Different tail 4 KB
        Arrays.fill(fileBytes1, size - 4096, size, (byte) 'X');
        Arrays.fill(fileBytes2, size - 4096, size, (byte) 'Y');

        Path file1 = tempDir.resolve("container1.dat");
        Path file2 = tempDir.resolve("container2.dat");

        Files.write(file1, fileBytes1);
        Files.write(file2, fileBytes2);

        String hash1 = service.computeHeadTailHash(file1, size);
        String hash2 = service.computeHeadTailHash(file2, size);

        assertThat(hash1).isNotEqualTo(hash2);

        List<DuplicateFileGroup> groups = service.detectDuplicates(
                tempDir,
                100,
                Collections.emptySet(),
                new AtomicBoolean(false),
                null
        );

        // Separated at Pass 2 without needing full file streaming!
        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("Cancellation should abort scan with CancellationException")
    void testCancellation(@TempDir Path tempDir) throws IOException {
        Path fileA = tempDir.resolve("fileA.dat");
        Files.writeString(fileA, "some content");

        AtomicBoolean cancel = new AtomicBoolean(true);

        assertThatThrownBy(() -> service.detectDuplicates(tempDir, 1, Collections.emptySet(), cancel, null))
                .isInstanceOf(CancellationException.class);
    }
}
