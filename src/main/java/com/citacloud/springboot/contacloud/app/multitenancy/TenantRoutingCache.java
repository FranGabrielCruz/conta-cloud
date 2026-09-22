package com.citacloud.springboot.contacloud.app.multitenancy;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class TenantRoutingCache {
    private final Duration ttl; private final Clock clock;
    private final Map<UUID,Entry> entries = new ConcurrentHashMap<>();
    public TenantRoutingCache(Duration ttl) { this(ttl, Clock.systemUTC()); }
    TenantRoutingCache(Duration ttl, Clock clock) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("El TTL debe ser positivo");
        this.ttl=ttl; this.clock=clock;
    }
    public Optional<TenantRoute> get(UUID tenantId) {
        Entry entry=entries.get(tenantId);
        if(entry==null)return Optional.empty();
        if(!entry.expiresAt().isAfter(clock.instant())){entries.remove(tenantId,entry);return Optional.empty();}
        return Optional.of(entry.route());
    }
    public void put(TenantRoute route){entries.put(route.tenantId(),new Entry(route,clock.instant().plus(ttl)));}
    public void invalidate(UUID tenantId){entries.remove(tenantId);}
    public void clear(){entries.clear();}
    private record Entry(TenantRoute route,Instant expiresAt){}
}
