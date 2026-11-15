package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CONTACLOUD_POSTGRES_TESTS",matches="true")
class AsientoContablePostgresTest {
    private static PGSimpleDataSource ds;
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID(),moneda=UUID.randomUUID();
    private final LocalDate fecha=LocalDate.of(2026,11,15);

    @BeforeAll static void conectar() {
        ds=new PGSimpleDataSource();
        ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));
        ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));
        ds.setPassword(System.getenv("DB_PASSWORD"));
        assertThat(new JdbcTemplate(ds).queryForObject("select current_database()",String.class)).isEqualTo("conta_cloud");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void contabilizaRevierteYConservaPdfHistorico() {
        var jdbc=new JdbcTemplate(ds);
        var gestor=new DataSourceTransactionManager(ds);
        new TransactionTemplate(gestor).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            autenticar(tenant,empresa);
            var s=servicio(jdbc);
            var caja=cuenta(jdbc,"CASH_DEFAULT");
            var ventas=cuenta(jdbc,"SALES_REVENUE_DEFAULT");
            var e=entrada(caja,ventas,BigDecimal.TEN);
            var clave=UUID.randomUUID();
            var id=s.crear(e,clave);
            assertThat(s.crear(e,clave)).isEqualTo(id);
            assertThat(s.detalle(id).fila().estado()).isEqualTo(Estado.BORRADOR);
            assertThat(s.detalle(id).fila().numero()).isEqualTo("AS-000001");
            assertThat(s.listar(new Filtro(null,null,Tipo.DIARIO,Estado.BORRADOR,Origen.MANUAL,"AS-000001"),0,10)).hasSize(1);
            assertThat(ledger(jdbc,id)).isZero();
            var solicitud=UUID.randomUUID();
            assertThatThrownBy(()->s.contabilizar(id,0,solicitud)).hasMessageContaining("período fiscal abierto");
            abrirPeriodo(jdbc);
            s.contabilizar(id,0,solicitud);
            s.contabilizar(id,0,solicitud);
            assertThat(ledger(jdbc,id)).isEqualTo(2);
            assertThatThrownBy(()->s.editar(id,1,e)).hasMessageContaining("borrador");
            var savepoint=new TransactionTemplate(gestor);
            savepoint.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
            assertThatThrownBy(()->savepoint.executeWithoutResult(t->
                jdbc.update("UPDATE asiento_contable_linea SET debito=15 WHERE asiento_id=? AND orden=1",id)))
                .hasMessageContaining("inmutables");
            jdbc.update("UPDATE cuenta_contable SET nombre='Nombre actual' WHERE id=?",caja);
            var p=new AsientoContablePdfService(s,mock(LocalFileStorageService.class)).generar(id);
            try(var pdf=Loader.loadPDF(p.contenido())) {
                assertThat(new PDFTextStripper().getText(pdf)).contains("ASIENTO CONTABLE","AS-000001","Caja General","10.00")
                    .doesNotContain("ContaCloud","Nombre actual");
            } catch(java.io.IOException ex) {throw new AssertionError(ex);}
            var token=UUID.randomUUID();
            var reversa=s.reversar(id,1,fecha,"Corrección",token);
            assertThat(s.reversar(id,1,fecha,"Corrección",token)).isEqualTo(reversa);
            assertThat(s.detalle(id).fila().estado()).isEqualTo(Estado.REVERSADO);
            assertThat(s.detalle(id).numeroReversion()).isEqualTo("AS-000002");
            var d=s.detalle(reversa);
            assertThat(d.numeroOriginal()).isEqualTo("AS-000001");
            assertThat(d.lineas().getFirst().credito()).isEqualByComparingTo("10");
            assertThat(d.lineas().getLast().debito()).isEqualByComparingTo("10");
            assertThat(ledger(jdbc,id)).isEqualTo(2);
            assertThat(ledger(jdbc,reversa)).isEqualTo(2);
            autenticar(tenant,UUID.randomUUID());
            assertThatThrownBy(()->s.detalle(id)).isInstanceOf(RecursoNoEncontradoException.class);
        });
    }

    @Test void rechazaDescuadreYCuentasInvalidasYRegistraFuenteSoloUnaVez() {
        var jdbc=new JdbcTemplate(ds);
        var gestor=new DataSourceTransactionManager(ds);
        new TransactionTemplate(gestor).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            abrirPeriodo(jdbc);
            autenticar(tenant,empresa);
            var s=servicio(jdbc);
            var caja=cuenta(jdbc,"CASH_DEFAULT");
            var ventas=cuenta(jdbc,"SALES_REVENUE_DEFAULT");
            var agrupadora=cuenta(jdbc,"ASSETS_ROOT");
            assertThatThrownBy(()->s.crear(entrada(agrupadora,ventas,BigDecimal.TEN),UUID.randomUUID()))
                .hasMessageContaining("movimiento activa");
            assertThatThrownBy(()->s.crear(entrada(UUID.randomUUID(),ventas,BigDecimal.TEN),UUID.randomUUID()))
                .hasMessageContaining("movimiento activa");
            var id=s.crear(entrada(caja,ventas,BigDecimal.ONE),UUID.randomUUID());
            assertThatThrownBy(()->s.contabilizar(id,0,UUID.randomUUID())).hasMessageContaining("deben cuadrar");
            assertThat(s.detalle(id).fila().estado()).isEqualTo(Estado.BORRADOR);
            assertThat(ledger(jdbc,id)).isZero();
            var fuente=new Fuente(Origen.VENTAS,"FACTURA",UUID.randomUUID(),"FAC-125","EMISION");
            var lineas=List.of(new LineaConfigurada("CASH_DEFAULT",null,BigDecimal.TEN,BigDecimal.ZERO),
                new LineaConfigurada("SALES_REVENUE_DEFAULT",null,BigDecimal.ZERO,BigDecimal.TEN));
            var auto=s.registrarAutomatico(fuente,fecha,moneda,"Venta",lineas);
            assertThat(s.registrarAutomatico(fuente,fecha,moneda,"Venta",lineas)).isEqualTo(auto);
            assertThat(s.detalle(auto).fuente()).isEqualTo(fuente);
            assertThat(s.detalle(auto).reversible()).isFalse();
            assertThat(ledger(jdbc,auto)).isEqualTo(2);
            var savepoint=new TransactionTemplate(gestor);
            savepoint.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
            var fallida=mock(AuditoriaService.class);
            doThrow(new IllegalStateException("Auditoría no disponible")).when(fallida).registrar(eq("CONTABILIZAR"),anyString(),any(),anyString());
            var nuevo=s.crear(entrada(caja,ventas,BigDecimal.TEN),UUID.randomUUID());
            assertThatThrownBy(()->savepoint.executeWithoutResult(t->servicio(jdbc,fallida).contabilizar(nuevo,0,UUID.randomUUID())))
                .hasMessage("Auditoría no disponible");
            assertThat(ledger(jdbc,nuevo)).isZero();
            assertThat(s.detalle(nuevo).fila().estado()).isEqualTo(Estado.BORRADOR);
        });
    }

    @Test void serializaEscriturasConcurrentesMientrasElAsientoSeContabilizaYRevierte() throws Exception {
        var jdbc=new JdbcTemplate(ds);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        autenticar(tenant,empresa);
        var s=servicio(jdbc);
        var id=tx.execute(t->{
            preparar(jdbc);
            abrirPeriodo(jdbc);
            return s.crear(entrada(cuenta(jdbc,"CASH_DEFAULT"),cuenta(jdbc,"SALES_REVENUE_DEFAULT"),BigDecimal.TEN),
                UUID.randomUUID());
        });
        var reversado=new CountDownLatch(1);
        var liberar=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var primero=executor.submit(()->{
                autenticar(tenant,empresa);
                try {
                    tx.executeWithoutResult(t->{
                        t.setRollbackOnly();
                        s.contabilizar(id,0,UUID.randomUUID());
                        s.reversar(id,1,fecha,"Prueba de concurrencia",UUID.randomUUID());
                        reversado.countDown();
                        esperar(liberar);
                    });
                } finally {SecurityContextHolder.clearContext();}
            });
            try {
                esperar(reversado);
                var segundo=executor.submit(()->{
                    autenticar(tenant,empresa);
                    try {
                        assertThatThrownBy(()->tx.executeWithoutResult(t->{
                            jdbc.execute("SET LOCAL lock_timeout = '300ms'");
                            s.contabilizar(id,0,UUID.randomUUID());
                        })).hasMessageContaining("FOR NO KEY UPDATE");
                    } finally {SecurityContextHolder.clearContext();}
                });
                segundo.get(10,TimeUnit.SECONDS);
            } finally {liberar.countDown();}
            primero.get(10,TimeUnit.SECONDS);
            assertThat(s.detalle(id).fila().estado()).isEqualTo(Estado.BORRADOR);
            assertThat(ledger(jdbc,id)).isZero();
        } finally {
            tx.executeWithoutResult(t->{
                for(var tabla:List.of("auditoria","asiento_contable_linea","asiento_contable","asiento_secuencia",
                    "periodos_fiscales","cuenta_contable","configuracion_contable","usuarios","monedas","condiciones_pago","unit_of_measure",
                    "inventory_adjustment_reason","sales_payment_method","sales_return_reason"))
                    jdbc.update("DELETE FROM "+tabla+" WHERE empresa_id=?",empresa);
                jdbc.update("DELETE FROM empresas WHERE id=? AND tenant_id=?",empresa,tenant);
            });
        }
    }

    private static void esperar(CountDownLatch señal) {
        try {
            if(!señal.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Espera excedida.");
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private long ledger(JdbcTemplate j,UUID id) {
        return j.queryForObject("SELECT count(*) FROM asiento_movimiento_contable WHERE asiento_id=?",Long.class,id);
    }

    private Entrada entrada(UUID debe,UUID haber,BigDecimal credito) {
        return new Entrada(fecha,Tipo.DIARIO,moneda,"Ajuste de prueba","REF-01",null,List.of(
            new Movimiento(debe,"Débito",BigDecimal.TEN,BigDecimal.ZERO),
            new Movimiento(haber,"Crédito",BigDecimal.ZERO,credito)));
    }

    private UUID cuenta(JdbcTemplate j,String clave) {
        return j.queryForObject("SELECT cuenta_id FROM cuenta_contable_clave_empresa WHERE tenant_id=? AND empresa_id=? AND clave=?",
            UUID.class,tenant,empresa,clave);
    }

    private void preparar(JdbcTemplate j) {
        j.update("INSERT INTO empresas(id,tenant_id,codigo,nombre) VALUES (?,?,?,'Prueba asiento')",
            empresa,tenant,"TEST-"+empresa.toString().substring(0,8));
        j.update("INSERT INTO usuarios(id,tenant_id,empresa_id,usuario,nombre,password_hash) VALUES (?,?,?,'prueba','Prueba','inutilizado')",
            usuario,tenant,empresa);
        j.update("INSERT INTO monedas(id,tenant_id,empresa_id,codigo_iso,nombre,simbolo,moneda_base) VALUES (?,?,?,'USD','Dólar','$',true)",
            moneda,tenant,empresa);
    }

    private void abrirPeriodo(JdbcTemplate j) {
        j.update("""
            INSERT INTO periodos_fiscales(tenant_id,empresa_id,anio,mes,nombre,fecha_inicial,fecha_final,estado)
            VALUES (?,?,2026,11,'Noviembre',?,?,'ABIERTO')
            """,tenant,empresa,fecha.withDayOfMonth(1),fecha.withDayOfMonth(30));
    }

    private AsientoContableService servicio(JdbcTemplate j) {return servicio(j,new AuditoriaService(j));}

    private AsientoContableService servicio(JdbcTemplate j,AuditoriaService auditoria) {
        var named=new NamedParameterJdbcTemplate(j);
        var empresas=mock(EmpresaRepository.class);
        var e=mock(Empresa.class);
        when(e.getNombre()).thenReturn("Empresa de prueba");
        when(e.getZonaHoraria()).thenReturn("America/Caracas");
        when(empresas.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(e));
        return new AsientoContableService(new AsientoContableRepository(named),new CuentaContableRepository(named),empresas,
            mock(DatosEmpresaRepository.class),auditoria);
    }

    private void autenticar(UUID t,UUID e) {
        var p=new TenantPrincipal(usuario,t,e,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
}
