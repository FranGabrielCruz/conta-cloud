package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.*;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.CuentaContableRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

@EnabledIfEnvironmentVariable(named = "CONTACLOUD_POSTGRES_TESTS", matches = "true")
class CatalogoCuentasPostgresTest {
    private static PGSimpleDataSource ds;
    private final UUID tenant = UUID.randomUUID();
    private final UUID empresa = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();

    @BeforeAll
    static void conectar() {
        ds = new PGSimpleDataSource();
        ds.setURL(System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/conta_cloud"));
        ds.setUser(System.getenv().getOrDefault("DB_USERNAME", "postgres"));
        ds.setPassword(System.getenv("DB_PASSWORD"));
        assertThat(new JdbcTemplate(ds).queryForObject("select current_database()", String.class)).isEqualTo("conta_cloud");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @AfterEach
    void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void cadaEmpresaTieneCuentasClavesYSecuenciasIndependientes() {
        var jdbc = new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            var otraEmpresa = UUID.randomUUID();
            var otroUsuario = UUID.randomUUID();
            preparar(jdbc, empresa, usuario);
            preparar(jdbc, otraEmpresa, otroUsuario);
            var s = servicio(jdbc);
            var cajaA = cuentaDeCaja(jdbc, empresa);
            var cajaB = cuentaDeCaja(jdbc, otraEmpresa);
            assertThat(cajaA).isNotEqualTo(cajaB);
            autenticar(empresa, usuario);
            var a = s.detalle(cajaA).cuenta();
            assertThatThrownBy(() -> s.detalle(cajaB)).isInstanceOf(RecursoNoEncontradoException.class);
            var nuevaA = s.crear(entrada(a.padre(), "Banco A", Tipo.MOVIMIENTO), UUID.randomUUID());
            s.editar(a.id(), a.version(), entrada(a.padre(), "Caja Empresa A", Tipo.MOVIMIENTO));
            assertThat(s.detalle(nuevaA).cuenta().codigo()).isEqualTo("1.1.01.03");
            autenticar(otraEmpresa, otroUsuario);
            var b = s.detalle(cajaB).cuenta();
            assertThat(b.nombre()).isEqualTo("Caja General");
            assertThat(b.codigo()).isEqualTo(a.codigo());
            assertThat(b.padre()).isNotEqualTo(a.padre());
            var nuevaB = s.crear(entrada(b.padre(), "Banco B", Tipo.MOVIMIENTO), UUID.randomUUID());
            assertThat(s.detalle(nuevaB).cuenta().codigo()).isEqualTo("1.1.01.03");
            assertThatThrownBy(() -> s.crear(entrada(a.padre(), "Cruce", Tipo.MOVIMIENTO), UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        });
    }

    private UUID cuentaDeCaja(JdbcTemplate jdbc, UUID empresaId) {
        return jdbc.queryForObject("""
            SELECT cuenta_id FROM cuenta_contable_clave_empresa
            WHERE tenant_id=? AND empresa_id=? AND clave='CASH_DEFAULT'
            """, UUID.class, tenant, empresaId);
    }

    @Test
    void recuperaEmpresaVaciaYConservaCatalogoExistente() {
        var jdbc = new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            preparar(jdbc, empresa, usuario);
            jdbc.update("DELETE FROM cuenta_contable WHERE empresa_id=?", empresa);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cuenta_contable_instalacion WHERE empresa_id=?",
                Long.class, empresa)).isEqualTo(1);
            jdbc.query("SELECT instalar_catalogo_contable(?,?)", rs -> {}, tenant, empresa);
            autenticar(empresa, usuario);
            var s = servicio(jdbc);
            assertThat(s.reporte(null).cuentas()).hasSize(53);
            var raiz = s.raices(null, 0, 10).getContent().getFirst();
            jdbc.update("UPDATE cuenta_contable SET nombre='Nombre personalizado' WHERE id=?", raiz.id());
            jdbc.query("SELECT instalar_catalogo_contable(?,?)", rs -> {}, tenant, empresa);
            assertThat(s.detalle(raiz.id()).cuenta().nombre()).isEqualTo("Nombre personalizado");
            assertThat(s.reporte(null).cuentas()).hasSize(53);
            assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auditoria WHERE empresa_id=? AND accion='INSTALAR_CATALOGO_BASE'
                """, Long.class, empresa)).isEqualTo(2);
        });
    }

    @Test
    void plantillaIncompletaRevierteLaEmpresaYPermiteReintentar() {
        var jdbc = new JdbcTemplate(ds);
        var gestor = new DataSourceTransactionManager(ds);
        new TransactionTemplate(gestor).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            var intento = new TransactionTemplate(gestor);
            intento.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
            assertThatThrownBy(() -> intento.executeWithoutResult(t -> {
                jdbc.update("DELETE FROM cuenta_contable_plantilla_item WHERE version=1 AND codigo='1.2.01.01'");
                preparar(jdbc, empresa, usuario);
            })).hasMessageContaining("plantilla contable");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM empresas WHERE id=?", Long.class, empresa)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cuenta_contable_instalacion WHERE empresa_id=?",
                Long.class, empresa)).isZero();
            preparar(jdbc, empresa, usuario);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cuenta_contable WHERE empresa_id=?",
                Long.class, empresa)).isEqualTo(53);
        });
    }

    @Test
    void protegeJerarquiaEstadoDependenciasTecnicasYReporte() {
        var jdbc = new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            preparar(jdbc, empresa, usuario);
            autenticar(empresa, usuario);
            var servicio = servicio(jdbc);
            var raices = servicio.raices(null, 0, 10).getContent();
            assertThat(raices).extracting(Cuenta::codigo).containsExactly("1", "2", "3", "4", "5", "6");
            assertThat(raices).allMatch(Cuenta::estructural);
            assertThat(servicio.reporte(null).cuentas()).hasSize(53);
            jdbc.query("SELECT instalar_catalogo_contable(?,?)", rs -> {}, tenant, empresa);
            assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auditoria WHERE empresa_id=? AND accion='INSTALAR_CATALOGO_BASE'
                """, Long.class, empresa)).isEqualTo(1);
            var raiz = raices.getFirst().id();
            var corriente = servicio.hijos(raiz, null).getFirst().id();
            var efectivo = servicio.hijos(corriente, null).getFirst().id();
            var inventario = servicio.hijos(corriente, null).getLast().id();
            var pruebaRollback = new TransactionTemplate(new DataSourceTransactionManager(ds));
            pruebaRollback.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
            pruebaRollback.executeWithoutResult(t -> {
                servicio.crear(entrada(efectivo, "Revertida", Tipo.MOVIMIENTO), UUID.randomUUID());
                t.setRollbackOnly();
            });
            assertThat(servicio.hijos(efectivo, null)).extracting(Cuenta::codigo).containsExactly("1.1.01.01", "1.1.01.02");
            var grupo = servicio.crear(entrada(efectivo, "Grupo de prueba", Tipo.AGRUPADORA), UUID.randomUUID());
            var token = UUID.randomUUID();
            var entrada = new Entrada(grupo, "Caja de prueba", Tipo.MOVIMIENTO, Naturaleza.ACREEDORA, null);
            var cuenta = servicio.crear(entrada, token);
            assertThat(servicio.crear(entrada, token)).isEqualTo(cuenta);
            assertThat(servicio.detalle(grupo).cuenta().codigo()).isEqualTo("1.1.01.03");
            assertThat(servicio.detalle(cuenta).nivel()).isEqualTo(5);
            assertThat(servicio.detalle(cuenta).cuenta().codigo()).isEqualTo("1.1.01.03.01");
            assertThat(servicio.detalle(cuenta).cuenta().clasificacion()).isEqualTo(Clasificacion.ACTIVO);
            assertThat(servicio.detalle(cuenta).cuenta().naturaleza()).isEqualTo(Naturaleza.ACREEDORA);
            assertThat(java.util.Arrays.stream(Entrada.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("codigo", "clasificacion");
            assertThatThrownBy(() -> servicio.crear(entrada(cuenta, "Hija", Tipo.MOVIMIENTO), UUID.randomUUID()))
                .hasMessageContaining("agrupadora activa");
            assertThatThrownBy(() -> servicio.crear(entrada(null, "Raíz adicional", Tipo.MOVIMIENTO), UUID.randomUUID()))
                .hasMessageContaining("cuenta padre");
            assertThatThrownBy(() -> servicio.editar(raiz, 0, entrada(grupo, "Activos", Tipo.AGRUPADORA)))
                .hasMessageContaining("protegida");
            assertThatThrownBy(() -> servicio.desactivar(raiz, 0)).hasMessageContaining("estructurales");
            assertThat(servicio.padres(raiz, "efectivo", 0, 10)).isEmpty();
            assertThatThrownBy(() -> servicio.desactivar(grupo, 0)).hasMessageContaining("hijas activas");
            var movible = servicio.crear(entrada(efectivo, "Movible", Tipo.MOVIMIENTO), UUID.randomUUID());
            assertThat(servicio.detalle(movible).cuenta().codigo()).isEqualTo("1.1.01.04");
            servicio.editar(movible, 0, entrada(inventario, "Movida", Tipo.MOVIMIENTO));
            assertThat(servicio.detalle(movible).cuenta().codigo()).isEqualTo("1.1.03.02");
            var siguiente = servicio.crear(entrada(efectivo, "Siguiente", Tipo.MOVIMIENTO), UUID.randomUUID());
            assertThat(servicio.detalle(siguiente).cuenta().codigo()).isEqualTo("1.1.01.05");
            var referencia = UUID.randomUUID();
            servicio.registrarUso(cuenta, "Caja", referencia, "Configuración de caja", false, Clasificacion.ACTIVO, null);
            assertThatThrownBy(() -> servicio.desactivar(cuenta, 0)).hasMessageContaining("configuración activa");
            servicio.desvincular(cuenta, "Caja", referencia);
            servicio.registrarUso(cuenta, "Asientos", UUID.randomUUID(), "Asiento confirmado", true, null, null);
            assertThatThrownBy(() -> servicio.editar(cuenta, 0, entrada(inventario, "Caja", Tipo.MOVIMIENTO)))
                .hasMessageContaining("protegen su estructura");
            servicio.desactivar(cuenta, 0);
            servicio.desactivar(cuenta, 0);
            servicio.desactivar(grupo, 0);
            assertThatThrownBy(() -> servicio.activar(cuenta, 1)).hasMessageContaining("Activa primero");
            servicio.activar(grupo, 1);
            servicio.activar(cuenta, 1);
            assertThat(servicio.detalle(cuenta).movimientos()).isTrue();

            var filtro = new Filtro(Tipo.MOVIMIENTO, Naturaleza.ACREEDORA, true, "Caja de prueba");
            assertThat(servicio.raices(filtro, 0, 10)).extracting(Cuenta::id).containsExactly(raiz);
            assertThat(servicio.hijos(raiz, filtro)).extracting(Cuenta::id).containsExactly(corriente);
            assertThat(servicio.reporte(filtro).cuentas()).extracting(l -> l.cuenta().id()).containsExactly(raiz, corriente, efectivo, grupo, cuenta);
            var pdf = new CatalogoCuentasPdfService(servicio, mock(LocalFileStorageService.class)).generar(filtro);
            try (var documento = Loader.loadPDF(pdf.contenido())) {
                assertThat(new PDFTextStripper().getText(documento)).contains("CATÁLOGO DE CUENTAS", "Caja de prueba", "Naturaleza: Acreedora");
            } catch (java.io.IOException ex) {
                throw new AssertionError(ex);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE registro_id=?", Long.class, cuenta)).isEqualTo(3);

            var otraEmpresa = UUID.randomUUID();
            var otroUsuario = UUID.randomUUID();
            preparar(jdbc, otraEmpresa, otroUsuario);
            autenticar(otraEmpresa, otroUsuario);
            assertThat(servicio.raices(null, 0, 10)).hasSize(6);
            assertThatThrownBy(() -> servicio.detalle(cuenta)).isInstanceOf(RecursoNoEncontradoException.class);
            assertThatThrownBy(() -> servicio.crear(entrada(grupo, "Invisible", Tipo.MOVIMIENTO), UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class);
            assertThat(servicio.raices(null, 0, 10).getContent().getFirst().id()).isNotEqualTo(raiz);
        });
    }

    @Test
    void serializaAprovisionamientoYSecuenciaYDetectaVersionObsoleta() throws Exception {
        var jdbc = new JdbcTemplate(ds);
        var transaccion = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var servicio = servicio(jdbc);
        transaccion.executeWithoutResult(tx -> {
            preparar(jdbc, empresa, usuario);
            jdbc.update("DELETE FROM cuenta_contable WHERE empresa_id=?", empresa);
        });
        autenticar(empresa, usuario);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var aprovisionar = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Void> instalar = () -> {
                if (!aprovisionar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Espera excedida.");
                transaccion.executeWithoutResult(tx ->
                    jdbc.query("SELECT instalar_catalogo_contable(?,?)", rs -> {}, tenant, empresa));
                return null;
            };
            var intentoA = executor.submit(instalar);
            var intentoB = executor.submit(instalar);
            aprovisionar.countDown();
            intentoA.get(15, TimeUnit.SECONDS);
            intentoB.get(15, TimeUnit.SECONDS);
            assertThat(servicio.reporte(null).cuentas()).hasSize(53);
            assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auditoria WHERE empresa_id=? AND accion='INSTALAR_CATALOGO_BASE'
                """, Long.class, empresa)).isEqualTo(2);
            var padre = servicio.raices(null, 0, 10).getContent().getLast().id();
            var inicio = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<UUID> crear = () -> {
                autenticar(empresa, usuario);
                try {
                    if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Espera excedida.");
                    return transaccion.execute(tx -> servicio.crear(entrada(padre, "Cuenta", Tipo.MOVIMIENTO), UUID.randomUUID()));
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            var primero = executor.submit(crear);
            var segundo = executor.submit(crear);
            inicio.countDown();
            var primera = servicio.detalle(primero.get(15, TimeUnit.SECONDS)).cuenta();
            var segunda = servicio.detalle(segundo.get(15, TimeUnit.SECONDS)).cuenta();
            assertThat(java.util.List.of(primera.codigo(), segunda.codigo())).containsExactlyInAnyOrder("6.3", "6.4");
            transaccion.executeWithoutResult(tx -> servicio.editar(primera.id(), 0, entrada(padre, "Editada", Tipo.MOVIMIENTO)));
            assertThatThrownBy(() -> transaccion.executeWithoutResult(tx ->
                servicio.editar(primera.id(), 0, entrada(padre, "Obsoleta", Tipo.MOVIMIENTO))))
                .hasMessageContaining("cambió");
        } finally {
            transaccion.executeWithoutResult(tx -> {
                for (var tabla : java.util.List.of("auditoria", "cuenta_contable", "usuarios", "condiciones_pago", "unit_of_measure",
                        "inventory_adjustment_reason", "sales_payment_method", "sales_return_reason"))
                    jdbc.update("DELETE FROM " + tabla + " WHERE empresa_id=?", empresa);
                jdbc.update("DELETE FROM empresas WHERE id=? AND tenant_id=?", empresa, tenant);
            });
        }
    }

    private CatalogoCuentasService servicio(JdbcTemplate jdbc) {
        var empresas = mock(EmpresaRepository.class);
        var e = mock(Empresa.class);
        when(e.getNombre()).thenReturn("Empresa de prueba");
        when(e.getZonaHoraria()).thenReturn("America/Caracas");
        when(empresas.findByIdAndTenantId(empresa, tenant)).thenReturn(Optional.of(e));
        return new CatalogoCuentasService(new CuentaContableRepository(new NamedParameterJdbcTemplate(jdbc)),
            empresas, mock(DatosEmpresaRepository.class), new AuditoriaService(jdbc));
    }

    private void preparar(JdbcTemplate jdbc, UUID empresa, UUID usuario) {
        jdbc.update("INSERT INTO empresas(id,tenant_id,codigo,nombre) VALUES (?,?,?,'Prueba catálogo')",
            empresa, tenant, "TEST-" + empresa.toString().substring(0, 8));
        jdbc.update("INSERT INTO usuarios(id,tenant_id,empresa_id,usuario,nombre,password_hash) VALUES (?,?,?,?,'Prueba','inutilizado')",
            usuario, tenant, empresa, "prueba-" + usuario.toString().substring(0, 8));
    }

    private void autenticar(UUID empresa, UUID usuario) {
        var p = new TenantPrincipal(usuario, tenant, empresa, null, "TEST", "Prueba", "test", "", true, true, Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
    }

    private static Entrada entrada(UUID padre, String nombre, Tipo tipo) {
        return new Entrada(padre, nombre, tipo, Naturaleza.DEUDORA, null);
    }
}
