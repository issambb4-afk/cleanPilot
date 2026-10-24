package com.cleanpilot.service;

import com.cleanpilot.model.DetailedProcessInfo;
import com.cleanpilot.model.ProcessInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors active processes to detect runaway CPU consumption, excessive RAM usage,
 * and system-blocking background tasks.
 */
@Service
public class ProcessMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(ProcessMonitoringService.class);

    public enum ProcessSortBy {
        CPU,
        MEMORY,
        DISK_IO,
        NAME,
        PID
    }

    private final OperatingSystem operatingSystem;
    private final int logicalProcessorCount;
    private final Map<Integer, OSProcess> priorProcesses = new ConcurrentHashMap<>();

    public ProcessMonitoringService(SystemMetricsService systemMetricsService) {
        SystemInfo systemInfo = new SystemInfo();
        this.operatingSystem = systemInfo.getOperatingSystem();
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        this.logicalProcessorCount = Math.max(1, processor.getLogicalProcessorCount());
    }

    /**
     * Retrieves an active snapshot of processes sorted by specified metric.
     *
     * @param limit Maximum number of processes to return
     * @param sortBy Sort criteria (CPU, MEMORY, DISK_IO, etc.)
     * @return List of ProcessInfo models
     */
    public synchronized List<ProcessInfo> getProcesses(int limit, ProcessSortBy sortBy) {
        List<OSProcess> currentProcs = operatingSystem.getProcesses();
        Map<Integer, OSProcess> currentMap = new HashMap<>();
        List<ProcessInfo> result = new ArrayList<>();

        for (OSProcess proc : currentProcs) {
            int pid = proc.getProcessID();
            currentMap.put(pid, proc);

            OSProcess prior = priorProcesses.get(pid);
            double cpuPercent;
            if (prior != null) {
                // Calculate CPU load between consecutive snapshots normalized by logical CPU cores
                double rawCpu = proc.getProcessCpuLoadBetweenTicks(prior);
                cpuPercent = Math.max(0.0, (rawCpu * 100.0) / logicalProcessorCount);
            } else {
                cpuPercent = Math.max(0.0, (proc.getProcessCpuLoadCumulative() * 100.0) / logicalProcessorCount);
            }

            result.add(new ProcessInfo(
                    pid,
                    proc.getName() != null && !proc.getName().isBlank() ? proc.getName() : "Process " + pid,
                    cpuPercent,
                    proc.getResidentSetSize(),
                    proc.getVirtualSize(),
                    proc.getBytesRead(),
                    proc.getBytesWritten(),
                    proc.getThreadCount(),
                    proc.getState() != null ? proc.getState().toString() : "UNKNOWN",
                    proc.getUser() != null ? proc.getUser() : "-",
                    ""
            ));
        }

        // Update prior cache for subsequent CPU tick delta calculation
        priorProcesses.clear();
        priorProcesses.putAll(currentMap);

        // Sort results
        Comparator<ProcessInfo> comparator = switch (sortBy) {
            case CPU -> Comparator.comparingDouble(ProcessInfo::cpuPercent).reversed();
            case MEMORY -> Comparator.comparingLong(ProcessInfo::residentMemoryBytes).reversed();
            case DISK_IO -> Comparator.comparingLong((ProcessInfo p) -> p.bytesRead() + p.bytesWritten()).reversed();
            case NAME -> Comparator.comparing(ProcessInfo::name, String.CASE_INSENSITIVE_ORDER);
            case PID -> Comparator.comparingInt(ProcessInfo::pid);
        };

        result.sort(comparator);

        if (limit > 0 && result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }

        return result;
    }

    /**
     * Identifies processes that are currently putting heavy strain on system resources
     * (CPU >= 15% or RAM >= 1 GB).
     */
    public List<ProcessInfo> getSystemHogs() {
        List<ProcessInfo> all = getProcesses(100, ProcessSortBy.CPU);
        List<ProcessInfo> hogs = new ArrayList<>();
        for (ProcessInfo p : all) {
            if (p.isSystemHog()) {
                hogs.add(p);
            }
        }
        return hogs;
    }

    private static final Set<String> CRITICAL_WINDOWS_BINARIES = Set.of(
            "system", "system idle process", "smss.exe", "csrss.exe", "wininit.exe",
            "services.exe", "lsass.exe", "winlogon.exe", "explorer.exe", "svchost.exe",
            "fontdrvhost.exe", "dwm.exe", "spoolsv.exe"
    );

    private static final Set<String> CRITICAL_UNIX_BINARIES = Set.of(
            "systemd", "init", "launchd", "kernel_task", "kthreadd", "kworker",
            "sshd", "loginwindow", "windowserver"
    );

    /**
     * Identifies whether a process is an essential operating system component
     * whose termination would destabilize or crash the OS.
     */
    public boolean isCriticalProcess(int pid, String processName) {
        if (pid <= 4) {
            return true;
        }
        if (processName == null || processName.isBlank()) {
            return false;
        }
        String lower = processName.trim().toLowerCase(Locale.ROOT);
        return CRITICAL_WINDOWS_BINARIES.contains(lower) || CRITICAL_UNIX_BINARIES.contains(lower);
    }

    /**
     * Inspects a specific running process to retrieve deep, authentic execution metadata.
     *
     * @param pid Process ID
     * @return DetailedProcessInfo or null if process has terminated
     */
    public DetailedProcessInfo getProcessDetails(int pid) {
        OSProcess proc = operatingSystem.getProcess(pid);
        if (proc == null) {
            return null;
        }

        // Parent process identification
        int parentPid = proc.getParentProcessID();
        String parentName = "-";
        if (parentPid > 0) {
            OSProcess parentProc = operatingSystem.getProcess(parentPid);
            if (parentProc != null && parentProc.getName() != null) {
                parentName = parentProc.getName() + " (PID " + parentPid + ")";
            } else {
                parentName = "PID " + parentPid;
            }
        }

        // Live CPU delta calculation
        OSProcess prior = priorProcesses.get(pid);
        double cpuPercent;
        if (prior != null) {
            double rawCpu = proc.getProcessCpuLoadBetweenTicks(prior);
            cpuPercent = Math.max(0.0, (rawCpu * 100.0) / logicalProcessorCount);
        } else {
            cpuPercent = Math.max(0.0, (proc.getProcessCpuLoadCumulative() * 100.0) / logicalProcessorCount);
        }

        // Discover active child PIDs via Java 9+ ProcessHandle
        List<Integer> childPids = new ArrayList<>();
        try {
            Optional<ProcessHandle> handleOpt = ProcessHandle.of(pid);
            if (handleOpt.isPresent()) {
                handleOpt.get().children().forEach(ch -> childPids.add((int) ch.pid()));
            }
        } catch (Exception ignored) {
        }

        // Resolve command line (OSHI -> Java ProcessHandle fallback)
        String cmdLine = proc.getCommandLine();
        if (cmdLine == null || cmdLine.isBlank()) {
            try {
                Optional<ProcessHandle> ph = ProcessHandle.of(pid);
                if (ph.isPresent() && ph.get().info().commandLine().isPresent()) {
                    cmdLine = ph.get().info().commandLine().get();
                }
            } catch (Exception ignored) {
            }
        }
        if (cmdLine == null || cmdLine.isBlank()) {
            cmdLine = proc.getPath() != null && !proc.getPath().isBlank() ? proc.getPath() : proc.getName();
        }

        boolean critical = isCriticalProcess(pid, proc.getName());
        String impact = getImpactClassification(cpuPercent, proc.getResidentSetSize(), critical);

        return new DetailedProcessInfo(
                pid,
                parentPid,
                parentName,
                proc.getName() != null && !proc.getName().isBlank() ? proc.getName() : "Process " + pid,
                proc.getPath() != null && !proc.getPath().isBlank() ? proc.getPath() : "-",
                cmdLine != null ? cmdLine : "-",
                proc.getCurrentWorkingDirectory() != null && !proc.getCurrentWorkingDirectory().isBlank() ? proc.getCurrentWorkingDirectory() : "-",
                proc.getUser() != null ? proc.getUser() : "-",
                proc.getUserID() != null ? proc.getUserID() : "-",
                proc.getGroup() != null ? proc.getGroup() : "-",
                proc.getGroupID() != null ? proc.getGroupID() : "-",
                proc.getState() != null ? proc.getState().toString() : "UNKNOWN",
                proc.getBitness(),
                proc.getPriority(),
                proc.getThreadCount(),
                proc.getStartTime(),
                proc.getUpTime(),
                proc.getUserTime(),
                proc.getKernelTime(),
                cpuPercent,
                proc.getResidentSetSize(),
                proc.getVirtualSize(),
                proc.getBytesRead(),
                proc.getBytesWritten(),
                proc.getOpenFiles(),
                proc.getMinorFaults(),
                proc.getMajorFaults(),
                childPids,
                critical,
                impact
        );
    }

    private String getImpactClassification(double cpuPercent, long ramBytes, boolean critical) {
        if (critical) {
            return "System Core";
        }
        if (cpuPercent >= 40.0 && ramBytes >= 1024L * 1024 * 1024) {
            return "Critical Blocker";
        }
        if (cpuPercent >= 25.0) {
            return "High CPU Load";
        }
        if (ramBytes >= 1024L * 1024 * 1024) {
            return "Heavy Memory";
        }
        if (cpuPercent >= 10.0) {
            return "Moderate Load";
        }
        return "Normal";
    }

    /**
     * Safely attempts to terminate a process by PID.
     *
     * @param pid Process ID
     * @param force Whether to force kill
     * @return true if termination command was successfully dispatched
     */
    public boolean terminateProcess(int pid, boolean force) {
        try {
            Optional<ProcessHandle> handleOpt = ProcessHandle.of(pid);
            if (handleOpt.isPresent()) {
                ProcessHandle handle = handleOpt.get();
                boolean destroyed = force ? handle.destroyForcibly() : handle.destroy();
                log.info("Process PID {} termination requested (force={}): {}", pid, force, destroyed);
                return destroyed;
            } else {
                log.warn("Process PID {} not found for termination", pid);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to terminate process PID {}: {}", pid, e.getMessage());
            return false;
        }
    }
}
