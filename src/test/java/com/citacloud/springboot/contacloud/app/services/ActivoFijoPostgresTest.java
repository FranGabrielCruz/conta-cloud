package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.*;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.ActivoFijoRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class ActivoFijoPostgresTest {
    private static final LocalDate FECHA=LocalDate.of(2026,11,9);
    private static PGSimpleDataSource ds;

    @BeforeAll
    static void conectar() {
        ds=new PGSimpleDataSource();
        ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));
        ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));
        ds.setPassword(System.getenv("DB_PASSWORD"));
        assertThat(new JdbcTemplate(ds).queryForObject("select current_database()",String.class)).isEqualTo("conta_cloud");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @AfterEach
    void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void registraDepreciablesYTerrenosConHistorialYProtegeLaEdicion() {
        probar((jdbc,svc,p,sucursal,moneda,marca)->{
            var a=svc.crear(datos(marca,sucursal,moneda,true,"Administración"));
            assertThat(a.resumen().codigo()).startsWith("AF-");
            assertThat(svc.detalle(a.resumen().id()).activo().datos().serie()).isEqualTo("SN-DL-2026-00125");
            assertThat(a.datos().depreciacion().inicio()).isEqualTo(FECHA.plusMonths(1));
            assertThat(svc.detalle(a.resumen().id()).asignaciones()).singleElement()
                .satisfies(h->assertThat(h.nueva().ubicacion()).isEqualTo("Administración"));
            var b=svc.crear(datos(marca,sucursal,moneda,false,"Recepción"));
            assertThat(numero(b)).isEqualTo(numero(a)+1);
            assertThat(b.datos().depreciacion().metodo()).isNull();
            var actualizado=svc.editar(a.resumen().id(),a.version(),datos(marca,sucursal,moneda,true,"Contabilidad"));
            assertThat(svc.detalle(a.resumen().id()).asignaciones()).hasSize(2).first().satisfies(h->{
                assertThat(h.anterior().ubicacion()).isEqualTo("Administración");
                assertThat(h.nueva().ubicacion()).isEqualTo("Contabilidad");
            });
            assertThat(jdbc.queryForObject("""
                SELECT detalle->'nuevo'->'asignacion'->>'ubicacion' FROM auditoria
                WHERE empresa_id=? AND registro_id=? AND accion='EDITAR_ASIGNACION' AND detalle->>'traceId' IS NOT NULL
                """,String.class,p.empresaId(),a.resumen().id()))
                .isEqualTo("Contabilidad");
            assertThatThrownBy(()->svc.editar(a.resumen().id(),a.version(),a.datos()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambió");

            jdbc.update("""
                INSERT INTO activo_fijo_movimiento(tenant_id,empresa_id,activo_id,sucursal_id,tipo,fecha,descripcion,importe)
                VALUES (?,?,?,?,'DEPRECIACION',?,'Cierre de prueba',14000)
                """,p.tenantId(),p.empresaId(),a.resumen().id(),sucursal,FECHA.plusMonths(1));
            var vigente=svc.detalle(a.resumen().id()).activo();
            assertThat(vigente.version()).isGreaterThan(actualizado.version());
            assertThat(vigente.resumen().valorLibros()).isEqualByComparingTo("61000.00");
            var d=vigente.datos();
            var cambio=new Datos(d.nombre(),d.categoria(),d.marca(),d.modelo(),d.serie(),d.adquisicion(),d.asignacion(),
                new Depreciacion(true,Metodo.LINEA_RECTA,36,new BigDecimal("5000"),FECHA.plusMonths(1)),null);
            assertThatThrownBy(()->svc.editar(a.resumen().id(),vigente.version(),cambio))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("movimientos financieros");
            jdbc.update("""
                INSERT INTO activo_fijo_movimiento(tenant_id,empresa_id,activo_id,sucursal_id,tipo,fecha,descripcion,importe)
                VALUES (?,?,?,?,'BAJA',?,'Retiro de prueba',61000)
                """,p.tenantId(),p.empresaId(),a.resumen().id(),sucursal,FECHA.plusMonths(2));
            var baja=svc.detalle(a.resumen().id()).activo();
            assertThat(baja.resumen().estado()).isEqualTo(Estado.BAJA);
            assertThat(baja.resumen().valorLibros()).isZero();
            assertThatThrownBy(()->svc.editar(a.resumen().id(),baja.version(),baja.datos()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("dados de baja");
        });
    }

    @Test
    void informeYTotalesAbarcanLosFiltrosYAislanEmpresaTenantYSucursal() {
        probar((jdbc,svc,p,sucursal,moneda,marca)->{
            UUID monedaDos=jdbc.query("SELECT id FROM monedas WHERE empresa_id=? AND activo AND id<>? LIMIT 1",
                (r,n)->r.getObject(1,UUID.class),p.empresaId(),moneda).stream().findFirst().orElse(null);
            if (monedaDos==null) {
                monedaDos=UUID.randomUUID();
                jdbc.update("INSERT INTO monedas(id,tenant_id,empresa_id,codigo_iso,nombre,simbolo) VALUES (?,?,?,'ZZZ','Moneda prueba','Z')",
                    monedaDos,p.tenantId(),p.empresaId());
            }
            Registro referencia=null;
            for (int i=0;i<40;i++) {
                var actual=svc.crear(datos(marca,sucursal,i==36?monedaDos:moneda,false,i<37?"Área filtrada":"Otra área"));
                if (i==0) referencia=actual;
            }
            var filtro=new Filtro("TERRENOS",sucursal,"Área filtrada",Estado.ACTIVO,marca);
            var lista=svc.consultar(filtro,0,10);
            assertThat(lista.activos()).hasSize(10);
            assertThat(lista.activos().getTotalElements()).isEqualTo(37);
            assertThat(lista.totales()).hasSize(2);
            var anterior=jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE empresa_id=? AND entidad='ActivoFijo'",
                Long.class,p.empresaId());
            var reporte=svc.reporte(filtro);
            assertThat(reporte.activos()).hasSize(37);
            assertThat(reporte.filtros()).containsExactly("Categoría: Terrenos","Sucursal: "+referencia.resumen().sucursal(),
                "Ubicación: Área filtrada","Estado: Activo","Búsqueda: "+marca);
            assertThat(svc.reporte(null).filtros()).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE empresa_id=? AND entidad='ActivoFijo'",
                Long.class,p.empresaId())).isEqualTo(anterior);
            UUID id=referencia.resumen().id();

            autenticar(p.usuarioId(),p.tenantId(),UUID.randomUUID(),true,Set.of());
            assertThatThrownBy(()->svc.detalle(id)).isInstanceOf(RecursoNoEncontradoException.class);
            autenticar(p.usuarioId(),UUID.randomUUID(),p.empresaId(),true,Set.of());
            assertThatThrownBy(()->svc.detalle(id)).isInstanceOf(RecursoNoEncontradoException.class);
            autenticar(p.usuarioId(),p.tenantId(),p.empresaId(),false,Set.of());
            assertThatThrownBy(()->svc.detalle(id)).isInstanceOf(RecursoNoEncontradoException.class);
            assertThatThrownBy(()->svc.crear(datos(marca,sucursal,moneda,false,null)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("sucursal");
        });
    }

    private static long numero(Registro a) { return Long.parseLong(a.resumen().codigo().substring(3)); }

    private static Datos datos(String nombre,UUID sucursal,UUID moneda,boolean depreciable,String ubicacion) {
        return new Datos(nombre,depreciable?"TECNOLOGIA":"TERRENOS","Dell","Latitude","SN-DL-2026-00125",
            new Adquisicion(FECHA,new BigDecimal("75000"),moneda,null,null,null),
            new Asignacion(sucursal,ubicacion,null),
            depreciable?new Depreciacion(true,Metodo.LINEA_RECTA,60,new BigDecimal("5000"),FECHA.plusMonths(1))
                :new Depreciacion(false,null,null,null,null),null);
    }

    private static void probar(Escenario escenario) {
        var jdbc=new JdbcTemplate(ds);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.executeWithoutResult(status->{
            status.setRollbackOnly();
            var registro=jdbc.queryForMap("""
                SELECT e.tenant_id,e.id empresa,s.id sucursal,m.id moneda,u.id usuario
                FROM empresas e JOIN sucursales s ON s.empresa_id=e.id AND s.activo
                JOIN monedas m ON m.empresa_id=e.id AND m.activo AND m.moneda_base
                JOIN usuarios u ON u.tenant_id=e.tenant_id AND u.activo LIMIT 1
                """);
            UUID tenant=(UUID)registro.get("tenant_id"),empresa=(UUID)registro.get("empresa");
            var p=autenticar((UUID)registro.get("usuario"),tenant,empresa,true,Set.of());
            var empresas=mock(EmpresaRepository.class);
            var datos=mock(DatosEmpresaRepository.class);
            var e=mock(Empresa.class);
            when(e.getNombre()).thenReturn("Empresa de prueba");
            when(e.getZonaHoraria()).thenReturn("America/Santo_Domingo");
            when(empresas.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(e));
            var svc=new ActivoFijoService(new ActivoFijoRepository(new NamedParameterJdbcTemplate(jdbc)),
                new AuditoriaService(jdbc),empresas,datos);
            escenario.ejecutar(jdbc,svc,p,(UUID)registro.get("sucursal"),(UUID)registro.get("moneda"),"PRUEBA-"+UUID.randomUUID());
        });
    }

    private static TenantPrincipal autenticar(UUID usuario,UUID tenant,UUID empresa,boolean todas,Set<UUID> sucursales) {
        var p=new TenantPrincipal(usuario,tenant,empresa,null,"TEST","Prueba","test","",true,todas,sucursales,Set.of());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
        return p;
    }

    @FunctionalInterface
    private interface Escenario {
        void ejecutar(JdbcTemplate jdbc,ActivoFijoService svc,TenantPrincipal p,UUID sucursal,UUID moneda,String marca);
    }
}
