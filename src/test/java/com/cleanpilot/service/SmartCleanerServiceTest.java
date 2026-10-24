package com.cleanpilot.service;

import com.cleanpilot.model.SmartCleanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartCleanerServiceTest {

    @Mock
    private QuarantineService quarantineService;

    @Mock
    private ScanHistoryService scanHistoryService;

    private SmartCleanerService smartCleanerService;

    @BeforeEach
    void setUp() {
        smartCleanerService = new SmartCleanerService(quarantineService, scanHistoryService);
    }

    @Test
    @DisplayName("Should execute 1-Click Smart Clean routine and return valid result")
    void testPerformSmartClean() {
        when(quarantineService.purgeExpired()).thenReturn(3);

        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        SmartCleanResult result = smartCleanerService.performSmartClean((task, pct) -> {
            listenerCalled.set(true);
        });

        assertThat(result).isNotNull();
        assertThat(listenerCalled.get()).isTrue();
        assertThat(result.expiredQuarantineItemsPurged()).isEqualTo(3);
        assertThat(result.details()).isNotEmpty();
        assertThat(result.formattedTotalReclaimed()).isNotEmpty();
        assertThat(result.formattedMemoryTrimmed()).isNotEmpty();

        verify(quarantineService, times(1)).purgeExpired();
        verify(scanHistoryService, times(1)).saveScan(any(com.cleanpilot.model.ScanHistoryEntity.class));
    }

    @Test
    @DisplayName("Should execute read-only preview analysis without mutating files")
    void testAnalyzeSmartClean() {
        when(quarantineService.getExpiredItems()).thenReturn(java.util.Collections.emptyList());

        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        com.cleanpilot.model.SmartCleanAnalysis analysis = smartCleanerService.analyzeSmartClean((task, pct) -> {
            listenerCalled.set(true);
        });

        assertThat(analysis).isNotNull();
        assertThat(listenerCalled.get()).isTrue();
        assertThat(analysis.formattedTotalReclaimable()).isNotEmpty();
        assertThat(analysis.formattedMemoryReclaimable()).isNotEmpty();
        assertThat(analysis.formattedPrivacyBytes()).isNotEmpty();
        assertThat(analysis.formattedSpaceBytes()).isNotEmpty();
        assertThat(analysis.targetDetails()).isNotNull();

        // Verify quarantine purge was NOT called during read-only analysis
        verify(quarantineService, never()).purgeExpired();
        verify(scanHistoryService, never()).saveScan(any());
    }
}
