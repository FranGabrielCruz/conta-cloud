package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.DepreciacionRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
class DepreciacionPostgresTest {
    private static PGSimpleDataSource ds;
    private final UUID tenant=UUID.randomUUID();
    private final UUID empresa=UUID.randomUUID();
    private final UUID sucursal=UUID.randomUUID();
    private final UUID moneda=UUID.randomUUID();
    private final UUID usuario=UUID.randomUUID();
    private final YearMonth periodo=YearMonth.now(java.time.ZoneId.of("America/Santo_Domingo")).minusMonths(2);

    @BeforeAll
    static void conectar() {
        ds=new PGSimpleDataSource();
        ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));
        ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));
        ds.setPassword(System.getenv("DB_PASSWORD"));
        assertThat(new JdbcTemplate(ds).queryForObject("select current_database()",String.class)).isEqualTo("conta_cloud");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void procesaSoloElegiblesConHistorialInmutableYControlDeAlcance() {
        var jdbc=new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            var laptop=activo(jdbc,"AF-000001","Laptop histórica",true,periodo);
            activo(jdbc,"AF-000002","Terreno",false,periodo);
            activo(jdbc,"AF-000003","Vehículo futuro",true,periodo.plusMonths(1));
            var baja=activo(jdbc,"AF-000004","Activo retirado",true,periodo.minusMonths(2));
            jdbc.update("""
                INSERT INTO activo_fijo_movimiento(tenant_id,empresa_id,activo_id,sucursal_id,tipo,fecha,descripcion,importe)
                VALUES (?,?,?,?,'BAJA',?,'Baja de prueba',0)
                """,tenant,empresa,baja,sucursal,periodo.minusMonths(1).atEndOfMonth());
            autenticar(tenant,empresa,true,Set.of());
            var svc=servicio(jdbc,new AuditoriaService(jdbc));
            var previa=svc.calcular(periodo);
            assertThat(previa.lineas()).singleElement().satisfies(l->{
                assertThat(l.activo()).isEqualTo(laptop);
                assertThat(l.importe()).isEqualByComparingTo("2000.00");
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM activo_depreciacion_proceso WHERE empresa_id=?",Long.class,empresa)).isZero();
            jdbc.update("UPDATE activo_fijo SET nombre='Laptop corregida' WHERE id=?",laptop);
            assertThatThrownBy(()->svc.procesar(periodo,previa.revision(),previa.solicitud()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambiaron");
            var revisada=svc.calcular(periodo);
            var id=svc.procesar(periodo,revisada.revision(),revisada.solicitud());
            assertThat(svc.procesar(periodo,revisada.revision(),revisada.solicitud())).isEqualTo(id);
            assertThatThrownBy(()->svc.procesar(periodo,revisada.revision(),UUID.randomUUID()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("procesado");
            assertThat(svc.siguiente()).isEqualTo(periodo.plusMonths(1));
            assertThat(svc.historial(null,null,0,10)).singleElement().satisfies(f->assertThat(f.importe()).isEqualByComparingTo("2000"));
            assertThat(svc.indicadores().acumulados()).singleElement().satisfies(s->assertThat(s.importe()).isEqualByComparingTo("2000"));
            assertThat(jdbc.queryForObject("SELECT importe FROM activo_fijo_movimiento WHERE depreciacion_proceso_id=?",BigDecimal.class,id))
                .isEqualByComparingTo("2000");
            assertThat(jdbc.queryForObject("SELECT detalle->>'resultado' FROM auditoria WHERE registro_id=?",String.class,id))
                .isEqualTo("PROCESADO");
            jdbc.update("UPDATE activo_fijo SET nombre='Nombre actual' WHERE id=?",laptop);
            assertThat(svc.documento(id).lineas()).singleElement().satisfies(l->{
                assertThat(l.nombre()).isEqualTo("Laptop corregida");
                assertThat(l.libros()).isEqualByComparingTo("148000");
            });
            autenticar(UUID.randomUUID(),empresa,true,Set.of());
            assertThatThrownBy(()->svc.detalle(id)).isInstanceOf(RecursoNoEncontradoException.class);
            autenticar(tenant,UUID.randomUUID(),true,Set.of());
            assertThatThrownBy(()->svc.documento(id)).isInstanceOf(RecursoNoEncontradoException.class);
            autenticar(tenant,empresa,false,Set.of(UUID.randomUUID()));
            assertThat(svc.historial(null,null,0,10)).isEmpty();
            assertThatThrownBy(()->svc.detalle(id)).isInstanceOf(RecursoNoEncontradoException.class);
            autenticar(tenant,empresa,false,Set.of(sucursal));
            assertThat(svc.detalle(id).lineas()).hasSize(1);
        });
    }

    @Test
    void dosSolicitudesConcurrentesSoloRegistranUnProceso() throws Exception {
        var jdbc=new JdbcTemplate(ds);
        var transacciones=new TransactionTemplate(new DataSourceTransactionManager(ds));
        transacciones.executeWithoutResult(tx->{
            preparar(jdbc);
            activo(jdbc,"AF-000001","Activo concurrente",true,periodo);
        });
        try (var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            autenticar(tenant,empresa,true,Set.of());
            var svc=servicio(jdbc,new AuditoriaService(jdbc));
            var previa=svc.calcular(periodo);
            var listo=new java.util.concurrent.CountDownLatch(2);
            var inicio=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<String> procesar=()->{
                autenticar(tenant,empresa,true,Set.of());
                try {
                    listo.countDown();
                    if (!inicio.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Espera excedida");
                    return transacciones.execute(tx->svc.procesar(periodo,previa.revision(),UUID.randomUUID())).toString();
                } catch (ReglaNegocioException ex) {
                    return ex.getMessage();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            var uno=executor.submit(procesar);
            var dos=executor.submit(procesar);
            boolean ambos=listo.await(10,java.util.concurrent.TimeUnit.SECONDS);
            inicio.countDown();
            assertThat(ambos).isTrue();
            var resultados=java.util.List.of(uno.get(15,java.util.concurrent.TimeUnit.SECONDS),
                dos.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(r->r.contains("ya fue procesado"))).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM activo_depreciacion_proceso WHERE empresa_id=?",Long.class,empresa))
                .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT sum(importe) FROM activo_fijo_movimiento WHERE empresa_id=?",BigDecimal.class,empresa))
                .isEqualByComparingTo("2000");
        } finally {
            transacciones.executeWithoutResult(tx->{
                for (var tabla:java.util.List.of("auditoria","activo_fijo_movimiento","activo_depreciacion_detalle",
                    "activo_depreciacion_proceso","activo_fijo","periodos_fiscales","usuarios","monedas","sucursales",
                    "condiciones_pago","unit_of_measure","inventory_adjustment_reason","sales_payment_method","sales_return_reason"))
                    jdbc.update("DELETE FROM "+tabla+" WHERE empresa_id=?",empresa);
                jdbc.update("DELETE FROM cuenta_contable WHERE empresa_id=?",empresa);
                jdbc.update("DELETE FROM empresas WHERE id=? AND tenant_id=?",empresa,tenant);
            });
        }
    }

    @Test
    void revierteCabeceraDetalleYMovimientoSiFallaLaAuditoria() {
        var jdbc=new JdbcTemplate(ds);
        var transacciones=new TransactionTemplate(new DataSourceTransactionManager(ds));
        transacciones.executeWithoutResult(ext->{
            ext.setRollbackOnly();
            preparar(jdbc);
            activo(jdbc,"AF-000001","Activo",true,periodo);
            autenticar(tenant,empresa,true,Set.of());
            var auditoria=mock(AuditoriaService.class);
            doThrow(new IllegalStateException("Fallo de prueba")).when(auditoria).registrar(anyString(),anyString(),any(),anyString());
            var svc=servicio(jdbc,auditoria);
            var previa=svc.calcular(periodo);
            var operacion=new TransactionTemplate(new DataSourceTransactionManager(ds));
            operacion.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
            assertThatThrownBy(()->operacion.execute(tx->svc.procesar(periodo,previa.revision(),previa.solicitud())))
                .isInstanceOf(IllegalStateException.class).hasMessage("Fallo de prueba");
            for (var tabla: java.util.List.of("activo_depreciacion_proceso","activo_depreciacion_detalle","activo_fijo_movimiento"))
                assertThat(jdbc.queryForObject("SELECT count(*) FROM "+tabla+" WHERE empresa_id=?",Long.class,empresa)).isZero();
        });
    }

    private DepreciacionService servicio(JdbcTemplate jdbc,AuditoriaService auditoria) {
        var empresas=mock(EmpresaRepository.class);
        var e=mock(Empresa.class);
        when(e.getNombre()).thenReturn("Empresa de depreciación de prueba");
        when(e.getZonaHoraria()).thenReturn("America/Santo_Domingo");
        when(empresas.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(e));
        return new DepreciacionService(new DepreciacionRepository(new NamedParameterJdbcTemplate(jdbc)),
            empresas,mock(DatosEmpresaRepository.class),auditoria,mock(ContabilizacionOperativaService.class));
    }

    private void preparar(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO empresas(id,tenant_id,codigo,nombre) VALUES (?,?,?,?)",
            empresa,tenant,"PR-"+empresa.toString().substring(0,8),"Empresa temporal");
        jdbc.update("INSERT INTO sucursales(id,tenant_id,empresa_id,codigo,nombre) VALUES (?,?,?,'01','Principal')",sucursal,tenant,empresa);
        jdbc.update("INSERT INTO monedas(id,tenant_id,empresa_id,codigo_iso,nombre,simbolo) VALUES (?,?,?,'DOP','Peso','RD$')",
            moneda,tenant,empresa);
        jdbc.update("INSERT INTO usuarios(id,tenant_id,empresa_id,usuario,nombre,password_hash) VALUES (?,?,?,'prueba','Prueba','inutilizado')",
            usuario,tenant,empresa);
        jdbc.update("""
            INSERT INTO periodos_fiscales(tenant_id,empresa_id,anio,mes,fecha_inicial,fecha_final,estado,nombre)
            VALUES (?,?,?,?,?,?,'ABIERTO','Mes de prueba')
            """,tenant,empresa,periodo.getYear(),periodo.getMonthValue(),periodo.atDay(1),periodo.atEndOfMonth());
    }

    private UUID activo(JdbcTemplate jdbc,String codigo,String nombre,boolean deprecia,YearMonth inicio) {
        var id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO activo_fijo(id,tenant_id,empresa_id,codigo,nombre,categoria,fecha_adquisicion,costo,moneda_id,
               sucursal_id,depreciable,metodo,vida_meses,residual,inicio_depreciacion)
            VALUES (?,?,?,?,?,'TECNOLOGIA',?,150000,?,?,?,?,?,?,?)
            """,id,tenant,empresa,codigo,nombre,inicio.atDay(1),moneda,sucursal,deprecia,deprecia?"LINEA_RECTA":null,
            deprecia?60:null,deprecia?new BigDecimal("30000"):null,deprecia?inicio.atDay(1):null);
        return id;
    }

    private void autenticar(UUID t,UUID e,boolean todas,Set<UUID> sucursales) {
        var p=new TenantPrincipal(usuario,t,e,null,"TEST","Prueba","test","",true,todas,sucursales,Set.of());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
}
