package com.cleanpilot.service;

import com.cleanpilot.model.DrivePartitionInfo;
import com.cleanpilot.model.SystemResourceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries authentic physical disk drives, mounted partitions, CPU load, and RAM stats via OSHI and Java NIO.
 */
@Service
public class SystemMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsService.class);

    private final SystemInfo systemInfo;
    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final OperatingSystem operatingSystem;

    private long[] prevCpuTicks;

    public SystemMetricsService() {
        this.systemInfo = new SystemInfo();
        this.processor = systemInfo.getHardware().getProcessor();
        this.memory = systemInfo.getHardware().getMemory();
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.prevCpuTicks = processor.getSystemCpuLoadTicks();
    }

    /**
     * Enumerates all mounted partitions and physical file stores with authentic capacity metrics.
     */
    public List<DrivePartitionInfo> getPartitions() {
        List<DrivePartitionInfo> partitions = new ArrayList<>();
        try {
            FileSystem fileSystem = operatingSystem.getFileSystem();
            List<OSFileStore> fileStores = fileSystem.getFileStores();

            for (OSFileStore store : fileStores) {
                // Filter out zero-capacity or virtual system pseudo-mounts
                if (store.getTotalSpace() <= 0) {
                    continue;
                }

                String name = store.getName();
                String mount = store.getMount();
                String type = store.getType();
                long total = store.getTotalSpace();
                long free = store.getFreeSpace();
                long usable = store.getUsableSpace();

                // On Windows, store.getName() might be drive label or drive letter
                String displayName = name;
                if (mount != null && !mount.isBlank()) {
                    if (displayName == null || displayName.isBlank()) {
                        displayName = mount;
                    } else if (!displayName.contains(mount)) {
                        displayName = displayName + " (" + mount + ")";
                    }
                }

                partitions.add(new DrivePartitionInfo(
                        displayName,
                        mount,
                        type,
                        total,
                        free,
                        usable,
                        store.getFreeSpace() == 0
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to query file stores via OSHI: {}. Falling back to File.listRoots()", e.getMessage());
            // Fallback to standard Java File.listRoots()
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    long total = root.getTotalSpace();
                    long free = root.getFreeSpace();
                    long usable = root.getUsableSpace();
                    if (total > 0) {
                        partitions.add(new DrivePartitionInfo(
                                root.getPath(),
                                root.getAbsolutePath(),
                                "Local Disk",
                                total,
                                free,
                                usable,
                                !root.canWrite()
                        ));
                    }
                }
            }
        }
        return partitions;
    }

    /**
     * Queries current CPU load percentage and physical RAM metrics.
     */
    public synchronized SystemResourceMetrics getSystemResourceMetrics() {
        double cpuLoad = 0.0;
        try {
            cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks);
            prevCpuTicks = processor.getSystemCpuLoadTicks();
        } catch (Exception e) {
            log.warn("Error calculating CPU load: {}", e.getMessage());
        }

        long totalMem = memory.getTotal();
        long availMem = memory.getAvailable();
        long usedMem = Math.max(0, totalMem - availMem);

        return new SystemResourceMetrics(cpuLoad, usedMem, totalMem);
    }

    public String getOsFamily() {
        return System.getProperty("os.name", "Unknown");
    }
}
