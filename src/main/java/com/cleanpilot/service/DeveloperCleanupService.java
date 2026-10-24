package com.cleanpilot.service;

import com.cleanpilot.model.DeveloperArtifactInfo;
import com.cleanpilot.model.DeveloperArtifactInfo.ArtifactType;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.util.ProcessExecutionUtils;
import com.cleanpilot.util.WindowsSecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Service for scanning, sizing, and cleaning heavy developer artifacts:
 * - node_modules
 * - Gradle / Maven local caches
 * - Rust target/ directories
 * - Python virtual environments (.venv)
 * - WSL2 Virtual Hard Disks (ext4.vhdx) with diskpart compaction
 */
@Service
public class DeveloperCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DeveloperCleanupService.class);

    private final OperationLifecycleManager lifecycleManager;

    public DeveloperCleanupService() {
        this(null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DeveloperCleanupService(@org.springframework.context.annotation.Lazy OperationLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }

    /**
     * Resolves default locations to search for developer workspaces and caches.
     */
    public List<Path> getDefaultSearchRoots() {
        List<Path> roots = new ArrayList<>();
        String userHome = System.getProperty("user.home");

        // 1. Common workspace directories
        String[] candidatePaths = {
                "c:\\projects", "c:\\dev", "c:\\workspace", "c:\\code", "c:\\repos",
                userHome + "\\projects", userHome + "\\dev", userHome + "\\workspace",
                userHome + "\\IdeaProjects", userHome + "\\source\\repos"
        };

        for (String p : candidatePaths) {
            Path path = Paths.get(p);
            if (Files.exists(path) && Files.isDirectory(path)) {
                roots.add(path);
            }
        }

        // Always check current working directory if not already included
        Path currentDir = Paths.get(".").toAbsolutePath().normalize();
        if (Files.exists(currentDir) && roots.stream().noneMatch(currentDir::startsWith)) {
            roots.add(currentDir);
        }

        return roots;
    }

    /**
     * Scans for developer artifacts across search roots and caches.
     */
    public List<DeveloperArtifactInfo> scanDeveloperArtifacts(List<Path> searchRoots, AtomicBoolean cancelFlag, BiConsumer<String, Double> progress) {
        List<DeveloperArtifactInfo> results = new ArrayList<>();

        if (searchRoots == null || searchRoots.isEmpty()) {
            searchRoots = getDefaultSearchRoots();
        }

        double totalSteps = searchRoots.size() + 3.0; // roots + npm cache + pip cache + WSL
        double currentStep = 0.0;

        // 1. Scan global package manager caches
        if (progress != null) progress.accept("Scanning package manager caches (npm, yarn, pip)...", ++currentStep / totalSteps);
        scanGlobalCaches(results);

        // 2. Scan WSL2 Virtual Hard Disks
        if (progress != null) progress.accept("Scanning WSL2 Virtual Disks...", ++currentStep / totalSteps);
        scanWslDisks(results);

        // 3. Scan workspace directories
        for (Path root : searchRoots) {
            if (cancelFlag != null && cancelFlag.get()) break;
            if (progress != null) progress.accept("Scanning workspaces in " + root + "...", ++currentStep / totalSteps);
            scanProjectTree(root, results, cancelFlag);
        }

        if (progress != null) progress.accept("Scan complete!", 1.0);
        return results;
    }

    public List<DeveloperArtifactInfo> scanDeveloperArtifacts(List<Path> searchRoots, BiConsumer<String, Double> progress, AtomicBoolean cancelFlag) {
        return scanDeveloperArtifacts(searchRoots, cancelFlag, progress);
    }

    private void scanGlobalCaches(List<DeveloperArtifactInfo> results) {
        String userHome = System.getProperty("user.home");
        String localAppData = System.getenv("LOCALAPPDATA");

        // npm cache (%LocalAppData%\npm-cache)
        if (localAppData != null) {
            Path npmCache = Paths.get(localAppData, "npm-cache");
            addDirectoryArtifact(npmCache, npmCache, "npm cache", ArtifactType.BUILD_OUTPUT, results);
        }

        // Yarn cache (%LocalAppData%\Yarn\Cache)
        if (localAppData != null) {
            Path yarnCache = Paths.get(localAppData, "Yarn", "Cache");
            addDirectoryArtifact(yarnCache, yarnCache, "Yarn cache", ArtifactType.BUILD_OUTPUT, results);
        }

        // Pip cache (%LocalAppData%\pip\cache)
        if (localAppData != null) {
            Path pipCache = Paths.get(localAppData, "pip", "cache");
            addDirectoryArtifact(pipCache, pipCache, "Pip cache", ArtifactType.BUILD_OUTPUT, results);
        }

        // Gradle global cache (~/.gradle/caches)
        if (userHome != null) {
            Path gradleCache = Paths.get(userHome, ".gradle", "caches");
            addDirectoryArtifact(gradleCache, gradleCache, "Gradle cache", ArtifactType.GRADLE_CACHE, results);
        }

        // Cargo / Rust cache (~/.cargo/registry)
        if (userHome != null) {
            Path cargoCache = Paths.get(userHome, ".cargo", "registry");
            addDirectoryArtifact(cargoCache, cargoCache, "Cargo registry cache", ArtifactType.BUILD_OUTPUT, results);
        }
    }

    private void scanWslDisks(List<DeveloperArtifactInfo> results) {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) return;

        Path packagesDir = Paths.get(localAppData, "Packages");
        if (Files.exists(packagesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(packagesDir)) {
                for (Path pkg : stream) {
                    Path vhdx = pkg.resolve("LocalState\\ext4.vhdx");
                    if (Files.exists(vhdx)) {
                        try {
                            long size = Files.size(vhdx);
                            BasicFileAttributes attrs = Files.readAttributes(vhdx, BasicFileAttributes.class);
                            LocalDateTime modified = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

                            results.add(new DeveloperArtifactInfo(
                                    "WSL2 Virtual Disk (" + pkg.getFileName().toString().split("_")[0] + ")",
                                    ArtifactType.WSL2_VHDX,
                                    vhdx,
                                    pkg,
                                    size,
                                    1,
                                    modified
                            ));
                        } catch (Exception ex) {
                            log.debug("Could not read WSL vhdx attributes: {}", ex.getMessage());
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Error scanning Packages directory for WSL: {}", ex.getMessage());
            }
        }
    }

    private void scanProjectTree(Path root, List<DeveloperArtifactInfo> results, AtomicBoolean cancelFlag) {
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 6, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (cancelFlag != null && cancelFlag.get()) {
                        return FileVisitResult.TERMINATE;
                    }

                    // Reparse point safety: Never follow directory junctions or symbolic links
                    if (!dir.equals(root) && (attrs.isSymbolicLink() || attrs.isOther() || Files.isSymbolicLink(dir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";

                    // Skip VCS and IDE internal index folders
                    if (".git".equals(dirName) || ".svn".equals(dirName) || ".hg".equals(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    Path parent = dir.getParent();

                    // Check Node.js node_modules
                    if ("node_modules".equals(dirName) && parent != null && Files.exists(parent.resolve("package.json"))) {
                        addDirectoryArtifact(dir, parent, "node_modules", ArtifactType.NODE_MODULES, results);
                        return FileVisitResult.SKIP_SUBTREE; // Don't scan nested node_modules
                    }

                    // Check Rust target/
                    if ("target".equals(dirName) && parent != null && Files.exists(parent.resolve("Cargo.toml"))) {
                        addDirectoryArtifact(dir, parent, "target (Rust build)", ArtifactType.RUST_TARGET, results);
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // Check Gradle / Maven build outputs
                    if ("build".equals(dirName) && parent != null &&
                            (Files.exists(parent.resolve("build.gradle")) || Files.exists(parent.resolve("build.gradle.kts")) || Files.exists(parent.resolve("pom.xml")))) {
                        addDirectoryArtifact(dir, parent, "build (Java build output)", ArtifactType.BUILD_OUTPUT, results);
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // Check Python virtual environment (.venv / venv)
                    if ((".venv".equals(dirName) || "venv".equals(dirName)) && parent != null) {
                        Path pyvenv = dir.resolve("pyvenv.cfg");
                        if (Files.exists(pyvenv) || Files.exists(parent.resolve("requirements.txt")) || Files.exists(parent.resolve("pyproject.toml"))) {
                            addDirectoryArtifact(dir, parent, dirName + " (Python venv)", ArtifactType.PYTHON_VENV, results);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ex) {
            log.debug("Error walking project tree {}: {}", root, ex.getMessage());
        }
    }

    private void addDirectoryArtifact(Path dir, Path projectRoot, String name, ArtifactType type, List<DeveloperArtifactInfo> results) {
        try {
            AtomicLong totalBytes = new AtomicLong(0);
            AtomicInteger totalFiles = new AtomicInteger(0);
            AtomicLong newestTimestamp = new AtomicLong(0);

            Files.walkFileTree(dir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path subDir, BasicFileAttributes attrs) {
                    if (!subDir.equals(dir) && (attrs.isSymbolicLink() || attrs.isOther() || Files.isSymbolicLink(subDir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    totalBytes.addAndGet(attrs.size());
                    totalFiles.incrementAndGet();
                    long mod = attrs.lastModifiedTime().toMillis();
                    if (mod > newestTimestamp.get()) {
                        newestTimestamp.set(mod);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            long millis = newestTimestamp.get() > 0 ? newestTimestamp.get() : System.currentTimeMillis();
            LocalDateTime modDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());

            // Report any detected developer artifact directory
            if (totalBytes.get() >= 0) {
                results.add(new DeveloperArtifactInfo(
                        name,
                        type,
                        dir,
                        projectRoot,
                        totalBytes.get(),
                        totalFiles.get(),
                        modDate
                ));
            }
        } catch (Exception ex) {
            log.debug("Failed to calculate size for developer artifact {}: {}", dir, ex.getMessage());
        }
    }

    /**
     * Validates that the artifact directory is a legitimate developer target and not a system directory.
     */
    public boolean isSafeArtifactPath(Path path) {
        if (path == null) return false;
        Path normalized = path.toAbsolutePath().normalize();

        // Must have at least 3 path segments (e.g. C:\projects\myapp\node_modules)
        if (normalized.getNameCount() < 3) {
            return false;
        }

        String pathStr = normalized.toString().toLowerCase(Locale.ROOT);

        // Reject drive roots directly
        if (normalized.getParent() == null) {
            return false;
        }

        // Reject Windows system directory and subdirectories
        String sysRoot = System.getenv("SystemRoot");
        if (sysRoot == null || sysRoot.isBlank()) sysRoot = "C:\\Windows";
        String normSysRoot = Path.of(sysRoot).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
        if (pathStr.equals(normSysRoot) || pathStr.startsWith(normSysRoot + File.separator)) {
            return false;
        }

        // Reject Program Files roots
        String pf = System.getenv("ProgramFiles");
        if (pf != null) {
            String normPf = Path.of(pf).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (pathStr.equals(normPf) || pathStr.startsWith(normPf + File.separator)) {
                return false;
            }
        }
        String pfx86 = System.getenv("ProgramFiles(x86)");
        if (pfx86 != null) {
            String normPfx86 = Path.of(pfx86).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (pathStr.equals(normPfx86) || pathStr.startsWith(normPfx86 + File.separator)) {
                return false;
            }
        }

        // Reject User Home directory root directly
        String userHome = System.getProperty("user.home");
        if (userHome != null && pathStr.equals(Path.of(userHome).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT))) {
            return false;
        }

        // Name must match an expected developer artifact directory or .vhdx file
        Path fileName = normalized.getFileName();
        if (fileName == null) return false;
        String name = fileName.toString().toLowerCase(Locale.ROOT);
        return name.equals("node_modules") || name.equals("target") || name.equals("build")
                || name.equals(".venv") || name.equals("venv") || name.equals(".gradle")
                || name.equals(".m2") || name.equals(".cargo") || name.equals("cache")
                || name.equals("npm-cache") || name.endsWith(".vhdx");
    }

    /**
     * Safely deletes an artifact directory recursively with strict reparse-point protection.
     */
    public boolean deleteArtifact(DeveloperArtifactInfo artifact) {
        if (artifact == null || artifact.getPath() == null) return false;
        Path p = artifact.getPath().toAbsolutePath().normalize();
        if (!Files.exists(p)) return true;

        if (!isSafeArtifactPath(p)) {
            log.error("SAFETY VIOLATION: Refusing to delete non-developer artifact path: {}", p);
            return false;
        }

        String opId = lifecycleManager != null ?
                lifecycleManager.startOperation("DEVELOPER_CLEANUP", RiskClassification.MODERATE_RISK, p.toString()) : null;
        if (opId != null) lifecycleManager.beginExecution(opId);

        try {
            long size = artifact.getSizeBytes();
            if (Files.isRegularFile(p)) {
                Files.delete(p);
                if (opId != null) {
                    lifecycleManager.recordItemProgress(opId, p.toString(), true, size, "Artifact file deleted");
                    lifecycleManager.completeOperation(opId, size, Collections.emptyList());
                }
                return true;
            }

            Files.walkFileTree(p, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // Reparse point / junction safety: never enter junction targets; unlink the junction directly
                    if (!dir.equals(p) && (attrs.isSymbolicLink() || attrs.isOther() || Files.isSymbolicLink(dir))) {
                        Files.delete(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });

            if (opId != null) {
                lifecycleManager.recordItemProgress(opId, p.toString(), true, size, "Artifact folder deleted");
                lifecycleManager.completeOperation(opId, size, Collections.emptyList());
            }
            return true;
        } catch (Exception ex) {
            log.error("Failed to delete developer artifact {}: {}", p, ex.getMessage(), ex);
            if (opId != null) {
                lifecycleManager.recordItemProgress(opId, p.toString(), false, 0, ex.getMessage());
                lifecycleManager.failOperation(opId, ex.getMessage(), ex);
            }
            return false;
        }
    }

    /**
     * Executes WSL shutdown and compacts a WSL2 virtual disk (ext4.vhdx) via diskpart.
     * Returns the number of bytes saved (or -1 on failure).
     */
    public long compactWslDisk(Path vhdxPath) {
        if (vhdxPath == null || !Files.exists(vhdxPath) || !vhdxPath.toString().endsWith(".vhdx")) {
            return -1;
        }

        File script = null;
        try {
            long sizeBefore = Files.size(vhdxPath);

            // 1. Shutdown WSL to release locks
            ProcessBuilder wslShutdown = new ProcessBuilder("wsl.exe", "--shutdown");
            ProcessExecutionUtils.execute(wslShutdown, 15);

            // 2. Create diskpart compact script
            script = File.createTempFile("cleanpilot_diskpart_", ".txt");
            String diskpartContent = String.format(
                    "select vdisk file=\"%s\"\nattach vdisk readonly\ncompact vdisk\ndetach vdisk\nexit\n",
                    vhdxPath.toAbsolutePath().toString().replace("\\", "\\\\")
            );
            Files.writeString(script.toPath(), diskpartContent);

            // 3. Execute diskpart
            if (WindowsSecurityUtils.isRunningAsAdmin()) {
                ProcessBuilder pb = new ProcessBuilder("diskpart.exe", "/s", script.getAbsolutePath());
                ProcessExecutionUtils.execute(pb, 60);
            } else {
                WindowsSecurityUtils.runElevated("diskpart.exe /s \"" + script.getAbsolutePath() + "\"");
            }

            long sizeAfter = Files.size(vhdxPath);
            return Math.max(0, sizeBefore - sizeAfter);
        } catch (Exception ex) {
            log.error("Failed to compact WSL2 virtual disk {}: {}", vhdxPath, ex.getMessage(), ex);
            return -1;
        } finally {
            if (script != null && script.exists()) {
                try {
                    Files.deleteIfExists(script.toPath());
                } catch (Exception ignored) {}
            }
        }
    }
}