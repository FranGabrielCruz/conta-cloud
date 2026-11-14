package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.BajaActivoDto.*;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
class BajaActivoPostgresTest {
    private static PGSimpleDataSource ds;
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),sucursal=UUID.randomUUID(),
        moneda=UUID.randomUUID(),usuario=UUID.randomUUID(),activo=UUID.randomUUID();
    private final LocalDate hoy=LocalDate.now(java.time.ZoneId.of("America/Santo_Domingo"));

    @BeforeAll static void conectar() {
        ds=new PGSimpleDataSource();
        ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));
        ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));
        ds.setPassword(System.getenv("DB_PASSWORD"));
        assertThat(new JdbcTemplate(ds).queryForObject("select current_database()",String.class)).isEqualTo("conta_cloud");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @AfterEach void limpiar(){ SecurityContextHolder.clearContext(); }

    @Test
    void dosBorradoresConcurrentesSoloConfirmanUnaBaja() throws Exception {
        var jdbc = new JdbcTemplate(ds);
        var transaccion = new TransactionTemplate(new DataSourceTransactionManager(ds));
        autenticar(tenant, empresa, true);
        var servicio = servicio(jdbc, new AuditoriaService(jdbc));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var borradores = transaccion.execute(tx -> {
                preparar(jdbc);
                var entrada = new Entrada(activo, "ROBO", hoy, "Disposición", BigDecimal.ZERO, null, null, null);
                return List.of(servicio.revisar(servicio.crear(entrada)), servicio.revisar(servicio.crear(entrada)));
            });
            var inicio = new CountDownLatch(1);
            var primero = executor.submit(() -> confirmarConcurrente(inicio, transaccion, servicio, borradores.getFirst()));
            var segundo = executor.submit(() -> confirmarConcurrente(inicio, transaccion, servicio, borradores.getLast()));
            inicio.countDown();
            assertThat(List.of(primero.get(15, TimeUnit.SECONDS), segundo.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM activo_baja WHERE activo_id=? AND estado='CONFIRMADA'",
                Long.class, activo)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM activo_fijo_movimiento WHERE activo_id=? AND tipo='BAJA'",
                Long.class, activo)).isEqualTo(1);
        } finally {
            transaccion.executeWithoutResult(tx -> {
                for (var tabla : List.of("auditoria", "activo_fijo_movimiento", "activo_baja", "activo_baja_secuencia",
                        "activo_fijo", "usuarios", "monedas", "sucursales", "condiciones_pago", "unit_of_measure",
                        "inventory_adjustment_reason", "sales_payment_method", "sales_return_reason"))
                    jdbc.update("DELETE FROM " + tabla + " WHERE empresa_id=?", empresa);
                jdbc.update("DELETE FROM empresas WHERE id=? AND tenant_id=?", empresa, tenant);
            });
        }
    }

    private boolean confirmarConcurrente(CountDownLatch inicio, TransactionTemplate transaccion,
                                          BajaActivoService servicio, Revision revision) throws InterruptedException {
        if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Espera excedida.");
        autenticar(tenant, empresa, true);
        try {
            transaccion.executeWithoutResult(tx -> servicio.confirmar(revision, UUID.randomUUID()));
            return true;
        } catch (ReglaNegocioException ex) {
            assertThat(ex).hasMessageContaining("ya está dado de baja");
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void confirmaUnaVezYConservaElHistorial() {
        var jdbc = new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            preparar(jdbc);
            autenticar(tenant, empresa, true);
            var s = servicio(jdbc, new AuditoriaService(jdbc));
            assertThat(s.buscarActivos("SER-123", 0, 10)).hasSize(1);
            var entrada = new Entrada(activo, "VENTA", hoy, "Disposición", new BigDecimal("140000"), "ACTA-01", null, null);
            var id = s.crear(entrada);
            var otro = s.crear(entrada);
            assertThat(s.detalle(id).estado()).isEqualTo(Estado.BORRADOR);
            assertThat(s.listar(new Filtro(null, null, "VENTA", Estado.BORRADOR, "SER-123"), 0, 10)).hasSize(2);
            assertThat(s.totales()).isEmpty();
            assertThatThrownBy(() -> s.documento(id)).isInstanceOf(ReglaNegocioException.class);
            s.editar(id, 0, entrada);
            var previa = s.revisar(id);
            jdbc.update("""
                INSERT INTO activo_fijo_movimiento(tenant_id,empresa_id,activo_id,sucursal_id,tipo,fecha,descripcion,importe)
                VALUES (?,?,?,?,'DEPRECIACION',?,'Depreciación registrada',25000)
                """, tenant, empresa, activo, sucursal, hoy);
            assertThatThrownBy(() -> s.confirmar(previa, UUID.randomUUID())).hasMessageContaining("activo cambió");
            var revision = s.revisar(id);
            assertThat(revision.activo().libros()).isEqualByComparingTo("125000");
            assertThat(revision.resultado().ganancia()).isEqualByComparingTo("15000");
            var solicitud = UUID.randomUUID();
            var auditoriaFallida = mock(AuditoriaService.class);
            doThrow(new IllegalStateException("Auditoría no disponible")).when(auditoriaFallida)
                .registrar(eq("CONFIRMAR"), eq("BajaActivo"), eq(id), anyString());
            var transaccionAnidada = new TransactionTemplate(new DataSourceTransactionManager(ds));
            transaccionAnidada.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
            assertThatThrownBy(() -> transaccionAnidada.executeWithoutResult(t ->
                servicio(jdbc, auditoriaFallida).confirmar(revision, solicitud))).hasMessage("Auditoría no disponible");
            assertThat(s.detalle(id).estado()).isEqualTo(Estado.BORRADOR);
            assertThat(jdbc.queryForObject("SELECT estado FROM activo_fijo WHERE id=?", String.class, activo)).isEqualTo("ACTIVO");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM activo_fijo_movimiento WHERE baja_id=?", Long.class, id)).isZero();
            s.confirmar(revision, solicitud);
            s.confirmar(revision, solicitud);
            assertThatThrownBy(() -> s.revisar(otro)).hasMessageContaining("ya está dado de baja");
            assertThat(s.buscarActivos("SER-123", 0, 10)).isEmpty();
            assertThat(s.totales()).singleElement().satisfies(t -> {
                assertThat(t.cantidad()).isEqualTo(1);
                assertThat(t.libros()).isEqualByComparingTo("125000");
            });
            assertThat(jdbc.queryForObject("SELECT estado FROM activo_fijo WHERE id=?", String.class, activo)).isEqualTo("BAJA");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM activo_fijo_movimiento WHERE baja_id=?", Long.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE registro_id=?", Long.class, id)).isEqualTo(3);
            jdbc.update("UPDATE activo_fijo SET nombre='Nombre nuevo' WHERE id=?", activo);
            assertThat(s.confirmadaDeActivo(activo)).get().satisfies(d -> assertThat(d.activo().nombre()).isEqualTo("Activo histórico"));
            var archivo = new BajaActivoPdfService(s, mock(LocalFileStorageService.class)).generar(id);
            try (var doc = Loader.loadPDF(archivo.contenido())) {
                assertThat(new PDFTextStripper().getText(doc)).contains("BAJA DE ACTIVO", "Activo histórico", "125,000.00", "ACTA-01");
            } catch (java.io.IOException ex) {
                throw new AssertionError(ex);
            }
            autenticar(tenant, empresa, false);
            assertThatThrownBy(() -> s.detalle(id)).isInstanceOf(RecursoNoEncontradoException.class);
            assertThat(s.listar(new Filtro(null, null, null, null, ""), 0, 10)).isEmpty();
            autenticar(UUID.randomUUID(), UUID.randomUUID(), true);
            assertThatThrownBy(() -> s.documento(id)).isInstanceOf(RecursoNoEncontradoException.class);
        });
    }

    private BajaActivoService servicio(JdbcTemplate jdbc,AuditoriaService auditoria) {
        var named=new NamedParameterJdbcTemplate(jdbc);
        var empresas=mock(EmpresaRepository.class);
        var e=mock(Empresa.class);
        when(e.getZonaHoraria()).thenReturn("America/Santo_Domingo");
        when(e.getNombre()).thenReturn("Empresa de prueba");
        when(empresas.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(e));
        return new BajaActivoService(new BajaActivoRepository(named),empresas,
            mock(DatosEmpresaRepository.class),auditoria);
    }

    private void preparar(JdbcTemplate j) {
        j.update("INSERT INTO empresas(id,tenant_id,codigo,nombre) VALUES (?,?,?,'Prueba baja')",empresa,tenant,
            "TEST-"+empresa.toString().substring(0,8));
        j.update("INSERT INTO sucursales(id,tenant_id,empresa_id,codigo,nombre) VALUES (?,?,?,'01','Principal')",sucursal,tenant,empresa);
        j.update("INSERT INTO monedas(id,tenant_id,empresa_id,codigo_iso,nombre,simbolo) VALUES (?,?,?,'DOP','Peso','RD$')",moneda,tenant,empresa);
        j.update("INSERT INTO usuarios(id,tenant_id,empresa_id,usuario,nombre,password_hash) VALUES (?,?,?,'prueba','Prueba','inutilizado')",
            usuario,tenant,empresa);
        j.update("""
            INSERT INTO activo_fijo(id,tenant_id,empresa_id,codigo,nombre,categoria,fecha_adquisicion,costo,
              moneda_id,sucursal_id,depreciable,condicion,serie)
            VALUES (?,?,?,'AF-000001','Activo histórico','TECNOLOGIA',?,150000,?,?,false,'FUERA_SERVICIO','SER-123')
            """,activo,tenant,empresa,hoy.minusMonths(1),moneda,sucursal);
    }

    private void autenticar(UUID t,UUID e,boolean todas) {
        var p=new TenantPrincipal(usuario,t,e,null,"TEST","Prueba","test","",true,todas,Set.of(UUID.randomUUID()),Set.of());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
}
