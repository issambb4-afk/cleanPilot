package com.cleanpilot.service;

import com.cleanpilot.model.ScanHistoryEntity;
import com.cleanpilot.repository.ScanHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing historical scan records and audit logs.
 */
@Service
public class ScanHistoryService {

    private final ScanHistoryRepository repository;

    public ScanHistoryService(ScanHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ScanHistoryEntity saveScan(ScanHistoryEntity entity) {
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ScanHistoryEntity> getAllHistory() {
        return repository.findAllByOrderByScanTimestampDesc();
    }

    @Transactional(readOnly = true)
    public long getTotalBytesRecovered() {
        return repository.getTotalBytesRecovered();
    }

    @Transactional(readOnly = true)
    public long getTotalItemsDeleted() {
        return repository.getTotalItemsDeleted();
    }

    @Transactional
    public void recordDeletion(Long scanId, long itemsDeleted, long bytesRecovered) {
        if (scanId != null) {
            repository.findById(scanId).ifPresent(entity -> {
                entity.setItemsDeleted(entity.getItemsDeleted() + itemsDeleted);
                entity.setBytesRecovered(entity.getBytesRecovered() + bytesRecovered);
                repository.save(entity);
            });
        }
    }

    @Transactional
    public void clearHistory() {
        repository.deleteAll();
    }
}
