package com.cleanpilot.service;

import com.cleanpilot.model.ComponentStoreReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsComponentStoreServiceTest {

    private final WindowsComponentStoreService service = new WindowsComponentStoreService();

    @Test
    @DisplayName("Should analyze component store and return valid report structure")
    void shouldAnalyzeComponentStore() {
        ComponentStoreReport report = service.analyzeComponentStore();
        assertThat(report).isNotNull();
        assertThat(report.summaryText()).isNotBlank();
        assertThat(report.formattedActualStoreSize()).isNotEmpty();
        assertThat(report.formattedReclaimable()).isNotEmpty();
    }

    @Test
    @DisplayName("Should calculate cache footprint safely")
    void shouldCalculateCacheFootprint() {
        long footprint = service.calculateCacheFootprint();
        assertThat(footprint).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Should execute cleanup with progress listener without throwing")
    void shouldExecuteCleanupSafely() {
        AtomicBoolean progressNotified = new AtomicBoolean(false);
        // Runs in dryRun test mode to isolate test from mutating live OS directories
        boolean result = service.cleanupComponentStore(msg -> {
            progressNotified.set(true);
        }, true);
        assertThat(result).isTrue();
        assertThat(progressNotified.get()).isTrue();
    }
}
