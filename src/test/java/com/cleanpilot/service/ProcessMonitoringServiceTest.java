package com.cleanpilot.service;

import com.cleanpilot.model.ProcessInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessMonitoringServiceTest {

    private final SystemMetricsService metricsService = new SystemMetricsService();
    private final ProcessMonitoringService processService = new ProcessMonitoringService(metricsService);

    @Test
    @DisplayName("Should query authentic running processes from OS")
    void testGetProcesses() {
        List<ProcessInfo> processes = processService.getProcesses(10, ProcessMonitoringService.ProcessSortBy.CPU);

        assertThat(processes).isNotEmpty();
        ProcessInfo first = processes.get(0);
        assertThat(first.pid()).isGreaterThanOrEqualTo(0);
        assertThat(first.name()).isNotBlank();
        assertThat(first.formattedRam()).isNotEmpty();
        assertThat(first.formattedCpu()).isNotEmpty();
    }

    @Test
    @DisplayName("Should sort processes by physical memory descending")
    void testSortByMemory() {
        List<ProcessInfo> processes = processService.getProcesses(20, ProcessMonitoringService.ProcessSortBy.MEMORY);

        assertThat(processes).isNotEmpty();
        if (processes.size() >= 2) {
            assertThat(processes.get(0).residentMemoryBytes())
                    .isGreaterThanOrEqualTo(processes.get(1).residentMemoryBytes());
        }
    }

    @Test
    @DisplayName("ProcessInfo impact badge categorization")
    void testProcessInfoImpact() {
        ProcessInfo normal = new ProcessInfo(1, "test", 2.0, 50 * 1024 * 1024, 100 * 1024 * 1024, 0, 0, 1, "RUNNING", "user", "");
        assertThat(normal.isSystemHog()).isFalse();
        assertThat(normal.getImpactLevel()).isEqualTo("Normal");

        ProcessInfo highCpu = new ProcessInfo(2, "cpu_hog", 35.0, 100 * 1024 * 1024, 200 * 1024 * 1024, 0, 0, 4, "RUNNING", "user", "");
        assertThat(highCpu.isSystemHog()).isTrue();
        assertThat(highCpu.isHighCpu()).isTrue();
        assertThat(highCpu.getImpactLevel()).isEqualTo("High CPU Load");

        ProcessInfo highRam = new ProcessInfo(3, "ram_hog", 5.0, 2L * 1024 * 1024 * 1024, 3L * 1024 * 1024 * 1024, 0, 0, 8, "RUNNING", "user", "");
        assertThat(highRam.isSystemHog()).isTrue();
        assertThat(highRam.isHighMemory()).isTrue();
        assertThat(highRam.getImpactLevel()).isEqualTo("Heavy Memory");
    }

    @Test
    @DisplayName("Should retrieve advanced process details for an authentic running process")
    void testInspectProcessDetails() {
        int currentPid = (int) ProcessHandle.current().pid();
        com.cleanpilot.model.DetailedProcessInfo details = processService.getProcessDetails(currentPid);

        assertThat(details).isNotNull();
        assertThat(details.pid()).isEqualTo(currentPid);
        assertThat(details.name()).isNotBlank();
        assertThat(details.formattedCpu()).isNotEmpty();
        assertThat(details.formattedRam()).isNotEmpty();
        assertThat(details.formattedUpTime()).isNotEmpty();
        assertThat(details.formattedStartTime()).isNotEmpty();
        assertThat(details.threadCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should accurately identify critical operating system processes")
    void testCriticalProcessSafetyClassification() {
        assertThat(processService.isCriticalProcess(0, "System Idle Process")).isTrue();
        assertThat(processService.isCriticalProcess(4, "System")).isTrue();
        assertThat(processService.isCriticalProcess(1, "init")).isTrue();
        assertThat(processService.isCriticalProcess(1044, "csrss.exe")).isTrue();
        assertThat(processService.isCriticalProcess(1088, "systemd")).isTrue();
        assertThat(processService.isCriticalProcess(1099, "services.exe")).isTrue();

        assertThat(processService.isCriticalProcess(5555, "notepad.exe")).isFalse();
        assertThat(processService.isCriticalProcess(6666, "chrome.exe")).isFalse();
    }
}
