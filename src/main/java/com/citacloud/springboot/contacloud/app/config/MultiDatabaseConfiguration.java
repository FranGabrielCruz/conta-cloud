package com.citacloud.springboot.contacloud.app.config;

import com.citacloud.springboot.contacloud.app.multitenancy.*;
import com.zaxxer.hikari.*;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name="contacloud.multidatabase.enabled",havingValue="true")
public class MultiDatabaseConfiguration {
    @Bean(name="directoryDataSource") DataSource directoryDataSource(
        @Value("${contacloud.directory.jdbc-url}") String url,
        @Value("${contacloud.directory.username}") String username,
        @Value("${contacloud.directory.password}") String password){
        HikariConfig c=new HikariConfig();c.setPoolName("contacloud-directory");c.setJdbcUrl(url);c.setUsername(username);c.setPassword(password);c.setMaximumPoolSize(5);return new HikariDataSource(c);
    }
    @Bean DirectoryGateway directoryGateway(@Qualifier("directoryDataSource") DataSource ds){Flyway.configure().dataSource(ds).locations("classpath:db/directory-migration").table("flyway_directory_history").baselineOnMigrate(true).load().migrate();var manager=new DataSourceTransactionManager(ds);return new JdbcDirectoryGateway(new JdbcTemplate(ds),new TransactionTemplate(manager));}
    @Bean TenantRoutingCache tenantRoutingCache(@Value("${contacloud.routing.cache-ttl:PT1M}") Duration ttl){return new TenantRoutingCache(ttl);}
    @Bean TenantDatabaseResolver tenantDatabaseResolver(DirectoryGateway gateway,TenantRoutingCache cache){return new TenantDatabaseResolver(gateway,cache);}
    @Bean DatabaseSecretResolver databaseSecretResolver(){return new EnvironmentDatabaseSecretResolver();}
    @Bean(destroyMethod="close") DataSourceRegistry dataSourceRegistry(DatabaseSecretResolver resolver){return new DataSourceRegistry(resolver);}
    @Bean @Primary DataSource dataSource(TenantDatabaseResolver resolver,DataSourceRegistry registry,
            @Value("${contacloud.multidatabase.bootstrap-tenant-id:}") String bootstrapTenantId){
        if(bootstrapTenantId==null||bootstrapTenantId.isBlank())throw new IllegalStateException("MULTIDATABASE_BOOTSTRAP_TENANT_ID es obligatorio al activar el modo multi-base.");
        TenantRoute bootstrapRoute=resolver.resolve(java.util.UUID.fromString(bootstrapTenantId.trim()));
        TenantRoutingDataSource ds=new TenantRoutingDataSource(resolver,registry,registry.get(bootstrapRoute.databaseNode()));ds.afterPropertiesSet();return ds;
    }
    @Bean DatabaseAllocationService databaseAllocationService(DirectoryGateway gateway,@Value("${contacloud.schema.required-version:}")String version){return new DatabaseAllocationService(gateway,version);}
    @Bean OperationalFlywayService operationalFlywayService(DataSourceRegistry registry){return new OperationalFlywayService(registry);}
    @Bean TenantProvisioningService tenantProvisioningService(DirectoryGateway gateway,DatabaseAllocationService allocation,DataSourceRegistry registry,TenantDatabaseResolver resolver,com.citacloud.springboot.contacloud.app.repositories.UsuarioRepository usuarios){return new TenantProvisioningService(gateway,allocation,registry,resolver,usuarios);}
}
