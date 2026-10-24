package com.cleanpilot.service;

import com.cleanpilot.model.SystemIntegrityReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SystemIntegrityRepairServiceTest {

    private final SystemIntegrityRepairService service = new SystemIntegrityRepairService();

    @Test
    @DisplayName("Should scan system health and return structured report")
    void shouldScanSystemHealth() {
        AtomicBoolean progressNotified = new AtomicBoolean(false);
        SystemIntegrityReport report = service.scanSystemHealth(msg -> progressNotified.set(true));

        assertThat(report).isNotNull();
        assertThat(report.statusMessage()).isNotBlank();
        assertThat(report.scanTimestamp()).isNotBlank();
        assertThat(progressNotified.get()).isTrue();
    }
}
