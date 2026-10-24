package com.cleanpilot.repository;

import com.cleanpilot.model.ScanHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanHistoryRepository extends JpaRepository<ScanHistoryEntity, Long> {

    List<ScanHistoryEntity> findAllByOrderByScanTimestampDesc();

    @Query("SELECT COALESCE(SUM(s.bytesRecovered), 0) FROM ScanHistoryEntity s")
    long getTotalBytesRecovered();

    @Query("SELECT COALESCE(SUM(s.itemsDeleted), 0) FROM ScanHistoryEntity s")
    long getTotalItemsDeleted();
}
