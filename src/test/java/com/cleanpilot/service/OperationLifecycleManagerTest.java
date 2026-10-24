package com.cleanpilot.service;

import com.cleanpilot.model.OperationJournalEntity;
import com.cleanpilot.repository.OperationJournalRepository;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.OperationState;
import com.cleanpilot.safety.RiskClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationLifecycleManagerTest {

    @Mock
    private OperationJournalRepository repository;

    private OperationLifecycleManager lifecycleManager;

    @BeforeEach
    void setUp() {
        lifecycleManager = new OperationLifecycleManager(repository);
    }

    @Test
    @DisplayName("Should initialize operation in DISCOVER phase with unique operationId")
    void testStartOperation() {
        String opId = lifecycleManager.startOperation("DEVELOPER_CLEANUP", RiskClassification.MODERATE_RISK, "C:\\projects");

        assertThat(opId).isNotNull().isNotBlank();
        ArgumentCaptor<OperationJournalEntity> captor = ArgumentCaptor.forClass(OperationJournalEntity.class);
        verify(repository, times(1)).save(captor.capture());

        OperationJournalEntity entity = captor.getValue();
        assertThat(entity.getOperationId()).isEqualTo(opId);
        assertThat(entity.getState()).isEqualTo(OperationState.DISCOVER);
        assertThat(entity.getRiskLevel()).isEqualTo(RiskClassification.MODERATE_RISK);
    }

    @Test
    @DisplayName("Should advance through full lifecycle to COMPLETED_SUCCESSFULLY")
    void testSuccessfulLifecycle() {
        String opId = "test-op-123";
        OperationJournalEntity entity = new OperationJournalEntity(opId, "TEMP_CLEAN", RiskClassification.LOW_RISK, "C:\\Temp");
        when(repository.findByOperationId(opId)).thenReturn(Optional.of(entity));

        // 1. Record Preview
        OperationPreview preview = OperationPreview.builder("TEMP_CLEAN", RiskClassification.LOW_RISK)
                .operationId(opId)
                .targetCount(3)
                .estimatedBytes(2048, true)
                .build();
        lifecycleManager.recordPreview(opId, preview);
        assertThat(entity.getState()).isEqualTo(OperationState.PREVIEW);

        // 2. Authorize
        lifecycleManager.authorize(opId);
        assertThat(entity.getState()).isEqualTo(OperationState.AUTHORIZED);

        // 3. Begin Execution
        lifecycleManager.beginExecution(opId);
        assertThat(entity.getState()).isEqualTo(OperationState.EXECUTING);

        // 4. Record item progress
        lifecycleManager.recordItemProgress(opId, "C:\\Temp\\f1.tmp", true, 1024, "Cleaned");
        lifecycleManager.recordItemProgress(opId, "C:\\Temp\\f2.tmp", true, 1024, "Cleaned");
        assertThat(entity.getCompletedTargets()).isEqualTo(2);

        // 5. Complete operation
        lifecycleManager.completeOperation(opId, 2048, null);
        assertThat(entity.getState()).isEqualTo(OperationState.COMPLETED_SUCCESSFULLY);
        assertThat(entity.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("Should transition to PARTIALLY_COMPLETED when some items fail")
    void testPartiallyCompletedLifecycle() {
        String opId = "test-op-partial";
        OperationJournalEntity entity = new OperationJournalEntity(opId, "DEEP_CLEAN", RiskClassification.MODERATE_RISK, "C:\\Cache");
        when(repository.findByOperationId(opId)).thenReturn(Optional.of(entity));

        lifecycleManager.beginExecution(opId);
        lifecycleManager.recordItemProgress(opId, "target1", true, 500, "Cleaned");
        lifecycleManager.recordItemProgress(opId, "target2", false, 0, "Access Denied");

        lifecycleManager.completeOperation(opId, 500, List.of("One target failed"));

        assertThat(entity.getState()).isEqualTo(OperationState.PARTIALLY_COMPLETED);
        assertThat(entity.getCompletedTargets()).isEqualTo(1);
        assertThat(entity.getFailedTargets()).isEqualTo(1);
    }

    @Test
    @DisplayName("Startup reconciliation must detect incomplete operations and mark them INTERRUPTED without retrying")
    void testReconcileInterruptedOperations() {
        OperationJournalEntity crashedOp = new OperationJournalEntity("crashed-op-99", "CRITICAL_CLEAN", RiskClassification.HIGH_RISK, "C:\\Data");
        crashedOp.setState(OperationState.EXECUTING);
        crashedOp.setReconciled(false);

        when(repository.findInterruptedOperations()).thenReturn(List.of(crashedOp));

        lifecycleManager.reconcileInterruptedOperations();

        assertThat(crashedOp.getState()).isEqualTo(OperationState.INTERRUPTED);
        assertThat(crashedOp.isReconciled()).isTrue();
        assertThat(crashedOp.getSummaryMessage()).contains("interrupted by application shutdown");
        verify(repository, times(1)).save(crashedOp);
    }
}