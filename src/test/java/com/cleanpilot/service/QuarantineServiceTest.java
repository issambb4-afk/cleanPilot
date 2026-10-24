package com.cleanpilot.service;

import com.cleanpilot.model.QuarantinedItem;
import com.cleanpilot.repository.QuarantineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuarantineServiceTest {

    @Mock
    private QuarantineRepository repository;

    private QuarantineService quarantineService;
    private Path vaultDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        vaultDir = tempDir.resolve("quarantine_vault");
        quarantineService = new QuarantineService(repository, vaultDir);

        lenient().when(repository.save(any(QuarantinedItem.class))).thenAnswer(invocation -> {
            QuarantinedItem item = invocation.getArgument(0);
            item.setId(1L);
            return item;
        });
    }

    @Test
    @DisplayName("Should quarantine file, move from source, and persist metadata")
    void shouldQuarantineFile(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("test_document.txt");
        Files.writeString(sourceFile, "Hello Quarantine World!");

        QuarantinedItem item = quarantineService.quarantineFile(sourceFile);

        assertThat(item).isNotNull();
        assertThat(item.getFileName()).isEqualTo("test_document.txt");
        assertThat(item.getFileSizeBytes()).isEqualTo(Files.size(Paths.get(item.getQuarantinePath())));
        assertThat(Files.exists(sourceFile)).isFalse();
        assertThat(Files.exists(Paths.get(item.getQuarantinePath()))).isTrue();
        verify(repository, times(1)).save(any(QuarantinedItem.class));
    }

    @Test
    @DisplayName("Should restore quarantined item back to original directory")
    void shouldRestoreQuarantinedItem(@TempDir Path tempDir) throws IOException {
        Path originalPath = tempDir.resolve("original_folder").resolve("restore_me.txt");
        Files.createDirectories(originalPath.getParent());
        Files.writeString(originalPath, "Content to be restored");

        QuarantinedItem item = quarantineService.quarantineFile(originalPath);
        assertThat(Files.exists(originalPath)).isFalse();

        when(repository.findById(1L)).thenReturn(Optional.of(item));

        Path restoredPath = quarantineService.restoreItem(1L);

        assertThat(restoredPath).isEqualTo(originalPath);
        assertThat(Files.exists(originalPath)).isTrue();
        assertThat(Files.readString(originalPath)).isEqualTo("Content to be restored");
        assertThat(Files.exists(Paths.get(item.getQuarantinePath()))).isFalse();
        verify(repository, times(1)).delete(item);
    }

    @Test
    @DisplayName("Should handle collision during restore by appending _restored without overwriting")
    void shouldHandleCollisionDuringRestore(@TempDir Path tempDir) throws IOException {
        Path originalPath = tempDir.resolve("conflict.txt");
        Files.writeString(originalPath, "Original content in quarantine");

        QuarantinedItem item = quarantineService.quarantineFile(originalPath);

        // Create a new conflicting file at original destination
        Files.writeString(originalPath, "Brand new conflicting file");

        when(repository.findById(1L)).thenReturn(Optional.of(item));

        Path restoredPath = quarantineService.restoreItem(1L);

        assertThat(restoredPath).isNotEqualTo(originalPath);
        assertThat(restoredPath.getFileName().toString()).isEqualTo("conflict_restored.txt");
        assertThat(Files.exists(originalPath)).isTrue();
        assertThat(Files.readString(originalPath)).isEqualTo("Brand new conflicting file");
        assertThat(Files.readString(restoredPath)).isEqualTo("Original content in quarantine");
    }

    @Test
    @DisplayName("Should throw FileNotFoundException gracefully if file is missing from vault")
    void shouldHandleMissingVaultFile(@TempDir Path tempDir) {
        QuarantinedItem item = new QuarantinedItem(
                tempDir.resolve("missing.txt").toString(),
                tempDir.resolve("vault_missing.txt").toString(),
                "missing.txt",
                100L,
                "fakehash",
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        item.setId(99L);

        when(repository.findById(99L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> quarantineService.restoreItem(99L))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("missing from vault");

        verify(repository, times(1)).delete(item);
    }

    @Test
    @DisplayName("Should purge expired items older than retention threshold")
    void shouldPurgeExpiredItems(@TempDir Path tempDir) throws IOException {
        Path vaultFile = vaultDir.resolve("expired.txt");
        Files.writeString(vaultFile, "expired content");

        QuarantinedItem expiredItem = new QuarantinedItem(
                tempDir.resolve("expired.txt").toString(),
                vaultFile.toString(),
                "expired.txt",
                15L,
                "fakehash",
                LocalDateTime.now().minusDays(35),
                LocalDateTime.now().minusDays(5)
        );
        expiredItem.setId(10L);

        when(repository.findExpiredItems(any(LocalDateTime.class))).thenReturn(List.of(expiredItem));

        int purged = quarantineService.purgeExpired();

        assertThat(purged).isEqualTo(1);
        assertThat(Files.exists(vaultFile)).isFalse();
        verify(repository, times(1)).delete(expiredItem);
    }

    @Test
    @DisplayName("Should refuse deleting physical files that reside outside the quarantine vault")
    void shouldRefuseDeletingPhysicalFileOutsideVaultDirectory(@TempDir Path tempDir) throws IOException {
        Path externalFile = tempDir.resolve("external_system_file.txt");
        Files.writeString(externalFile, "Critical data outside vault");

        QuarantinedItem forgedItem = new QuarantinedItem(
                "C:\\important.txt",
                externalFile.toString(), // points outside vaultDir!
                "important.txt",
                100L,
                "fakehash",
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30)
        );
        forgedItem.setId(42L);

        when(repository.findById(42L)).thenReturn(Optional.of(forgedItem));

        boolean res = quarantineService.permanentlyDeleteItem(42L);

        assertThat(res).isTrue();
        // External file outside vault must NOT be deleted!
        assertThat(Files.exists(externalFile)).isTrue();
        verify(repository, times(1)).delete(forgedItem);
    }
}
