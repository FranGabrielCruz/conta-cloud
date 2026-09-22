package com.citacloud.springboot.contacloud.app.multitenancy;
import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import java.util.UUID;
public class TenantDatabaseResolver {
    private final DirectoryGateway directory; private final TenantRoutingCache cache;
    public TenantDatabaseResolver(DirectoryGateway directory,TenantRoutingCache cache){this.directory=directory;this.cache=cache;}
    public TenantRoute resolve(UUID tenantId){
        TenantRoute route=cache.get(tenantId).orElseGet(()->{TenantRoute loaded=directory.findTenantRoute(tenantId)
            .orElseThrow(()->new ReglaNegocioException("El tenant no tiene una base de datos asignada."));cache.put(loaded);return loaded;});
        if(route.tenantStatus()!=TenantStatus.ACTIVE && route.tenantStatus()!=TenantStatus.PROVISIONING)
            throw new ReglaNegocioException("El tenant no está disponible para operar.");
        if(route.databaseNode().status()==DatabaseNodeStatus.INACTIVA)
            throw new ReglaNegocioException("La base de datos asignada no está disponible.");
        return route;
    }
    public void invalidate(UUID tenantId){cache.invalidate(tenantId);}
}
