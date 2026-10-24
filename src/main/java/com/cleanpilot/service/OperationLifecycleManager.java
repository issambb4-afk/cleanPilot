package com.cleanpilot.service;

import com.cleanpilot.model.OperationJournalEntity;
import com.cleanpilot.repository.OperationJournalRepository;
import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.OperationState;
import com.cleanpilot.safety.RiskClassification;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the unified lifecycle (DISCOVER -> VALIDATE -> PREVIEW -> AUTHORIZE -> EXECUTE -> VERIFY -> COMPLETE)
 * and persists durable operation journal records with crash reconciliation.
 */
@Service
public class OperationLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(OperationLifecycleManager.class);

    private final OperationJournalRepository repository;

    public OperationLifecycleManager(OperationJournalRepository repository) {
        this.repository = repository;
    }

    /**
     * Reconciles interrupted operations from prior sessions on application startup.
     * Prevents blind auto-retries of incomplete destructive operations.
     */
    @PostConstruct
    @Transactional
    public void reconcileInterruptedOperations() {
        try {
            List<OperationJournalEntity> interrupted = repository.findInterruptedOperations();
            if (!interrupted.isEmpty()) {
                log.warn("RECONCILIATION: Found {} incomplete maintenance operation(s) from prior session.", interrupted.size());
                for (OperationJournalEntity op : interrupted) {
                    log.warn("Operation {} ({}) was interrupted in state {}.", op.getOperationId(), op.getOperationType(), op.getState());
                    op.setState(OperationState.INTERRUPTED);
                    op.setEndTime(Instant.now());
                    op.setReconciled(true);
                    op.setSummaryMessage("Operation was interrupted by application shutdown, termination, or power loss.");
                    repository.save(op);
                }
            }
        } catch (Exception e) {
            log.error("Failed to reconcile interrupted operations: {}", e.getMessage(), e);
        }
    }

    /**
     * Initializes a new maintenance operation in the DISCOVER phase.
     */
    @Transactional
    public String startOperation(String operationType, RiskClassification risk, String targetScope) {
        String opId = UUID.randomUUID().toString();
        OperationJournalEntity entity = new OperationJournalEntity(opId, operationType, risk, targetScope);
        repository.save(entity);
        log.info("Operation [{}] started: type={}, risk={}, scope={}", opId, operationType, risk, targetScope);
        return opId;
    }

    /**
     * Records the validated operation preview.
     */
    @Transactional
    public void recordPreview(String opId, OperationPreview preview) {
        if (opId == null || preview == null) return;
        repository.findByOperationId(opId).ifPresent(entity -> {
            entity.setState(OperationState.PREVIEW);
            entity.setTotalTargets(preview.targetCount());
            entity.setBytesAffected(preview.estimatedBytes());
            entity.setSummaryMessage("Preview generated: " + preview.targetCount() + " targets (" + preview.estimatedBytes() + " bytes)");
            repository.save(entity);
        });
    }

    /**
     * Marks an operation as AUTHORIZED by user consent.
     */
    @Transactional
    public void authorize(String opId) {
        if (opId == null) return;
        repository.findByOperationId(opId).ifPresent(entity -> {
            entity.setState(OperationState.AUTHORIZED);
            repository.save(entity);
            log.info("Operation [{}] authorized by user.", opId);
        });
    }

    /**
     * Transitions an operation to active execution.
     */
    @Transactional
    public void beginExecution(String opId) {
        if (opId == null) return;
        repository.findByOperationId(opId).ifPresent(entity -> {
            if (entity.getState() != OperationState.AUTHORIZED && entity.getState() != OperationState.PREVIEW) {
                log.warn("LIFECYCLE WARNING: Operation [{}] executed from unconfirmed state: {}", opId, entity.getState());
            }
            entity.setState(OperationState.EXECUTING);
            repository.save(entity);
            log.info("Operation [{}] began execution.", opId);
        });
    }

    /**
     * Updates per-item progress during execution.
     */
    @Transactional
    public void recordItemProgress(String opId, String target, boolean success, long bytesFreed, String note) {
        if (opId == null) return;
        repository.findByOperationId(opId).ifPresent(entity -> {
            if (success) {
                entity.setCompletedTargets(entity.getCompletedTargets() + 1);
            } else {
                entity.setFailedTargets(entity.getFailedTargets() + 1);
            }

            // Append bounded log details
            String logEntry = String.format("[%s] %s: %s (%d bytes)%n",
                    success ? "OK" : "FAIL",
                    target != null ? target : "Unknown",
                    note != null ? note : "",
                    bytesFreed
            );

            String existing = entity.getDetailsLog();
            if (existing == null) {
                entity.setDetailsLog(logEntry);
            } else if (existing.length() < 7500) {
                entity.setDetailsLog(existing + logEntry);
            }
            repository.save(entity);
        });
    }

    /**
     * Completes an operation with measured outcomes and verification.
     */
    @Transactional
    public void completeOperation(String opId, long finalBytesRecovered, List<String> warnings) {
        if (opId == null) return;
        repository.findByOperationId(opId).ifPresent(entity -> {
            entity.setEndTime(Instant.now());
            entity.setBytesAffected(finalBytesRecovered);

            boolean hasWarnings = (warnings != null && !warnings.isEmpty()) || entity.getFailedTargets() > 0;
            if (entity.getCompletedTargets() == 0 && entity.getFailedTargets() > 0) {
                entity.setState(OperationState.FAILED);
            } else if (entity.getFailedTargets() > 0 && entity.getCompletedTargets() > 0) {
                entity.setState(OperationState.PARTIALLY_COMPLETED);
            } else if (hasWarnings) {
                entity.setState(OperationState.COMPLETED_WITH_WARNINGS);
            } else {
                entity.setState(OperationState.COMPLETED_SUCCESSFULLY);
            }

            String summary = String.format("Completed: %d succeeded, %d failed. Reclaimed %d bytes.",
                    entity.getCompletedTargets(), entity.getFailedTargets(), finalBytesRecovered);
            entity.setSummaryMessage(summary);
            repository.save(entity);
            log.info("Operation [{}] completed with state: {}", opId, entity.getState());
        });
    }

    /**
     * Marks an operation as CANCELLED by user or system interruption.
     */
    @Transactional
    public void cancelOperation(String opId, String reason) {
        if (opId == null) return;
        repository.findByOperationId(opId).ifPresent(entity -> {
            entity.setState(OperationState.CANCELLED);
            entity.setEndTime(Instant.now());
            entity.setSummaryMessage("Operation cancelled: " + reason);
            repository.save(entity);
            log.info("Operation [{}] cancelled: {}", opId, reason);
        });
    }

    /**
     * Marks an operation as FAILED due to an unhandled exception or critical error.
     */
    @Transactional
    public void failOperation(String opId, String error, Throwable t) {
        if (opId == null) return;
        repository.findByOperationId(opId).ifPresent(entity -> {
            entity.setState(OperationState.FAILED);
            entity.setEndTime(Instant.now());
            entity.setSummaryMessage("Operation failed: " + error + (t != null ? " (" + t.getMessage() + ")" : ""));
            repository.save(entity);
            log.error("Operation [{}] failed: {}", opId, error, t);
        });
    }

    @Transactional(readOnly = true)
    public Optional<OperationJournalEntity> getOperation(String opId) {
        return repository.findByOperationId(opId);
    }

    @Transactional(readOnly = true)
    public List<OperationJournalEntity> getAllJournalEntries() {
        return repository.findAllByOrderByStartTimeDesc();
    }
}