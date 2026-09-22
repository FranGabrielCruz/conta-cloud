package com.citacloud.springboot.contacloud.app.multitenancy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DatabaseAllocationServiceTest {

    @Test
    void delegaReservaAutomaticaYManualConTipoSeguro() {
        DirectoryGateway directory = mock(DirectoryGateway.class);
        DatabaseAllocationService service = new DatabaseAllocationService(directory, "13");
        UUID shared = UUID.randomUUID();
        UUID dedicated = UUID.randomUUID();

        service.reserve(HostingMode.AUTOMATIC, null);
        service.reserve(HostingMode.MANUAL, shared);
        service.reserve(HostingMode.DEDICATED, dedicated);

        verify(directory).reserveAutomatic("13");
        verify(directory).reserveManual(shared, DatabaseNodeType.COMPARTIDA, "13");
        verify(directory).reserveManual(dedicated, DatabaseNodeType.DEDICADA, "13");
    }

    @Test
    void exigeNodoParaAsignacionExplicita() {
        DatabaseAllocationService service = new DatabaseAllocationService(mock(DirectoryGateway.class), "13");

        assertThatThrownBy(() -> service.reserve(HostingMode.MANUAL, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reserve(HostingMode.DEDICATED, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
