package com.cleanpilot.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FormatUtilsTest {

    @Test
    @DisplayName("Format bytes should convert byte values to human readable representations")
    void testFormatBytes() {
        assertThat(FormatUtils.formatBytes(-50)).isEqualTo("0 B");
        assertThat(FormatUtils.formatBytes(0)).isEqualTo("0 B");
        assertThat(FormatUtils.formatBytes(512)).isEqualTo("512 B");
        assertThat(FormatUtils.formatBytes(1024)).contains("KB");
        assertThat(FormatUtils.formatBytes(1024 * 1024)).contains("MB");
        assertThat(FormatUtils.formatBytes(1024L * 1024 * 1024 * 5)).contains("5.00 GB");
        assertThat(FormatUtils.formatBytes(1024L * 1024 * 1024 * 1024 * 2)).contains("2.00 TB");
    }

    @Test
    @DisplayName("Format percentage should clamp and format correctly")
    void testFormatPercentage() {
        assertThat(FormatUtils.formatPercentage(0.0)).isEqualTo("0.0%");
        assertThat(FormatUtils.formatPercentage(0.4526)).isEqualTo("45.3%");
        assertThat(FormatUtils.formatPercentage(1.0)).isEqualTo("100.0%");
        assertThat(FormatUtils.formatPercentage(1.5)).isEqualTo("100.0%");
        assertThat(FormatUtils.formatPercentage(-0.2)).isEqualTo("0.0%");
    }

    @Test
    @DisplayName("Format duration should handle milliseconds, seconds, and minutes")
    void testFormatDuration() {
        assertThat(FormatUtils.formatDuration(350)).isEqualTo("350 ms");
        assertThat(FormatUtils.formatDuration(1500)).isEqualTo("1.5 s");
        assertThat(FormatUtils.formatDuration(75000)).isEqualTo("1 m 15 s");
    }

    @Test
    @DisplayName("File extension extraction and categorization")
    void testFileExtensionAndCategory() {
        assertThat(FormatUtils.getFileExtension("video.mp4")).isEqualTo("mp4");
        assertThat(FormatUtils.getFileExtension("archive.TAR.GZ")).isEqualTo("gz");
        assertThat(FormatUtils.getFileExtension("noextension")).isEqualTo("");
        assertThat(FormatUtils.getFileExtension(null)).isEqualTo("");

        assertThat(FormatUtils.categorize("mp4")).isEqualTo("Video");
        assertThat(FormatUtils.categorize("mkv")).isEqualTo("Video");
        assertThat(FormatUtils.categorize("mp3")).isEqualTo("Audio");
        assertThat(FormatUtils.categorize("zip")).isEqualTo("Archive / Image");
        assertThat(FormatUtils.categorize("iso")).isEqualTo("Archive / Image");
        assertThat(FormatUtils.categorize("pdf")).isEqualTo("Document");
        assertThat(FormatUtils.categorize("exe")).isEqualTo("Executable / Binary");
        assertThat(FormatUtils.categorize("unknownxyz")).isEqualTo("Other");
    }

    @Test
    @DisplayName("Format instant formats non-null and handles null")
    void testFormatInstant() {
        assertThat(FormatUtils.formatInstant(null)).isEqualTo("-");
        assertThat(FormatUtils.formatInstant(Instant.now())).isNotEmpty();
    }

    @Test
    @DisplayName("Format speed and throughput")
    void testSpeedAndThroughput() {
        assertThat(FormatUtils.formatSpeed(0)).isEqualTo("0 B/s");
        assertThat(FormatUtils.formatSpeed(1024 * 1024 * 15)).contains("15.00 MB/s");
        assertThat(FormatUtils.formatThroughput(2500)).isEqualTo("2,500 files/s");
    }

    @Test
    @DisplayName("Developer artifact detection")
    void testDeveloperArtifacts() {
        java.nio.file.Path nodeModules = java.nio.file.Paths.get("C:/project/node_modules/package/file.js");
        java.nio.file.Path normalFile = java.nio.file.Paths.get("C:/project/src/main/App.java");

        assertThat(FormatUtils.isDeveloperArtifact(nodeModules)).isTrue();
        assertThat(FormatUtils.isDeveloperArtifact(normalFile)).isFalse();
        assertThat(FormatUtils.categorize("js", nodeModules)).isEqualTo("Developer Artifacts");
        assertThat(FormatUtils.categorize("java", normalFile)).isEqualTo("Other");
    }
}
