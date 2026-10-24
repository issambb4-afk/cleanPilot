package com.cleanpilot.model;

import java.nio.file.Path;

/**
 * Represents a pre-configured, safe cleanup preset for development caches, package manager caches,
 * or application temp data that safe-cleaners frequently purge.
 *
 * @param id Unique identifier (e.g. "gradle-cache")
 * @param name Display title (e.g. "Gradle Dependency & Build Cache")
 * @param category Category grouping (e.g. "Developer Tools", "Package Managers", "System Temp")
 * @param path System path where the cache resides
 * @param description Safety explanation of what is cleaned and why it is safe
 * @param calculatedSizeBytes Total size in bytes calculated on disk
 * @param fileCount Number of files inside the cache directory
 * @param isDetected Whether the directory actually exists on this laptop
 */
public record CachePresetItem(
        String id,
        String name,
        String category,
        Path path,
        String description,
        long calculatedSizeBytes,
        int fileCount,
        boolean isDetected
) {

    public String formattedSize() {
        return com.cleanpilot.ui.FormatUtils.formatBytes(calculatedSizeBytes);
    }
}
