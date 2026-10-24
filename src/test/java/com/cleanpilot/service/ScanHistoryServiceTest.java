package com.cleanpilot.service;

import com.cleanpilot.model.ScanHistoryEntity;
import com.cleanpilot.repository.ScanHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanHistoryServiceTest {

    @Mock
    private ScanHistoryRepository repository;

    @InjectMocks
    private ScanHistoryService service;

    @Test
    @DisplayName("Save scan should invoke repository save")
    void testSaveScan() {
        ScanHistoryEntity entity = new ScanHistoryEntity("LARGE_FILES", "C:\\temp");
        when(repository.save(any(ScanHistoryEntity.class))).thenReturn(entity);

        ScanHistoryEntity saved = service.saveScan(entity);
        assertThat(saved).isNotNull();
        verify(repository, times(1)).save(entity);
    }

    @Test
    @DisplayName("Record deletion updates existing scan entity")
    void testRecordDeletion() {
        ScanHistoryEntity entity = new ScanHistoryEntity("LARGE_FILES", "C:\\temp");
        entity.setId(1L);
        entity.setItemsDeleted(2);
        entity.setBytesRecovered(1000);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        service.recordDeletion(1L, 3, 2000);

        assertThat(entity.getItemsDeleted()).isEqualTo(5);
        assertThat(entity.getBytesRecovered()).isEqualTo(3000);
        verify(repository, times(1)).save(entity);
    }

    @Test
    @DisplayName("Clear history deletes all repository entries")
    void testClearHistory() {
        service.clearHistory();
        verify(repository, times(1)).deleteAll();
    }

    @Test
    @DisplayName("Get all history delegates to repository ordered query")
    void testGetAllHistory() {
        when(repository.findAllByOrderByScanTimestampDesc()).thenReturn(List.of(new ScanHistoryEntity()));

        List<ScanHistoryEntity> history = service.getAllHistory();
        assertThat(history).hasSize(1);
    }
}
