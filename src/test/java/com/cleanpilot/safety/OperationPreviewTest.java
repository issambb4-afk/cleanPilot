package com.cleanpilot.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationPreviewTest {

    @Test
    @DisplayName("OperationPreview builder creates immutable preview with defaults")
    void testOperationPreviewBuilder() {
        OperationPreview preview = OperationPreview.builder("APPX_DEBLOAT", RiskClassification.MODERATE_RISK)
                .targets(List.of("Microsoft.ZuneVideo", "Microsoft.BingNews"))
                .estimatedBytes(150_000_000L, true)
                .warnings(List.of("Apps will be removed for current user"))
                .build();

        assertThat(preview.operationType()).isEqualTo("APPX_DEBLOAT");
        assertThat(preview.riskClassification()).isEqualTo(RiskClassification.MODERATE_RISK);
        assertThat(preview.targetCount()).isEqualTo(2);
        assertThat(preview.estimatedBytes()).isEqualTo(150_000_000L);
        assertThat(preview.exactByteEstimate()).isTrue();
        assertThat(preview.targetDescriptions()).containsExactly("Microsoft.ZuneVideo", "Microsoft.BingNews");
        assertThat(preview.warnings()).hasSize(1);
        assertThat(preview.requiresElevation()).isFalse();
        assertThat(preview.irreversible()).isFalse();
    }

    @Test
    @DisplayName("RiskClassification properties enforce correct elevation and confirmation requirements")
    void testRiskClassificationProperties() {
        assertThat(RiskClassification.READ_ONLY.isConfirmationRequired()).isFalse();
        assertThat(RiskClassification.READ_ONLY.isRequiresElevation()).isFalse();

        assertThat(RiskClassification.LOW_RISK.isConfirmationRequired()).isFalse();

        assertThat(RiskClassification.MODERATE_RISK.isConfirmationRequired()).isTrue();
        assertThat(RiskClassification.MODERATE_RISK.isIrreversible()).isFalse();

        assertThat(RiskClassification.HIGH_RISK.isRequiresElevation()).isTrue();
        assertThat(RiskClassification.HIGH_RISK.isConfirmationRequired()).isTrue();

        assertThat(RiskClassification.IRREVERSIBLE.isIrreversible()).isTrue();
        assertThat(RiskClassification.IRREVERSIBLE.isConfirmationRequired()).isTrue();
    }
}