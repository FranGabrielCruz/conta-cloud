package com.citacloud.springboot.contacloud.app.multitenancy;
import org.flywaydb.core.Flyway;
public class OperationalFlywayService {
    private final DataSourceRegistry registry;
    public OperationalFlywayService(DataSourceRegistry registry){this.registry=registry;}
    public String migrate(DatabaseNode node){var result=Flyway.configure().dataSource(registry.get(node)).locations("classpath:db/migration").load().migrate();return result.targetSchemaVersion==null?"":result.targetSchemaVersion;}
    public void validate(DatabaseNode node){Flyway.configure().dataSource(registry.get(node)).locations("classpath:db/migration").load().validate();}
}
