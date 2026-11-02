package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL/Flyway schema, random scoped fixtures; never changes an existing operation. */
@EnabledIfEnvironmentVariable(named="CONTACLOUD_POSTGRES_TESTS",matches="true")
class ConsultaMovimientosCajaPostgresTest {
    static PGSimpleDataSource ds;static LocalContainerEntityManagerFactoryBean factory;
    static UUID tenant,empresa,sucursal,moneda,usuario;
    final UUID caja=UUID.randomUUID(),sesion=UUID.randomUUID(),anterior=UUID.randomUUID();
    JdbcTemplate jdbc;
    @BeforeAll static void connect(){
        ds=new PGSimpleDataSource();ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));ds.setPassword(System.getenv("DB_PASSWORD"));
        var j=new JdbcTemplate(ds);assertThat(j.queryForObject("select current_database()",String.class)).isEqualTo("conta_cloud");
        j.query("select e.tenant_id,e.id,s.id,m.id,u.id from empresas e join sucursales s on s.empresa_id=e.id join monedas m on m.empresa_id=e.id join usuario_empresa ue on ue.empresa_id=e.id and ue.activo=true join usuarios u on u.id=ue.usuario_id where e.activo=true and s.activo=true and m.activo=true and u.activo=true order by e.id,s.id,m.id,u.id limit 1",r->{tenant=r.getObject(1,UUID.class);empresa=r.getObject(2,UUID.class);sucursal=r.getObject(3,UUID.class);moneda=r.getObject(4,UUID.class);usuario=r.getObject(5,UUID.class);});
        assertThat(empresa).isNotNull();factory=new LocalContainerEntityManagerFactoryBean();var vendor=new HibernateJpaVendorAdapter();vendor.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");factory.setJpaVendorAdapter(vendor);factory.setDataSource(ds);factory.setPackagesToScan("com.citacloud.springboot.contacloud.app.models");factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));factory.afterPropertiesSet();
    }
    @AfterAll static void close(){if(factory!=null)factory.destroy();}
    @BeforeEach void fixtures(){
        jdbc=new JdbcTemplate(ds);
        jdbc.update("insert into cajas(id,tenant_id,empresa_id,sucursal_id,moneda_id,codigo,nombre,creado_por) values(?,?,?,?,?,?,?,?)",caja,tenant,empresa,sucursal,moneda,"TEST-"+caja.toString().substring(0,20),"TEST consulta movimientos",usuario);
        int shift=0;for(UUID id:List.of(sesion,anterior))jdbc.update("insert into cash_register_session(id,tenant_id,empresa_id,cash_register_id,branch_id,currency_id,business_date,shift_number,display_code,opened_by,opening_amount,status,closed_at,closed_by,expected_cash_amount,counted_cash_amount,difference_amount) values(?,?,?,?,?,?,current_date,?,?,?,5000,'CLOSED',current_timestamp,?,5000,5000,0)",id,tenant,empresa,caja,sucursal,moneda,++shift,"TEST-"+id,usuario,usuario);
        movement(sesion,"MANUAL","INCOME",1500);movement(sesion,"MANUAL","EXPENSE",750);movement(sesion,"SALES_PAYMENT","INCOME",20500);movement(anterior,"MANUAL","INCOME",99999);
        auth(tenant,empresa);
    }
    void auth(UUID t,UUID e){var p=new TenantPrincipal(usuario,t,e,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("movimientos_caja.ver"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    void movement(UUID session,String origin,String type,int amount){jdbc.update("insert into movimientos_financieros(id,tenant_id,empresa_id,tipo_movimiento,fecha,tipo_cuenta,caja_id,moneda_id,monto,concepto,referencia,estado,tipo_origen,origen_id,cash_register_session_id,payment_method,creado_por,actualizado_por) values(?,?,?,?,current_date,'CASH_REGISTER',?,?,?,'TEST consulta','TEST','REGISTERED',?,?,?,'CASH',?,?)",UUID.randomUUID(),tenant,empresa,type,caja,moneda,amount,origin,UUID.randomUUID(),session,usuario,usuario);}
    @AfterEach void clean(){SecurityContextHolder.clearContext();if(jdbc==null)return;
        jdbc.update("delete from movimientos_financieros where tenant_id=? and empresa_id=? and caja_id=?",tenant,empresa,caja);
        jdbc.update("delete from cash_register_session where tenant_id=? and empresa_id=? and cash_register_id=?",tenant,empresa,caja);
        jdbc.update("delete from cajas where tenant_id=? and empresa_id=? and id=?",tenant,empresa,caja);
    }
    MovimientoCajaService service(jakarta.persistence.EntityManager em){var f=new JpaRepositoryFactory(em);return new MovimientoCajaService(f.getRepository(CashRegisterSessionRepository.class),f.getRepository(FinancialMovementRepository.class),mock(ConceptoMovimientoCajaRepository.class),mock(IdempotentOperationService.class),mock(AuditoriaService.class),mock(MovimientoCajaNumeroService.class),f.getRepository(CashRegisterRepository.class));}
    @Test void agregadosPaginacionFiltrosYAislamientoConRepositoriosReales(){
        try(var em=factory.getObject().createEntityManager()){
            em.getTransaction().begin();em.createNativeQuery("set transaction isolation level repeatable read read only").executeUpdate();var service=service(em);
            var consulta=service.consultar(sesion,"",null,null,0,5);assertThat(consulta.resumen().efectivoEsperado()).isEqualByComparingTo("26250");
            assertThat(consulta.resumen().totalOperaciones()).isEqualByComparingTo("20500");assertThat(consulta.movimientos().getTotalElements()).isEqualTo(3);
            var filtrado=service.consultar(sesion,"",TipoMovimientoFinanciero.EXPENSE,null,0,5);assertThat(filtrado.movimientos().getTotalElements()).isEqualTo(1);assertThat(filtrado.resumen()).isEqualTo(consulta.resumen());
            var repo=new JpaRepositoryFactory(em).getRepository(FinancialMovementRepository.class);
            assertThat(repo.resumenEfectivoSesion(UUID.randomUUID(),empresa,sesion)).isEmpty();assertThat(repo.formasPagoSesion(tenant,UUID.randomUUID(),sesion)).isEmpty();
            auth(UUID.randomUUID(),UUID.randomUUID());assertThatThrownBy(()->service.consultar(sesion,"",null,null,0,5)).isInstanceOf(RecursoNoEncontradoException.class);
            em.getTransaction().rollback();
        }
    }
    @Test void consultaDeCajaNoReutilizaTurnosCerradosYRechazaCajaAjena(){
        try(var em=factory.getObject().createEntityManager()){
            em.getTransaction().begin();em.createNativeQuery("set transaction isolation level repeatable read read only").executeUpdate();var service=service(em);
            assertThat(service.consultarCaja(caja,"",null,null,0,10).resumen()).isNull();
            auth(UUID.randomUUID(),UUID.randomUUID());
            assertThatThrownBy(()->service.consultarCaja(caja,"",null,null,0,10)).isInstanceOf(RecursoNoEncontradoException.class);
            em.getTransaction().rollback();
        }
    }
    @ParameterizedTest @ValueSource(strings={"MANUAL","SALES_PAYMENT","CUSTOMER_RECEIPT","CASH_VOUCHER"})
    void escrituraConcurrenteNoBloqueaConsultaYNuevaActualizacionVeOperacion(String origen)throws Exception{
        try(var em=factory.getObject().createEntityManager()){
            em.getTransaction().begin();em.createNativeQuery("set transaction isolation level repeatable read read only").executeUpdate();var service=service(em);
            assertThat(service.consultar(sesion,"",null,null,0,5).resumen().efectivoEsperado()).isEqualByComparingTo("26250");
            boolean salida=origen.equals("CASH_VOUCHER");
            try(var pool=Executors.newSingleThreadExecutor()){pool.submit(()->movement(sesion,origen,salida?"EXPENSE":"INCOME",125)).get(10,TimeUnit.SECONDS);}
            var mismo=service.consultar(sesion,"",null,null,0,5);assertThat(mismo.resumen().efectivoEsperado()).isEqualByComparingTo("26250");assertThat(mismo.movimientos().getTotalElements()).isEqualTo(3);
            em.getTransaction().rollback();em.clear();em.getTransaction().begin();
            var nuevo=service.consultar(sesion,"",null,null,0,5);assertThat(nuevo.resumen().efectivoEsperado()).isEqualByComparingTo(salida?"26125":"26375");assertThat(nuevo.movimientos().getTotalElements()).isEqualTo(4);em.getTransaction().rollback();
        }
    }
}
