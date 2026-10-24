package com.cleanpilot.service;

import com.cleanpilot.model.RecoveryItemDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepRecoveryScannerServiceTest {

    private DeepRecoveryScannerService scannerService;

    @BeforeEach
    void setUp() {
        scannerService = new DeepRecoveryScannerService();
    }

    @Test
    @DisplayName("Should recover file to specified target directory successfully")
    void shouldRecoverFileToDirectory(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("backup_notes.txt.bak");
        Files.writeString(source, "Important backup notes data");

        RecoveryItemDTO item = new RecoveryItemDTO(
                null,
                "backup_notes.txt.bak",
                source.toString(),
                source.toString(),
                Files.size(source),
                LocalDateTime.now(),
                null,
                RecoveryItemDTO.RecoverySource.DEEP_RECOVERY,
                "App Auto-Save / Backup Copy"
        );

        Path exportDir = tempDir.resolve("extracted_files");
        Path destination = scannerService.recoverToDirectory(item, exportDir);

        assertThat(Files.exists(destination)).isTrue();
        assertThat(Files.readString(destination)).isEqualTo("Important backup notes data");
        assertThat(destination.getFileName().toString()).isEqualTo("backup_notes.txt.bak");
    }

    @Test
    @DisplayName("Should avoid overwriting destination on collision by appending incremented suffix")
    void shouldAvoidCollisionOnRecover(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("data.old");
        Files.writeString(source, "Version 2 Old Data");

        Path exportDir = tempDir.resolve("extracted_files");
        Files.createDirectories(exportDir);
        // Create pre-existing file in target directory
        Files.writeString(exportDir.resolve("data.old"), "Pre-existing file in folder");

        RecoveryItemDTO item = new RecoveryItemDTO(
                null,
                "data.old",
                source.toString(),
                source.toString(),
                Files.size(source),
                LocalDateTime.now(),
                null,
                RecoveryItemDTO.RecoverySource.DEEP_RECOVERY,
                "App Auto-Save / Backup Copy"
        );

        Path destination = scannerService.recoverToDirectory(item, exportDir);

        assertThat(Files.exists(destination)).isTrue();
        assertThat(destination.getFileName().toString()).isEqualTo("data_recovered_1.old");
        assertThat(Files.readString(destination)).isEqualTo("Version 2 Old Data");
        // Verify pre-existing file was NOT overwritten
        assertThat(Files.readString(exportDir.resolve("data.old"))).isEqualTo("Pre-existing file in folder");
    }

    @Test
    @DisplayName("Should throw NoSuchFileException if recovery source is missing")
    void shouldThrowWhenSourceDoesNotExist(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("ghost.bak");
        RecoveryItemDTO item = new RecoveryItemDTO(
                null,
                "ghost.bak",
                nonExistent.toString(),
                nonExistent.toString(),
                100L,
                LocalDateTime.now(),
                null,
                RecoveryItemDTO.RecoverySource.DEEP_RECOVERY,
                "App Auto-Save / Backup Copy"
        );

        Path exportDir = tempDir.resolve("extracted_files");
        assertThatThrownBy(() -> scannerService.recoverToDirectory(item, exportDir))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    @DisplayName("Should sanitize path traversal attempts in filename and keep extraction inside target directory")
    void shouldPreventPathTraversalOnRecover(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("test_source.bak");
        Files.writeString(source, "Safe source data");

        Path exportDir = tempDir.resolve("isolated_export");
        Files.createDirectories(exportDir);

        RecoveryItemDTO maliciousItem = new RecoveryItemDTO(
                null,
                "../../escaped_file.txt",
                source.toString(),
                source.toString(),
                Files.size(source),
                LocalDateTime.now(),
                null,
                RecoveryItemDTO.RecoverySource.DEEP_RECOVERY,
                "App Auto-Save / Backup Copy"
        );

        Path destination = scannerService.recoverToDirectory(maliciousItem, exportDir);

        assertThat(destination.toAbsolutePath().normalize().startsWith(exportDir.toAbsolutePath().normalize())).isTrue();
        assertThat(destination.getFileName().toString()).isEqualTo("escaped_file.txt");
        assertThat(Files.exists(exportDir.resolve("escaped_file.txt"))).isTrue();
    }

    @Test
    @DisplayName("Should abort scan gracefully when cancellation flag is raised")
    void shouldAbortScanWhenCancelled() {
        AtomicBoolean cancelFlag = new AtomicBoolean(true);
        List<RecoveryItemDTO> results = scannerService.performDeepRecoveryScan(cancelFlag, null);

        assertThat(results).isNotNull();
    }
}
