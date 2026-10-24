package com.cleanpilot.service;

import com.cleanpilot.model.DeveloperArtifactInfo;
import com.cleanpilot.model.DeveloperArtifactInfo.ArtifactType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DeveloperCleanupServiceTest {

    private final DeveloperCleanupService service = new DeveloperCleanupService();

    @Test
    void testGetDefaultSearchRootsReturnsNonEmpty() {
        List<Path> roots = service.getDefaultSearchRoots();
        assertThat(roots).isNotNull().isNotEmpty();
    }

    @Test
    void testScanAndCleanDeveloperArtifacts(@TempDir Path tempDir) throws IOException {
        // Project 1: Node.js project
        Path nodeProject = tempDir.resolve("my-web-app");
        Files.createDirectories(nodeProject);
        Files.writeString(nodeProject.resolve("package.json"), "{\"name\": \"my-app\"}");

        Path nodeModules = nodeProject.resolve("node_modules");
        Files.createDirectories(nodeModules.resolve("react"));
        Files.writeString(nodeModules.resolve("react").resolve("index.js"), "console.log('dummy content for size calculation testing');".repeat(50));

        // Project 2: Rust project
        Path rustProject = tempDir.resolve("my-rust-cli");
        Files.createDirectories(rustProject);
        Files.writeString(rustProject.resolve("Cargo.toml"), "[package]\nname = \"cli\"");

        Path rustTarget = rustProject.resolve("target");
        Files.createDirectories(rustTarget.resolve("release"));
        Files.writeString(rustTarget.resolve("release").resolve("cli.exe"), "binary data simulation".repeat(200));

        // Scan the temporary workspace
        List<DeveloperArtifactInfo> artifacts = service.scanDeveloperArtifacts(
                List.of(tempDir),
                new AtomicBoolean(false),
                (msg, pct) -> {}
        );

        // Verify detection
        assertThat(artifacts).hasSizeGreaterThanOrEqualTo(2);
        assertThat(artifacts).anyMatch(a -> a.getType() == ArtifactType.NODE_MODULES && a.getName().equals("node_modules"));
        assertThat(artifacts).anyMatch(a -> a.getType() == ArtifactType.RUST_TARGET && a.getName().contains("target"));

        // Test deletion
        DeveloperArtifactInfo nodeArtifact = artifacts.stream()
                .filter(a -> a.getType() == ArtifactType.NODE_MODULES)
                .findFirst()
                .orElseThrow();

        boolean deleted = service.deleteArtifact(nodeArtifact);
        assertThat(deleted).isTrue();
        assertThat(Files.exists(nodeModules)).isFalse();
        assertThat(Files.exists(nodeProject.resolve("package.json"))).isTrue(); // Project root untouched
    }

    @Test
    @DisplayName("isSafeArtifactPath must reject root drives, OS folders, and non-developer folders")
    void testIsSafeArtifactPath() {
        assertThat(service.isSafeArtifactPath(null)).isFalse();
        assertThat(service.isSafeArtifactPath(Paths.get("C:\\"))).isFalse();
        assertThat(service.isSafeArtifactPath(Paths.get("C:\\Windows"))).isFalse();
        assertThat(service.isSafeArtifactPath(Paths.get("C:\\Windows\\System32"))).isFalse();
        assertThat(service.isSafeArtifactPath(Paths.get("C:\\Program Files\\node_modules"))).isFalse();

        // Random non-developer folder
        assertThat(service.isSafeArtifactPath(Paths.get("C:\\projects\\app\\important_secrets"))).isFalse();

        // Valid developer paths
        assertThat(service.isSafeArtifactPath(Paths.get("C:\\projects\\app\\node_modules"))).isTrue();
        assertThat(service.isSafeArtifactPath(Paths.get("C:\\projects\\app\\target"))).isTrue();
        assertThat(service.isSafeArtifactPath(Paths.get("C:\\projects\\app\\build"))).isTrue();
        assertThat(service.isSafeArtifactPath(Paths.get("C:\\projects\\app\\.venv"))).isTrue();
    }

    @Test
    @DisplayName("deleteArtifact must refuse deletion of unsafe paths")
    void testDeleteArtifactRefusesUnsafePath() {
        DeveloperArtifactInfo unsafeInfo = new DeveloperArtifactInfo(
                "System32",
                ArtifactType.BUILD_OUTPUT,
                Paths.get("C:\\Windows\\System32"),
                Paths.get("C:\\Windows"),
                1000,
                10,
                LocalDateTime.now()
        );

        boolean result = service.deleteArtifact(unsafeInfo);
        assertThat(result).isFalse();
    }
}