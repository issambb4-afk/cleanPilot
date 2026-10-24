package com.cleanpilot.service;

import com.cleanpilot.model.ActiveSocketInfo;
import com.cleanpilot.model.NetworkOptimizationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkOptimizationServiceTest {

    private final NetworkOptimizationService service = new NetworkOptimizationService();

    @Test
    @DisplayName("Should enumerate TCP sockets and map owning processes")
    void shouldScanActiveSockets() {
        List<ActiveSocketInfo> sockets = service.scanActiveSockets();
        assertThat(sockets).isNotNull();

        for (ActiveSocketInfo s : sockets) {
            assertThat(s.protocol()).isNotBlank();
            assertThat(s.localAddress()).isNotBlank();
            assertThat(s.state()).isNotBlank();
            assertThat(s.displayProcess()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Should run network stack optimization safely")
    void shouldOptimizeNetworkStack() {
        NetworkOptimizationResult result = service.optimizeNetworkStack();
        assertThat(result).isNotNull();
        assertThat(result.statusMessage()).isNotBlank();
        assertThat(result.closedWaitSocketsCount()).isGreaterThanOrEqualTo(0);
    }
}
