package com.cleanpilot.service;

import com.cleanpilot.model.ScannedFileItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LargeFileScannerServiceTest {

    private final LargeFileScannerService service = new LargeFileScannerService();

    @Test
    @DisplayName("Should detect only files meeting or exceeding minimum size threshold")
    void testThresholdFiltering(@TempDir Path tempDir) throws IOException {
        Path smallFile = tempDir.resolve("small.txt");
        Path largeFile1 = tempDir.resolve("large1.bin");
        Path largeFile2 = tempDir.resolve("large2.bin");

        Files.write(smallFile, new byte[100]); // 100 bytes
        Files.write(largeFile1, new byte[10_000]); // 10 KB
        Files.write(largeFile2, new byte[25_000]); // 25 KB

        List<ScannedFileItem> items = service.scanLargeFiles(
                tempDir,
                5_000, // threshold 5 KB
                Collections.emptySet(),
                new AtomicBoolean(false),
                null
        );

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getSizeInBytes()).isEqualTo(25_000); // Sorted descending
        assertThat(items.get(1).getSizeInBytes()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("Should skip excluded subdirectories")
    void testExcludedDirectories(@TempDir Path tempDir) throws IOException {
        Path excludedDir = tempDir.resolve("skip_me");
        Files.createDirectories(excludedDir);

        Path fileInsideExcluded = excludedDir.resolve("hidden_large.bin");
        Files.write(fileInsideExcluded, new byte[10_000]);

        Path normalFile = tempDir.resolve("normal_large.bin");
        Files.write(normalFile, new byte[10_000]);

        List<ScannedFileItem> items = service.scanLargeFiles(
                tempDir,
                1_000,
                Set.of(excludedDir.toAbsolutePath().normalize().toString()),
                new AtomicBoolean(false),
                null
        );

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getFileName()).isEqualTo("normal_large.bin");
    }

    @Test
    @DisplayName("Scan cancellation should abort gracefully")
    void testCancellation(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.bin");
        Files.write(file, new byte[500]);

        AtomicBoolean cancel = new AtomicBoolean(true);

        assertThatThrownBy(() -> service.scanLargeFiles(tempDir, 1, Collections.emptySet(), cancel, null))
                .isInstanceOf(CancellationException.class);
    }
}
