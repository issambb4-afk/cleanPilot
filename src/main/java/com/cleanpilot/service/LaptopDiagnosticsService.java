package com.cleanpilot.service;

import com.cleanpilot.model.*;
import com.cleanpilot.util.ProcessExecutionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gathers authentic hardware diagnostics tailored for laptops and portable workstations:
 * battery wear, ACPI charge rates, thermal throttling, NVMe/SATA queue saturation,
 * logical storage volumes, kernel memory pools (Paged/Non-Paged driver leaks),
 * virtual memory commit saturation, and operating system stability / crash events.
 */
@Service
public class LaptopDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(LaptopDiagnosticsService.class);

    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem operatingSystem;
    private final boolean isWindows;
    private final AtomicBoolean thermalSensorAvailable = new AtomicBoolean(true);

    public LaptopDiagnosticsService() {
        SystemInfo systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Generates a comprehensive real-time laptop health and hardware diagnostics report.
     */
    public LaptopDiagnosticsReport generateReport() {
        BatteryHealthInfo battery = inspectBattery();
        ThermalMetricsInfo thermals = inspectThermals();
        List<StorageHealthInfo> storageDrives = inspectStorageDrives();
        List<StorageVolumeHealthInfo> storageVolumes = inspectVolumes();
        MemoryThrashingInfo memory = inspectMemory();
        AdvancedMemoryHealthInfo advancedMemory = inspectAdvancedMemory();
        SystemStabilityReport stability = inspectSystemStability();

        List<String> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        int score = 100;

        // Battery Evaluation
        if (battery.hasBattery()) {
            if (battery.healthPercentage() > 0 && battery.healthPercentage() < 60.0) {
                issues.add("Battery capacity degraded to " + battery.formattedHealth() + " of design rating");
                recommendations.add("Consider battery service or replacement to restore portable runtime");
                score -= 15;
            } else if (battery.healthPercentage() > 0 && battery.healthPercentage() < 75.0) {
                issues.add("Moderate battery wear (" + battery.formattedHealth() + " original capacity)");
                score -= 8;
            }

            if (battery.isDischarging() && battery.powerUsageWatts() > 30.0) {
                issues.add("High discharge rate (" + battery.formattedPowerRate() + ")");
                recommendations.add("Lower screen brightness and close background CPU hogs to extend battery runtime");
                score -= 5;
            }
        }

        // Thermal Evaluation
        if (thermals.isThermalThrottlingRisk()) {
            issues.add("CPU package temperature elevated (" + thermals.formattedCpuTemp() + ")");
            recommendations.add("Elevate laptop chassis or clean cooling exhaust vents to prevent CPU downclocking");
            score -= 20;
        } else if (thermals.cpuTemperatureCelsius() >= 75.0) {
            issues.add("CPU temperature is warm (" + thermals.formattedCpuTemp() + ")");
            score -= 5;
        }

        // Memory Thrashing & Saturation Evaluation
        if (advancedMemory.isNonPagedPoolLeak()) {
            issues.add("Kernel Non-Paged Pool memory leak detected (" + advancedMemory.formattedNonPagedPool() + ")");
            recommendations.add("A hardware driver (network/audio/antivirus) is leaking kernel memory. Inspect Drivers tab.");
            score -= 20;
        }

        if (memory.isThrashingDetected()) {
            issues.add("Virtual memory swap thrashing detected (Pagefile " + memory.formattedSwapPercent() + ")");
            recommendations.add("Free up RAM by ending non-essential background processes in the Resource Monitor");
            score -= 20;
        } else if (memory.physicalUsedPercent() >= 90.0) {
            issues.add("Physical RAM near full saturation (" + memory.formattedPhysicalPercent() + ")");
            recommendations.add("Close unused browser tabs or heavy background applications");
            score -= 10;
        }

        if (advancedMemory.isCommitSaturationRisk()) {
            issues.add("Virtual memory commit saturation risk (" + advancedMemory.formattedCommit() + ")");
            recommendations.add("Close memory hogs to prevent OutOfMemory application crashes");
            score -= 15;
        }

        // Storage Bottlenecks & Capacity Evaluation
        for (StorageHealthInfo drive : storageDrives) {
            if (drive.isQueueBottleneck()) {
                issues.add("Drive " + drive.model() + " has heavy I/O queue backlog (Queue depth: " + drive.currentQueueLength() + ")");
                recommendations.add("Wait for intensive background file operations or disk indexing to finish");
                score -= 10;
            }
        }

        for (StorageVolumeHealthInfo vol : storageVolumes) {
            if (vol.isCriticalSpaceAlert()) {
                issues.add("Volume " + vol.mountPoint() + " critically low on free space (" + vol.formattedFree() + ")");
                recommendations.add("Free up space on " + vol.mountPoint() + " immediately using Large Files or Duplicates cleanup");
                score -= 20;
            } else if (vol.isLowSpaceAlert()) {
                issues.add("Volume " + vol.mountPoint() + " is low on space (" + vol.formattedFree() + ")");
                recommendations.add("Maintain at least 15% free space on " + vol.mountPoint() + " for healthy SSD TRIM & wear leveling");
                score -= 8;
            }
        }

        // Operating System Stability & Crash Evaluation
        if (stability.hasRecentCrashes()) {
            issues.add(stability.totalCrashesLast14Days() + " critical system crash/dirty shutdown event(s) in the past 14 days");
            recommendations.add("Review System Event Log for BSOD minidumps or hardware power disconnects");
            score -= Math.min(25, stability.totalCrashesLast14Days() * 10);
        }

        if (stability.isUptimeExcessive()) {
            issues.add("Excessive accumulated uptime: " + stability.formattedUptime() + " (Windows Fast-Startup bloat)");
            recommendations.add("Perform a full clean restart (Start -> Restart) to flush kernel caches and memory fragmentation");
            score -= 5;
        }

        score = Math.max(10, Math.min(100, score));

        String grade;
        if (score >= 90) {
            grade = "OPTIMAL";
        } else if (score >= 75) {
            grade = "GOOD";
        } else if (score >= 55) {
            grade = "ATTENTION NEEDED";
        } else {
            grade = "CRITICAL BOTTLENECK";
        }

        if (recommendations.isEmpty()) {
            if (score >= 90) {
                recommendations.add("Hardware parameters and system resources are running within optimal specifications");
            } else {
                recommendations.add("Keep cooling vents unobstructed and inspect the Resource Monitor for background hogs");
            }
        }

        return new LaptopDiagnosticsReport(
                score,
                grade,
                battery,
                thermals,
                storageDrives,
                storageVolumes,
                memory,
                advancedMemory,
                stability,
                issues,
                recommendations
        );
    }

    public BatteryHealthInfo inspectBattery() {
        try {
            List<PowerSource> powerSources = hardware.getPowerSources();
            if (powerSources.isEmpty()) {
                return BatteryHealthInfo.noBattery();
            }

            PowerSource ps = powerSources.get(0);
            double remainingPercent = ps.getRemainingCapacityPercent() * 100.0;
            boolean isPluggedIn = ps.isPowerOnLine();
            boolean isCharging = ps.isCharging();
            boolean isDischarging = ps.isDischarging();
            double timeRemainingSeconds = ps.getTimeRemainingEstimated();
            double powerWatts = Math.abs(ps.getPowerUsageRate() / 1000.0);
            double voltage = ps.getVoltage();
            double amperage = ps.getAmperage();
            int currentCapacity = ps.getCurrentCapacity();
            int maxCapacity = ps.getMaxCapacity();
            int designCapacity = ps.getDesignCapacity();
            int cycleCount = ps.getCycleCount();
            double temp = ps.getTemperature();

            double health = 100.0;
            if (designCapacity > 0 && maxCapacity > 0) {
                health = Math.min(100.0, ((double) maxCapacity / designCapacity) * 100.0);
            }

            String status;
            if (health >= 85.0) {
                status = "Excellent Condition";
            } else if (health >= 70.0) {
                status = "Good Condition";
            } else if (health >= 50.0) {
                status = "Degraded (Normal Wear)";
            } else {
                status = "Service Recommended";
            }

            return new BatteryHealthInfo(
                    true,
                    ps.getName() != null && !ps.getName().isBlank() ? ps.getName() : "Internal Battery",
                    ps.getDeviceName() != null ? ps.getDeviceName() : "Battery Pack",
                    ps.getChemistry() != null ? ps.getChemistry() : "Li-Ion",
                    remainingPercent,
                    isPluggedIn,
                    isCharging,
                    isDischarging,
                    timeRemainingSeconds,
                    powerWatts,
                    voltage,
                    amperage,
                    currentCapacity,
                    maxCapacity,
                    designCapacity,
                    health,
                    cycleCount,
                    temp,
                    status
            );
        } catch (Exception e) {
            log.warn("Failed to query battery diagnostics: {}", e.getMessage());
            return BatteryHealthInfo.noBattery();
        }
    }

    public ThermalMetricsInfo inspectThermals() {
        if (!thermalSensorAvailable.get()) {
            return new ThermalMetricsInfo(0.0, 0.0, new int[0], false, "Normal (< 55°C)");
        }

        try {
            Sensors sensors = hardware.getSensors();
            double cpuTemp = sensors.getCpuTemperature();

            // If temperature query fails or is not provided by the BIOS/ACPI tables,
            // disable repeated queries across virtual threads to avoid COM overhead.
            if (cpuTemp <= 0.0) {
                thermalSensorAvailable.set(false);
                return new ThermalMetricsInfo(0.0, 0.0, new int[0], false, "Normal (< 55°C)");
            }

            double cpuVoltage = sensors.getCpuVoltage();
            int[] fanSpeeds = sensors.getFanSpeeds();

            boolean throttleRisk = (cpuTemp >= 85.0);
            String status;
            if (cpuTemp < 60.0) {
                status = "Cool (Optimal)";
            } else if (cpuTemp < 75.0) {
                status = "Moderate";
            } else if (cpuTemp < 85.0) {
                status = "Elevated Heat";
            } else {
                status = "Throttling Risk (High)";
            }

            return new ThermalMetricsInfo(
                    cpuTemp,
                    cpuVoltage,
                    fanSpeeds,
                    throttleRisk,
                    status
            );
        } catch (Exception e) {
            log.debug("Hardware thermal sensors not supported via ACPI: {}", e.getMessage());
            thermalSensorAvailable.set(false);
            return new ThermalMetricsInfo(0.0, 0.0, new int[0], false, "Normal (< 55°C)");
        }
    }

    public List<StorageHealthInfo> inspectStorageDrives() {
        List<StorageHealthInfo> list = new ArrayList<>();
        try {
            List<HWDiskStore> diskStores = hardware.getDiskStores();
            for (HWDiskStore ds : diskStores) {
                if (ds.getSize() <= 0) continue;

                String status = ds.getCurrentQueueLength() >= 4 ? "Heavy Queue Saturation" : "Healthy";

                list.add(new StorageHealthInfo(
                        ds.getName(),
                        ds.getModel() != null ? ds.getModel() : "Solid State Drive",
                        ds.getSerial() != null ? ds.getSerial() : "-",
                        ds.getSize(),
                        ds.getReadBytes(),
                        ds.getWriteBytes(),
                        ds.getReads(),
                        ds.getWrites(),
                        ds.getCurrentQueueLength(),
                        ds.getTransferTime(),
                        status
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to query storage hardware stats: {}", e.getMessage());
        }
        return list;
    }

    public List<StorageVolumeHealthInfo> inspectVolumes() {
        List<StorageVolumeHealthInfo> volumes = new ArrayList<>();
        try {
            List<OSFileStore> fileStores = operatingSystem.getFileSystem().getFileStores();
            for (OSFileStore fs : fileStores) {
                long total = fs.getTotalSpace();
                if (total <= 0) continue;

                long free = fs.getFreeSpace();
                long usable = fs.getUsableSpace();
                double freePct = ((double) free / total) * 100.0;

                boolean isCritical = (freePct < 5.0) || (free < 2L * 1024 * 1024 * 1024); // < 5% or < 2GB
                boolean isLow = (freePct < 15.0) || (free < 10L * 1024 * 1024 * 1024);    // < 15% or < 10GB

                String status;
                if (isCritical) {
                    status = "Critical Low Space (<5%)";
                } else if (isLow) {
                    status = "Low Space Warning (<15%)";
                } else {
                    status = "Healthy Capacity";
                }

                volumes.add(new StorageVolumeHealthInfo(
                        fs.getMount(),
                        fs.getName() != null && !fs.getName().isBlank() ? fs.getName() : fs.getMount(),
                        fs.getType(),
                        total,
                        free,
                        usable,
                        freePct,
                        isLow,
                        isCritical,
                        status
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to query volume file store diagnostics: {}", e.getMessage());
        }
        return volumes;
    }

    public MemoryThrashingInfo inspectMemory() {
        try {
            GlobalMemory mem = hardware.getMemory();
            VirtualMemory vmem = mem.getVirtualMemory();

            long physicalTotal = mem.getTotal();
            long physicalAvailable = mem.getAvailable();
            long physicalUsed = physicalTotal - physicalAvailable;
            double physicalUsedPercent = physicalTotal > 0 ? ((double) physicalUsed / physicalTotal) * 100.0 : 0.0;

            long swapTotal = vmem.getSwapTotal();
            long swapUsed = vmem.getSwapUsed();
            double swapUsedPercent = swapTotal > 0 ? ((double) swapUsed / swapTotal) * 100.0 : 0.0;

            long swapPagesIn = vmem.getSwapPagesIn();
            long swapPagesOut = vmem.getSwapPagesOut();

            boolean thrashing = (physicalUsedPercent >= 92.0 && swapUsedPercent >= 50.0) || (swapUsedPercent >= 75.0);

            String status;
            if (thrashing) {
                status = "Thrashing Alert (Low Memory Freeze Risk)";
            } else if (swapUsedPercent >= 50.0) {
                status = "Elevated Pagefile Usage";
            } else if (physicalUsedPercent >= 85.0) {
                status = "High Memory Pressure";
            } else {
                status = "Optimal";
            }

            return new MemoryThrashingInfo(
                    physicalTotal,
                    physicalUsed,
                    physicalAvailable,
                    physicalUsedPercent,
                    swapTotal,
                    swapUsed,
                    swapUsedPercent,
                    swapPagesIn,
                    swapPagesOut,
                    thrashing,
                    status
            );
        } catch (Exception e) {
            log.warn("Failed to query memory paging diagnostics: {}", e.getMessage());
            return new MemoryThrashingInfo(0, 0, 0, 0, 0, 0, 0, 0, 0, false, "Unknown");
        }
    }

    public AdvancedMemoryHealthInfo inspectAdvancedMemory() {
        try {
            GlobalMemory mem = hardware.getMemory();
            VirtualMemory vmem = mem.getVirtualMemory();

            long physicalTotal = mem.getTotal();
            long physicalAvailable = mem.getAvailable();
            long physicalUsed = physicalTotal - physicalAvailable;
            double physicalUsedPercent = physicalTotal > 0 ? ((double) physicalUsed / physicalTotal) * 100.0 : 0.0;

            long commitLimit = vmem.getVirtualMax();
            long commitTotal = vmem.getVirtualInUse();
            double commitUsedPercent = commitLimit > 0 ? ((double) commitTotal / commitLimit) * 100.0 : 0.0;

            long pagedPool = 0L;
            long nonPagedPool = 0L;
            boolean isNonPagedLeak = false;

            if (isWindows) {
                try {
                    com.sun.jna.platform.win32.Psapi.PERFORMANCE_INFORMATION perfInfo =
                            new com.sun.jna.platform.win32.Psapi.PERFORMANCE_INFORMATION();
                    if (com.sun.jna.platform.win32.Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
                        long pageSize = perfInfo.PageSize.longValue();
                        pagedPool = perfInfo.KernelPaged.longValue() * pageSize;
                        nonPagedPool = perfInfo.KernelNonpaged.longValue() * pageSize;
                        // Kernel Non-Paged pool exceeding 1.2 GB indicates an active kernel/driver memory leak
                        if (nonPagedPool > 1200L * 1024 * 1024) {
                            isNonPagedLeak = true;
                        }
                    }
                } catch (Throwable t) {
                    log.debug("Could not read Psapi PERFORMANCE_INFORMATION: {}", t.getMessage());
                }
            }

            long swapTotal = vmem.getSwapTotal();
            long swapUsed = vmem.getSwapUsed();
            double swapUsedPercent = swapTotal > 0 ? ((double) swapUsed / swapTotal) * 100.0 : 0.0;

            boolean thrashing = (physicalUsedPercent >= 92.0 && swapUsedPercent >= 50.0) || (swapUsedPercent >= 75.0);
            boolean commitSaturation = commitUsedPercent >= 88.0;

            String status;
            if (isNonPagedLeak) {
                status = "Driver Leak Alert (Kernel Non-Paged Pool > 1.2 GB)";
            } else if (thrashing) {
                status = "Thrashing Alert (Low Memory Freeze Risk)";
            } else if (commitSaturation) {
                status = "Virtual Memory Exhaustion Alert";
            } else if (physicalUsedPercent >= 85.0) {
                status = "High Memory Pressure";
            } else {
                status = "Optimal Memory Health";
            }

            return new AdvancedMemoryHealthInfo(
                    physicalTotal,
                    physicalUsed,
                    physicalAvailable,
                    physicalUsedPercent,
                    commitLimit,
                    commitTotal,
                    commitUsedPercent,
                    pagedPool,
                    nonPagedPool,
                    isNonPagedLeak,
                    commitSaturation,
                    swapTotal,
                    swapUsed,
                    swapUsedPercent,
                    thrashing,
                    status
            );
        } catch (Exception e) {
            log.warn("Failed to query advanced memory telemetry: {}", e.getMessage());
            return new AdvancedMemoryHealthInfo(0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 0, 0, 0, false, "Unknown");
        }
    }

    public SystemStabilityReport inspectSystemStability() {
        try {
            long uptimeSeconds = operatingSystem.getSystemUptime();
            boolean isUptimeExcessive = uptimeSeconds > (14L * 86400L); // > 14 days without clean reboot
            String formattedUptime = formatUptime(uptimeSeconds);

            List<SystemStabilityEvent> recentCrashes = queryRecentCrashEvents();
            int totalCrashes = recentCrashes.size();

            String grade;
            if (totalCrashes == 0 && !isUptimeExcessive) {
                grade = "OPTIMAL (Clean Stability)";
            } else if (totalCrashes == 0) {
                grade = "STABLE (Reboot Recommended)";
            } else if (totalCrashes <= 2) {
                grade = "ATTENTION (Past Crashes Recorded)";
            } else {
                grade = "CRITICAL (Frequent Hardware/OS Crashes)";
            }

            return new SystemStabilityReport(
                    uptimeSeconds,
                    formattedUptime,
                    isUptimeExcessive,
                    recentCrashes,
                    totalCrashes,
                    grade
            );
        } catch (Exception e) {
            log.warn("Failed to query system stability: {}", e.getMessage());
            return new SystemStabilityReport(0, "Unknown", false, Collections.emptyList(), 0, "Unknown");
        }
    }

    private List<SystemStabilityEvent> queryRecentCrashEvents() {
        List<SystemStabilityEvent> events = new ArrayList<>();
        if (!isWindows) {
            return events;
        }

        try {
            // Query System log for Event ID 41 (Kernel-Power dirty shutdown), 1001 (BugCheck / BSOD),
            // 7 (Disk bad block), 11 (Disk controller error) within last 14 days (1209600000 ms)
            String query = "*[System[(EventID=41 or EventID=1001 or EventID=7 or EventID=11) and TimeCreated[timediff(@SystemTime) <= 1209600000]]]";
            ProcessBuilder pb = new ProcessBuilder("wevtutil", "qe", "System", "/q:" + query, "/c:5", "/f:text");
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 5);
            if (!result.isSuccess() || result.stdout().isBlank()) {
                return events;
            }

            Integer eventId = null;
            String source = null;
            String dateStr = null;
            StringBuilder desc = new StringBuilder();

            String[] lines = result.stdout().split("\\R");
            for (String rawLine : lines) {
                String trimmed = rawLine.trim();
                if (trimmed.startsWith("Event[")) {
                    if (eventId != null) {
                        events.add(createStabilityEvent(eventId, source, dateStr, desc.toString()));
                    }
                    eventId = null;
                    source = null;
                    dateStr = null;
                    desc.setLength(0);
                } else if (trimmed.startsWith("Event ID:")) {
                    try {
                        eventId = Integer.parseInt(trimmed.substring("Event ID:".length()).trim());
                    } catch (NumberFormatException ignored) {}
                } else if (trimmed.startsWith("Source:")) {
                    source = trimmed.substring("Source:".length()).trim();
                } else if (trimmed.startsWith("Date:")) {
                    dateStr = trimmed.substring("Date:".length()).trim();
                } else if (trimmed.startsWith("Description:")) {
                    // next lines will be description
                } else if (eventId != null && !trimmed.isEmpty()) {
                    if (desc.length() < 200) {
                        desc.append(trimmed).append(" ");
                    }
                }
            }
            if (eventId != null) {
                events.add(createStabilityEvent(eventId, source, dateStr, desc.toString()));
            }
        } catch (Exception e) {
            log.debug("Event log query skipped or failed: {}", e.getMessage());
        }
        return events;
    }

    private SystemStabilityEvent createStabilityEvent(int eventId, String source, String dateStr, String rawDesc) {
        String type;
        String summary;
        if (eventId == 1001) {
            type = "Blue Screen of Death (BSOD)";
            summary = "System crashed with a BugCheck stop error.";
        } else if (eventId == 41) {
            type = "Dirty Shutdown / Sudden Power Loss";
            summary = "System rebooted unexpectedly without cleanly closing files.";
        } else if (eventId == 7) {
            type = "Disk Bad Block Detected";
            summary = "The storage device has a bad block or unreadable sector.";
        } else if (eventId == 11) {
            type = "Disk Controller Error";
            summary = "The controller encountered a fatal communication error with the drive.";
        } else {
            type = "System Error " + eventId;
            summary = rawDesc.isBlank() ? "Critical event in System log" : rawDesc.trim();
        }

        LocalDateTime dt = LocalDateTime.now();
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                dt = LocalDateTime.parse(dateStr.replace("Z", "").substring(0, 19));
            } catch (Exception ignored) {}
        }

        return new SystemStabilityEvent(dt, type, eventId, source != null ? source : "System", summary);
    }

    private String formatUptime(long seconds) {
        if (seconds <= 0) return "Just started";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h " + mins + "m";
        } else if (hours > 0) {
            return hours + "h " + mins + "m";
        } else {
            return mins + " mins";
        }
    }
}
