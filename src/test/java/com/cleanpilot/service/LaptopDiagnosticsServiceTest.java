package com.cleanpilot.service;

import com.cleanpilot.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LaptopDiagnosticsServiceTest {

    private final LaptopDiagnosticsService diagnosticsService = new LaptopDiagnosticsService();

    @Test
    @DisplayName("Should generate complete laptop diagnostics report from authentic hardware")
    void testGenerateReport() {
        LaptopDiagnosticsReport report = diagnosticsService.generateReport();

        assertThat(report).isNotNull();
        assertThat(report.overallHealthScore()).isBetween(10, 100);
        assertThat(report.scoreGrade()).isNotBlank();
        assertThat(report.formattedScore()).contains("/100");
        assertThat(report.recommendations()).isNotEmpty();

        // Battery diagnostics
        BatteryHealthInfo battery = report.battery();
        assertThat(battery).isNotNull();
        assertThat(battery.formattedRemaining()).isNotEmpty();
        assertThat(battery.formattedHealth()).isNotEmpty();

        // Thermals diagnostics
        ThermalMetricsInfo thermals = report.thermals();
        assertThat(thermals).isNotNull();
        assertThat(thermals.formattedCpuTemp()).isNotEmpty();
        assertThat(thermals.formattedVoltage()).isNotEmpty();
        assertThat(thermals.formattedFanSpeeds()).isNotEmpty();

        // Physical storage drives
        List<StorageHealthInfo> drives = report.storageDrives();
        assertThat(drives).isNotNull();
        for (StorageHealthInfo d : drives) {
            assertThat(d.model()).isNotBlank();
            assertThat(d.formattedSize()).isNotEmpty();
            assertThat(d.formattedTotalTransfer()).isNotEmpty();
        }

        // Logical Storage Volumes
        List<StorageVolumeHealthInfo> volumes = report.storageVolumes();
        assertThat(volumes).isNotNull();

        // Memory & Thrashing
        MemoryThrashingInfo mem = report.memory();
        assertThat(mem).isNotNull();
        assertThat(mem.physicalTotalBytes()).isGreaterThan(0L);
        assertThat(mem.formattedPhysicalUsed()).isNotEmpty();
        assertThat(mem.formattedPhysicalPercent()).isNotEmpty();
        assertThat(mem.memoryHealthStatus()).isNotBlank();

        // Advanced Memory & Kernel Pools
        AdvancedMemoryHealthInfo advMem = report.advancedMemory();
        assertThat(advMem).isNotNull();
        assertThat(advMem.physicalTotalBytes()).isGreaterThan(0L);
        assertThat(advMem.formattedPhysicalUsed()).isNotEmpty();
        assertThat(advMem.formattedNonPagedPool()).isNotEmpty();

        // System Stability
        SystemStabilityReport stability = report.stability();
        assertThat(stability).isNotNull();
        assertThat(stability.formattedUptime()).isNotEmpty();
        assertThat(stability.stabilityGrade()).isNotEmpty();
    }

    @Test
    @DisplayName("Storage volume model calculations and alert thresholds")
    void testStorageVolumeModel() {
        long total = 500L * 1024 * 1024 * 1024;
        long freeLow = 50L * 1024 * 1024 * 1024; // 10%
        StorageVolumeHealthInfo lowVolume = new StorageVolumeHealthInfo(
                "C:\\",
                "OS Disk",
                "NTFS",
                total,
                freeLow,
                freeLow,
                10.0,
                true,
                false,
                "Low Space Warning (<15%)"
        );

        assertThat(lowVolume.usedPercent()).isEqualTo(90.0);
        assertThat(lowVolume.isLowSpaceAlert()).isTrue();
        assertThat(lowVolume.isCriticalSpaceAlert()).isFalse();
        assertThat(lowVolume.formattedFree()).contains("10.0%");
    }

    @Test
    @DisplayName("Advanced memory kernel non-paged pool leak detection")
    void testAdvancedMemoryLeakDetection() {
        AdvancedMemoryHealthInfo leakMemory = new AdvancedMemoryHealthInfo(
                32L * 1024 * 1024 * 1024,
                24L * 1024 * 1024 * 1024,
                8L * 1024 * 1024 * 1024,
                75.0,
                40L * 1024 * 1024 * 1024,
                38L * 1024 * 1024 * 1024,
                95.0,
                800L * 1024 * 1024,
                1500L * 1024 * 1024, // 1.5 GB non-paged pool -> leak
                true,
                true,
                8L * 1024 * 1024 * 1024,
                2L * 1024 * 1024 * 1024,
                25.0,
                false,
                "Driver Leak Alert"
        );

        assertThat(leakMemory.isNonPagedPoolLeak()).isTrue();
        assertThat(leakMemory.isCommitSaturationRisk()).isTrue();
        assertThat(leakMemory.formattedNonPagedPool()).contains("1.46 GB");
    }

    @Test
    @DisplayName("System stability uptime and crash events evaluation")
    void testSystemStabilityModel() {
        SystemStabilityEvent bsod = new SystemStabilityEvent(
                LocalDateTime.now(),
                "Blue Screen of Death (BSOD)",
                1001,
                "BugCheck",
                "System crashed with stop error"
        );

        SystemStabilityReport report = new SystemStabilityReport(
                20L * 86400L, // 20 days uptime -> excessive
                "20d 0h 0m",
                true,
                List.of(bsod),
                1,
                "ATTENTION (Past Crashes Recorded)"
        );

        assertThat(report.isUptimeExcessive()).isTrue();
        assertThat(report.hasRecentCrashes()).isTrue();
        assertThat(report.totalCrashesLast14Days()).isEqualTo(1);
    }

    @Test
    @DisplayName("Battery model formatting calculations")
    void testBatteryModel() {
        BatteryHealthInfo testBat = new BatteryHealthInfo(
                true,
                "Internal Battery",
                "Dell Battery Pack",
                "Li-Ion",
                82.5,
                false,
                false,
                true,
                7200, // 2 hours
                18.5,
                11.4,
                -1620,
                45000,
                50000,
                55000,
                (50000.0 / 55000.0) * 100.0, // ~90.9%
                142,
                31.5,
                "Excellent Health"
        );

        assertThat(testBat.hasBattery()).isTrue();
        assertThat(testBat.formattedRemaining()).isEqualTo("82.5%");
        assertThat(testBat.formattedHealth()).isEqualTo("90.9%");
        assertThat(testBat.formattedPowerRate()).isEqualTo("18.50 W");
        assertThat(testBat.formattedCycleCount()).isEqualTo("142 cycles");
        assertThat(testBat.formattedTimeRemaining()).contains("remaining");
    }

    @Test
    @DisplayName("Memory Thrashing model detection")
    void testMemoryThrashingDetection() {
        MemoryThrashingInfo normal = new MemoryThrashingInfo(
                16L * 1024 * 1024 * 1024,
                8L * 1024 * 1024 * 1024,
                8L * 1024 * 1024 * 1024,
                50.0,
                4L * 1024 * 1024 * 1024,
                1L * 1024 * 1024 * 1024,
                25.0,
                100,
                50,
                false,
                "Optimal"
        );
        assertThat(normal.isThrashingDetected()).isFalse();

        MemoryThrashingInfo thrashing = new MemoryThrashingInfo(
                16L * 1024 * 1024 * 1024,
                15L * 1024 * 1024 * 1024,
                1L * 1024 * 1024 * 1024,
                94.0,
                8L * 1024 * 1024 * 1024,
                7L * 1024 * 1024 * 1024,
                87.5,
                5000,
                4200,
                true,
                "Thrashing Alert"
        );
        assertThat(thrashing.isThrashingDetected()).isTrue();
    }
}
