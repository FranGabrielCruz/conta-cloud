package com.citacloud.springboot.contacloud.app.multitenancy;

import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantDatabaseResolverTest {

    @Test
    void resuelveCadaTenantEnSuNodoYUsaCache() {
        DirectoryGateway directory = mock(DirectoryGateway.class);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        TenantRoute routeA = route(tenantA, "DB-A", TenantStatus.ACTIVE);
        TenantRoute routeB = route(tenantB, "DB-B", TenantStatus.ACTIVE);
        when(directory.findTenantRoute(tenantA)).thenReturn(Optional.of(routeA));
        when(directory.findTenantRoute(tenantB)).thenReturn(Optional.of(routeB));
        TenantDatabaseResolver resolver = new TenantDatabaseResolver(directory,
            new TenantRoutingCache(Duration.ofMinutes(5)));

        assertThat(resolver.resolve(tenantA).databaseNode().code()).isEqualTo("DB-A");
        assertThat(resolver.resolve(tenantB).databaseNode().code()).isEqualTo("DB-B");
        assertThat(resolver.resolve(tenantA)).isSameAs(routeA);
        verify(directory, times(1)).findTenantRoute(tenantA);
        verify(directory, times(1)).findTenantRoute(tenantB);
    }

    @Test
    void rechazaTenantInexistenteOInactivo() {
        DirectoryGateway directory = mock(DirectoryGateway.class);
        UUID missing = UUID.randomUUID();
        UUID inactive = UUID.randomUUID();
        when(directory.findTenantRoute(missing)).thenReturn(Optional.empty());
        when(directory.findTenantRoute(inactive)).thenReturn(Optional.of(route(inactive, "DB-X", TenantStatus.SUSPENDED)));
        TenantDatabaseResolver resolver = new TenantDatabaseResolver(directory,
            new TenantRoutingCache(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> resolver.resolve(missing)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> resolver.resolve(inactive)).isInstanceOf(ReglaNegocioException.class);
    }

    private TenantRoute route(UUID tenantId, String code, TenantStatus status) {
        DatabaseNode node = new DatabaseNode(UUID.randomUUID(), code, "Nodo " + code,
            DatabaseNodeType.COMPARTIDA, DatabaseNodeStatus.ACTIVA, "host", code, "local",
            100, 1, java.math.BigDecimal.valueOf(90), "13", code, true);
        return new TenantRoute(tenantId, status, node);
    }
}
