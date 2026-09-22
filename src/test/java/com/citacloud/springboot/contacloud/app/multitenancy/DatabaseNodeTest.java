package com.citacloud.springboot.contacloud.app.multitenancy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseNodeTest {

    @Test
    void aceptaSoloNodosActivosSanosYDebajoDelUmbral() {
        assertThat(node(DatabaseNodeStatus.ACTIVA, true, 79).acceptsNewTenant()).isTrue();
        assertThat(node(DatabaseNodeStatus.ACTIVA, true, 80).acceptsNewTenant()).isFalse();
        assertThat(node(DatabaseNodeStatus.DRENANDO, true, 10).acceptsNewTenant()).isFalse();
        assertThat(node(DatabaseNodeStatus.ACTIVA, false, 10).acceptsNewTenant()).isFalse();
    }

    private DatabaseNode node(DatabaseNodeStatus status, boolean healthy, int tenants) {
        return new DatabaseNode(UUID.randomUUID(), "DB01", "Operacional 01",
            DatabaseNodeType.COMPARTIDA, status, "host-ref", "contacloud_01", "local",
            100, tenants, BigDecimal.valueOf(80), "13", "DB01", healthy);
    }
}
