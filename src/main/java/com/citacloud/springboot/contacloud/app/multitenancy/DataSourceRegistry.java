package com.citacloud.springboot.contacloud.app.multitenancy;
import com.zaxxer.hikari.*;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class DataSourceRegistry implements AutoCloseable {
    private final DatabaseSecretResolver secrets; private final Map<UUID,HikariDataSource> pools=new ConcurrentHashMap<>();
    public DataSourceRegistry(DatabaseSecretResolver secrets){this.secrets=secrets;}
    public DataSource get(DatabaseNode node){return pools.computeIfAbsent(node.id(),ignored->create(node));}
    public void evict(UUID nodeId){HikariDataSource ds=pools.remove(nodeId);if(ds!=null)ds.close();}
    public int poolCount(){return pools.size();}
    private HikariDataSource create(DatabaseNode node){DatabaseSecret secret=secrets.resolve(node);HikariConfig config=new HikariConfig();
        config.setPoolName("contacloud-"+node.code());config.setJdbcUrl(secret.jdbcUrl());config.setUsername(secret.username());config.setPassword(secret.password());
        config.setMaximumPoolSize(10);config.setMinimumIdle(1);config.setConnectionTimeout(10_000);return new HikariDataSource(config);}
    @Override public void close(){pools.values().forEach(HikariDataSource::close);pools.clear();}
}
