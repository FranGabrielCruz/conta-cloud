package com.citacloud.springboot.contacloud.app.multitenancy;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRoutingCacheTest {

    @Test
    void expiraEInvalidaRutasSinMezclarTenants() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-22T12:00:00Z"));
        TenantRoutingCache cache = new TenantRoutingCache(Duration.ofMinutes(5), clock);
        TenantRoute first = route(UUID.randomUUID());
        TenantRoute second = route(UUID.randomUUID());

        cache.put(first);
        cache.put(second);
        assertThat(cache.get(first.tenantId())).contains(first);
        assertThat(cache.get(second.tenantId())).contains(second);

        cache.invalidate(first.tenantId());
        assertThat(cache.get(first.tenantId())).isEmpty();
        assertThat(cache.get(second.tenantId())).contains(second);

        clock.advance(Duration.ofMinutes(6));
        assertThat(cache.get(second.tenantId())).isEmpty();
    }

    private TenantRoute route(UUID tenantId) {
        DatabaseNode node = new DatabaseNode(UUID.randomUUID(), "DB", "Nodo",
            DatabaseNodeType.COMPARTIDA, DatabaseNodeStatus.ACTIVA, "host", "database", "local",
            100, 1, java.math.BigDecimal.valueOf(90), "13", "secret", true);
        return new TenantRoute(tenantId, TenantStatus.ACTIVE, node);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
