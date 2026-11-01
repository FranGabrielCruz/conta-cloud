package com.citacloud.springboot.contacloud.app.services;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import java.util.Map;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PrintAgentMappingTest {
 @Test void relacionMulticajaYConsultasSeValidanSinCrearBaseNiTablas(){var factory=new LocalContainerEntityManagerFactoryBean();var vendor=new HibernateJpaVendorAdapter();vendor.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");factory.setJpaVendorAdapter(vendor);factory.setDataSource(mock(DataSource.class));factory.setPackagesToScan("com.citacloud.springboot.contacloud.app.models");factory.setJpaPropertyMap(Map.of("hibernate.boot.allow_jdbc_metadata_access",false,"hibernate.hbm2ddl.auto","none"));try{factory.afterPropertiesSet();try(var em=factory.getObject().createEntityManager()){var q=em.createQuery("select distinct a from AgenteImpresion a join a.cajas c where a.tenantId=:tenant and a.empresaId=:empresa and c.id=:caja");assertThat(q).isNotNull();}}finally{factory.destroy();}}
}
