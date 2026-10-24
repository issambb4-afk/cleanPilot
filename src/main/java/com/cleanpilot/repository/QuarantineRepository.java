package com.cleanpilot.repository;

import com.cleanpilot.model.QuarantinedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QuarantineRepository extends JpaRepository<QuarantinedItem, Long> {

    List<QuarantinedItem> findAllByOrderByDeletedAtDesc();

    @Query("SELECT q FROM QuarantinedItem q WHERE q.expiresAt <= :now")
    List<QuarantinedItem> findExpiredItems(@Param("now") LocalDateTime now);

    @Query("SELECT COALESCE(SUM(q.fileSizeBytes), 0) FROM QuarantinedItem q")
    long getTotalQuarantineSizeBytes();
}
