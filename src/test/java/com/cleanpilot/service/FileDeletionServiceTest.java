package com.cleanpilot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class FileDeletionServiceTest {

    private final FileDeletionService service = new FileDeletionService();

    @Test
    @DisplayName("Non existent path should return failure")
    void testNonExistentFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.txt");
        FileDeletionService.DeletionResult result = service.deleteFile(missing, false, true);

        assertThat(result.success()).isFalse();
        assertThat(result.freedBytes()).isEqualTo(0);
    }

    @Test
    @DisplayName("Permanent deletion deletes file and reports freed bytes accurately")
    void testPermanentDeletion(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("to_delete.txt");
        Files.write(file, new byte[1024]);

        FileDeletionService.DeletionResult result = service.deleteFile(file, false, true);

        assertThat(result.success()).isTrue();
        assertThat(result.freedBytes()).isEqualTo(1024);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    @DisplayName("isSafeDeletablePath must reject critical system paths and root drives")
    void testIsSafeDeletablePathRejections() {
        // Null
        assertThat(service.isSafeDeletablePath(null)).isFalse();

        // Drive roots
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\"))).isFalse();
        assertThat(service.isSafeDeletablePath(Paths.get("D:\\"))).isFalse();

        // Windows directory and subdirectories
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\Windows"))).isFalse();
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\Windows\\System32"))).isFalse();
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\Windows\\System32\\ntoskrnl.exe"))).isFalse();

        // Program Files
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\Program Files"))).isFalse();
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\Program Files (x86)"))).isFalse();

        // User Home root itself
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            assertThat(service.isSafeDeletablePath(Paths.get(userHome))).isFalse();
        }

        // Critical OS boot/paging files
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\pagefile.sys"))).isFalse();
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\swapfile.sys"))).isFalse();
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\hiberfil.sys"))).isFalse();
        assertThat(service.isSafeDeletablePath(Paths.get("C:\\bootmgr"))).isFalse();
    }

    @Test
    @DisplayName("deleteFile and quarantineFile must abort when targeting a protected system path")
    void testRefusesDeletionOfProtectedSystemPath() {
        Path winPath = Paths.get("C:\\Windows\\System32");
        FileDeletionService.DeletionResult delRes = service.deleteFile(winPath, true, true);
        assertThat(delRes.success()).isFalse();
        assertThat(delRes.message()).contains("SAFETY VIOLATION");

        FileDeletionService.DeletionResult quarRes = service.quarantineFile(winPath);
        assertThat(quarRes.success()).isFalse();
        assertThat(quarRes.message()).contains("SAFETY VIOLATION");
    }

    @Test
    @DisplayName("isSafeDeletablePath accepts safe temporary files with sufficient depth")
    void testIsSafeDeletablePathAcceptsSafeFiles(@TempDir Path tempDir) throws IOException {
        Path subDir = tempDir.resolve("subfolder");
        Files.createDirectories(subDir);
        Path safeFile = subDir.resolve("junk_file.tmp");
        Files.writeString(safeFile, "temporary content");

        assertThat(service.isSafeDeletablePath(safeFile)).isTrue();
    }
}