package com.cleanpilot.repository;

import com.cleanpilot.model.OperationJournalEntity;
import com.cleanpilot.safety.OperationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OperationJournalRepository extends JpaRepository<OperationJournalEntity, Long> {

    Optional<OperationJournalEntity> findByOperationId(String operationId);

    List<OperationJournalEntity> findByState(OperationState state);

    List<OperationJournalEntity> findAllByOrderByStartTimeDesc();

    @Query("SELECT o FROM OperationJournalEntity o WHERE o.state IN (" +
            "com.cleanpilot.safety.OperationState.DISCOVER, " +
            "com.cleanpilot.safety.OperationState.VALIDATE, " +
            "com.cleanpilot.safety.OperationState.PREVIEW, " +
            "com.cleanpilot.safety.OperationState.AUTHORIZED, " +
            "com.cleanpilot.safety.OperationState.EXECUTING, " +
            "com.cleanpilot.safety.OperationState.VERIFYING" +
            ") AND o.reconciled = false")
    List<OperationJournalEntity> findInterruptedOperations();
}