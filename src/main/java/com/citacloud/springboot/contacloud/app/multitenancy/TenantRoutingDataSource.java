package com.citacloud.springboot.contacloud.app.multitenancy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    private final TenantDatabaseResolver resolver; private final DataSourceRegistry registry; private final DataSource bootstrapDataSource;
    public TenantRoutingDataSource(TenantDatabaseResolver resolver,DataSourceRegistry registry,DataSource bootstrapDataSource){this.resolver=resolver;this.registry=registry;this.bootstrapDataSource=bootstrapDataSource;setTargetDataSources(Map.of());setDefaultTargetDataSource(bootstrapDataSource);}
    @Override protected Object determineCurrentLookupKey(){return TenantRoutingContext.currentTenantIdOrNull();}
    @Override protected DataSource determineTargetDataSource(){UUID tenantId=TenantRoutingContext.currentTenantIdOrNull();return tenantId==null?bootstrapDataSource:registry.get(resolver.resolve(tenantId).databaseNode());}
}
