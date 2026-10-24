package com.cleanpilot.ui;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * Utility for formatting filesystem metrics, sizes, dates, throughput, and file categorization.
 */
public final class FormatUtils {

    private static final String[] BYTE_UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};
    private static final java.text.DecimalFormatSymbols ROOT_SYMBOLS = new java.text.DecimalFormatSymbols(Locale.ROOT);
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.00", ROOT_SYMBOLS);
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,##0", ROOT_SYMBOLS);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg", "3gp", "ts"
    );

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "aiff", "opus"
    );

    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
            "zip", "tar", "gz", "tgz", "bz2", "7z", "rar", "xz", "iso", "img", "dmg", "pkg"
    );

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "rtf", "csv", "md"
    );

    private static final Set<String> EXECUTABLE_EXTENSIONS = Set.of(
            "exe", "msi", "dll", "bin", "app", "deb", "rpm", "bat", "cmd", "sh", "jar"
    );

    private static final Set<String> DEV_ARTIFACT_DIRS = Set.of(
            "node_modules", "target", ".gradle", ".m2", ".venv", "__pycache__", ".cargo", "vendor", ".cache"
    );

    private FormatUtils() {
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        exp = Math.min(exp, BYTE_UNITS.length - 1);
        double value = bytes / Math.pow(1024, exp);
        return SIZE_FORMAT.format(value) + " " + BYTE_UNITS[exp];
    }

    public static String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            return "0 B/s";
        }
        return formatBytes((long) bytesPerSecond) + "/s";
    }

    public static String formatThroughput(double filesPerSecond) {
        return formatNumber((long) filesPerSecond) + " files/s";
    }

    public static String formatNumber(long number) {
        return INTEGER_FORMAT.format(number);
    }

    public static String formatPercentage(double fraction) {
        double pct = Math.max(0.0, Math.min(100.0, fraction * 100.0));
        return String.format(Locale.ROOT, "%.1f%%", pct);
    }

    public static String formatDouble(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    public static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        double seconds = millis / 1000.0;
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%.1f s", seconds);
        }
        long minutes = (long) (seconds / 60);
        long remainingSec = (long) (seconds % 60);
        return String.format(Locale.ROOT, "%d m %d s", minutes, remainingSec);
    }

    public static String formatInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATE_FORMATTER);
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    public static boolean isDeveloperArtifact(Path path) {
        if (path == null) return false;
        String normalized = path.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
        for (String devDir : DEV_ARTIFACT_DIRS) {
            if (normalized.contains("/" + devDir + "/") || normalized.contains("/" + devDir + "\\")) {
                return true;
            }
        }
        return false;
    }

    public static String categorize(String extension) {
        return categorize(extension, null);
    }

    public static String categorize(String extension, Path path) {
        if (path != null && isDeveloperArtifact(path)) {
            return "Developer Artifacts";
        }
        if (extension == null || extension.isBlank()) {
            return "Other";
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        if (VIDEO_EXTENSIONS.contains(ext)) {
            return "Video";
        }
        if (AUDIO_EXTENSIONS.contains(ext)) {
            return "Audio";
        }
        if (ARCHIVE_EXTENSIONS.contains(ext)) {
            return "Archive / Image";
        }
        if (DOCUMENT_EXTENSIONS.contains(ext)) {
            return "Document";
        }
        if (EXECUTABLE_EXTENSIONS.contains(ext)) {
            return "Executable / Binary";
        }
        return "Other";
    }
}
