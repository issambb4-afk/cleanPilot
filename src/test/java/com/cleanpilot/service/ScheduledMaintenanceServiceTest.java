package com.cleanpilot.service;

import com.cleanpilot.model.SmartCleanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledMaintenanceServiceTest {

    @Mock private SmartCleanerService smartCleanerService;
    @Mock private QuarantineService quarantineService;
    @Mock private UserPreferencesService preferencesService;
    @Mock private SystemTrayManagerService trayManagerService;

    @Test
    void testExecuteMaintenanceRunsSuccessfully() {
        when(smartCleanerService.performSmartClean(any(SmartCleanerService.SmartCleanListener.class)))
                .thenReturn(new SmartCleanResult(
                        3L, 512L,
                        2L, 512L,
                        0L,
                        0L, 0L,
                        1024L,
                        1024L,
                        java.util.List.of("Cleaned temp")
                ));
        when(preferencesService.isAutoPurgeQuarantine()).thenReturn(true);
        when(quarantineService.purgeExpired()).thenReturn(2);
        when(preferencesService.isMaintenanceNotifications()).thenReturn(false);

        ScheduledMaintenanceService service = new ScheduledMaintenanceService(
                smartCleanerService,
                quarantineService,
                preferencesService,
                trayManagerService
        );

        SmartCleanResult result = service.executeMaintenance(true);
        assertThat(result).isNotNull();
        assertThat(result.totalBytesReclaimed()).isEqualTo(1024L);
        assertThat(result.totalFilesCleaned()).isEqualTo(5L);
    }
}
