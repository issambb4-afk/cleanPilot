package com.cleanpilot.service;

import com.cleanpilot.model.BootMetricInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootTraceProfilerServiceTest {

    private final BootTraceProfilerService service = new BootTraceProfilerService();

    @Test
    @DisplayName("Should gather boot metrics and return non-null evaluation")
    void shouldGetBootMetrics() {
        BootMetricInfo metrics = service.getBootMetrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.formattedTotalBootTime()).isNotEmpty();
        assertThat(metrics.formattedMainPath()).isNotEmpty();
        assertThat(metrics.bootEvaluationVerdict()).isNotBlank();
    }
}
