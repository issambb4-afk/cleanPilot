package com.cleanpilot.model;

import com.cleanpilot.safety.OperationState;
import com.cleanpilot.safety.RiskClassification;
import com.cleanpilot.ui.FormatUtils;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Persistent durable journal record tracking the execution lifecycle,
 * metrics, errors, and terminal states of maintenance operations.
 */
@Entity
@Table(name = "operation_journal", indexes = {
        @Index(name = "idx_op_id", columnList = "operationId", unique = true),
        @Index(name = "idx_op_state", columnList = "state"),
        @Index(name = "idx_op_start", columnList = "startTime")
})
public class OperationJournalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String operationId;

    @Column(nullable = false, length = 64)
    private String operationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RiskClassification riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OperationState state;

    @Column(nullable = false)
    private Instant startTime;

    private Instant endTime;

    @Column(length = 512)
    private String targetScope;

    private int totalTargets;
    private int completedTargets;
    private int failedTargets;
    private long bytesAffected;

    @Column(length = 4096)
    private String summaryMessage;

    @Column(length = 8192)
    private String detailsLog;

    private boolean reconciled;

    public OperationJournalEntity() {
    }

    public OperationJournalEntity(String operationId, String operationType, RiskClassification riskLevel, String targetScope) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.riskLevel = riskLevel;
        this.state = OperationState.DISCOVER;
        this.startTime = Instant.now();
        this.targetScope = targetScope;
        this.reconciled = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public RiskClassification getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskClassification riskLevel) {
        this.riskLevel = riskLevel;
    }

    public OperationState getState() {
        return state;
    }

    public void setState(OperationState state) {
        this.state = state;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getTargetScope() {
        return targetScope;
    }

    public void setTargetScope(String targetScope) {
        this.targetScope = targetScope;
    }

    public int getTotalTargets() {
        return totalTargets;
    }

    public void setTotalTargets(int totalTargets) {
        this.totalTargets = totalTargets;
    }

    public int getCompletedTargets() {
        return completedTargets;
    }

    public void setCompletedTargets(int completedTargets) {
        this.completedTargets = completedTargets;
    }

    public int getFailedTargets() {
        return failedTargets;
    }

    public void setFailedTargets(int failedTargets) {
        this.failedTargets = failedTargets;
    }

    public long getBytesAffected() {
        return bytesAffected;
    }

    public void setBytesAffected(long bytesAffected) {
        this.bytesAffected = bytesAffected;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public void setSummaryMessage(String summaryMessage) {
        this.summaryMessage = summaryMessage;
    }

    public String getDetailsLog() {
        return detailsLog;
    }

    public void setDetailsLog(String detailsLog) {
        this.detailsLog = detailsLog;
    }

    public boolean isReconciled() {
        return reconciled;
    }

    public void setReconciled(boolean reconciled) {
        this.reconciled = reconciled;
    }

    public String getFormattedStartTime() {
        return FormatUtils.formatInstant(startTime);
    }

    public String getFormattedBytes() {
        return FormatUtils.formatBytes(bytesAffected);
    }
}