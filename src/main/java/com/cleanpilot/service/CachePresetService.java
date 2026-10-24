package com.cleanpilot.service;

import com.cleanpilot.model.CachePresetItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Discovers and inspects safe application and developer cache presets
 * (Gradle, Maven, npm/yarn, pip, Docker build cache, IDE system logs/indexes, OS temp).
 */
@Service
public class CachePresetService {

    private static final Logger log = LoggerFactory.getLogger(CachePresetService.class);

    private record PresetDefinition(String id, String name, String category, String pathTemplate, String description) {}

    private final List<PresetDefinition> definitions = List.of(
            new PresetDefinition("gradle-cache", "Gradle Dependency & Wrapper Cache", "Developer Caches",
                    "${USER_HOME}/.gradle/caches", "Safe to remove. Gradle re-downloads dependencies on next build."),
            new PresetDefinition("maven-cache", "Maven Local Repository Cache", "Developer Caches",
                    "${USER_HOME}/.m2/repository", "Safe to clear. Maven re-populates missing artifacts on demand."),
            new PresetDefinition("npm-cache", "npm Global Cache", "Developer Caches",
                    "${USER_HOME}/AppData/Local/npm-cache", "Safe to clean. npm re-fetches packages if needed."),
            new PresetDefinition("yarn-cache", "Yarn Package Cache", "Developer Caches",
                    "${USER_HOME}/AppData/Local/Yarn/Cache", "Safe to clear. Packages will be downloaded when building."),
            new PresetDefinition("pip-cache", "Python Pip Download Cache", "Developer Caches",
                    "${USER_HOME}/AppData/Local/pip/cache", "Safe to delete. Pip will re-download wheels if needed."),
            new PresetDefinition("vscode-cache", "VS Code Crash & Cache Logs", "IDE & Editor Caches",
                    "${USER_HOME}/AppData/Roaming/Code/Cache", "Purges temporary editor workspace caches without touching settings."),
            new PresetDefinition("user-temp", "Windows User Temp Directory", "System Temp",
                    "${USER_HOME}/AppData/Local/Temp", "Temporary scratch files created by installers and running applications."),
            new PresetDefinition("delivery-opt", "Windows Delivery Optimization Cache", "System Temp",
                    "C:/Windows/SoftwareDistribution/DeliveryOptimization", "Residual peer-to-peer Windows Update cached blocks.")
    );

    /**
     * Scans for all predefined cache locations and computes their disk footprint.
     */
    public List<CachePresetItem> scanPresets() {
        String userHome = System.getProperty("user.home", "");
        List<CachePresetItem> result = new ArrayList<>();

        for (PresetDefinition def : definitions) {
            String resolved = def.pathTemplate().replace("${USER_HOME}", userHome).replace("/", "\\");
            Path path = Paths.get(resolved);

            if (Files.exists(path) && Files.isDirectory(path)) {
                try {
                    AtomicLong size = new AtomicLong(0);
                    AtomicInteger count = new AtomicInteger(0);

                    Files.walkFileTree(path, EnumSet.noneOf(FileVisitOption.class), 6, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            size.addAndGet(attrs.size());
                            count.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });

                    result.add(new CachePresetItem(
                            def.id(),
                            def.name(),
                            def.category(),
                            path,
                            def.description(),
                            size.get(),
                            count.get(),
                            true
                    ));
                } catch (Exception e) {
                    log.warn("Could not calculate size for cache preset {}: {}", def.id(), e.getMessage());
                }
            }
        }

        return result;
    }
}
