package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.MantenimientoDto.*;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
class MantenimientoPostgresTest {
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

    @Test void cicloCompletoConSnapshotYReintentosSinDuplicar() {
        var jdbc=new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            autenticar(tenant,empresa,true);
            var s=servicio(jdbc,new AuditoriaService(jdbc));
            var original=entrada();
            var id=s.crear(new Entrada(original.activo(),original.tipo(),original.prioridad(),hoy.plusDays(2),original.proxima(),
                original.responsable(),original.proveedor(),original.moneda(),original.estimado(),original.referencia(),
                original.descripcion(),original.observacion()));
            var d=s.detalle(id);
            assertThat(d.codigo()).isEqualTo("MAN-000001");
            assertThat(d.estado()).isEqualTo(Estado.PROGRAMADO);
            assertThat(s.buscarActivos("SER-123",0,10)).hasSize(1);
            assertThat(s.listar(new Filtro(null,null,null,null,false,"MAN-000001"),0,10)).hasSize(1);
            assertThat(s.listar(new Filtro(hoy,hoy.plusDays(3),"PREVENTIVO",Estado.PROGRAMADO,false,"histórico"),0,10)).hasSize(1);
            assertThat(s.listar(new Filtro(hoy,hoy.plusDays(3),"CORRECTIVO",Estado.PROGRAMADO,false,"histórico"),0,10)).isEmpty();
            assertThatThrownBy(()->s.listar(new Filtro(hoy.plusDays(1),hoy,null,null,false,""),0,10))
                .isInstanceOf(ReglaNegocioException.class);
            assertThat(s.indicadores().programados()).isEqualTo(1);
            var inicio=UUID.randomUUID();
            s.iniciar(id,d.version(),true,inicio);
            s.iniciar(id,d.version(),true,inicio);
            assertThat(condicion(jdbc)).isEqualTo("MANTENIMIENTO");
            var enProceso=s.detalle(id);
            var completar=UUID.randomUUID();
            var cierre=new Completar(hoy,new BigDecimal("13250"),null,"Cambio de filtros",hoy.plusDays(90),null);
            s.completar(id,enProceso.version(),cierre,completar);
            s.completar(id,enProceso.version(),cierre,completar);
            assertThat(condicion(jdbc)).isEqualTo("FUERA_SERVICIO");
            assertThat(s.detalle(id).variacion()).isEqualByComparingTo("750");
            assertThat(s.indicadores().costos()).singleElement().satisfies(c->assertThat(c.costo()).isEqualByComparingTo("13250"));
            assertThat(s.historialActivo(activo)).singleElement().satisfies(f->assertThat(f.estado()).isEqualTo(Estado.COMPLETADO));
            assertThatThrownBy(()->s.cancelar(id,enProceso.version(),"Otro",UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE registro_id=?",Long.class,id)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT costo FROM activo_fijo WHERE id=?",BigDecimal.class,activo)).isEqualByComparingTo("150000");
            jdbc.update("UPDATE activo_fijo SET nombre='Nombre posterior',estado='INACTIVO' WHERE id=?",activo);
            assertThat(s.documento(id).snapshot().activoNombre()).isEqualTo("Activo histórico");
            assertThat(s.documento(id).snapshot().estadoPatrimonial()).isEqualTo("ACTIVO");
            var archivo=new MantenimientoPdfService(s,null).generar(id);
            try(var pdf=Loader.loadPDF(archivo.contenido())) {
                if(Boolean.getBoolean("mantenimiento.pdf.preview"))
                    java.nio.file.Files.write(java.nio.file.Path.of("target","mantenimiento-prueba.pdf"),archivo.contenido());
                var texto=new PDFTextStripper().getText(pdf);
                assertThat(texto).contains("MAN-000001","Activo histórico","SER-123","Cambio de filtros",
                    "DOP 12,500.00","DOP 13,250.00","DOP 750.00","MANTENIMIENTO DE ACTIVO",
                    "Estado del activo","Próximo mantenimiento","Fecha programada","Fecha realizada","Generado:")
                    .doesNotContain("Nombre posterior","Inactivo","ContaCloud","OBSERVACIÓN","null","undefined");
                assertThat(pdf.getNumberOfPages()).isOne();
            } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            jdbc.update("UPDATE activo_mantenimiento SET snapshot=snapshot-'estadoPatrimonial' WHERE id=?",id);
            assertThat(s.documento(id).snapshot().estadoPatrimonial()).isNull();
            jdbc.update("UPDATE activo_mantenimiento SET trabajo=?,observacion=? WHERE id=?",
                "Línea de trabajo\n".repeat(90)+"Fin del trabajo",
                "Línea de observación\n".repeat(78)+"Fin de la observación",id);
            try(var pdf=Loader.loadPDF(new MantenimientoPdfService(s,null).generar(id).contenido())) {
                assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
                assertThat(new PDFTextStripper().getText(pdf)).contains("Fin del trabajo","Fin de la observación");
            } catch(java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            autenticar(UUID.randomUUID(),empresa,true);
            assertThatThrownBy(()->s.detalle(id)).isInstanceOf(RecursoNoEncontradoException.class);
            autenticar(tenant,UUID.randomUUID(),true);
            assertThatThrownBy(()->s.documento(id)).isInstanceOf(RecursoNoEncontradoException.class);
            autenticar(tenant,empresa,false);
            assertThatThrownBy(()->s.detalle(id)).isInstanceOf(RecursoNoEncontradoException.class);
            assertThatThrownBy(()->s.crear(entrada())).isInstanceOf(ReglaNegocioException.class);
        });
    }

    @Test void cancelaSoloConMotivoYRevierteSiLaAuditoriaFalla() {
        var jdbc=new JdbcTemplate(ds);
        var gerente=new DataSourceTransactionManager(ds);
        new TransactionTemplate(gerente).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            autenticar(tenant,empresa,true);
            var correcta=servicio(jdbc,new AuditoriaService(jdbc));
            var id=correcta.crear(entrada());
            var d=correcta.detalle(id);
            var auditoria=mock(AuditoriaService.class);
            doThrow(new IllegalStateException("Auditoría de prueba")).when(auditoria)
                .registrar(anyString(),anyString(),any(),anyString());
            var anidada=new TransactionTemplate(gerente);
            anidada.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
            assertThatThrownBy(()->anidada.executeWithoutResult(t->servicio(jdbc,auditoria).iniciar(id,d.version(),true,UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
            assertThat(correcta.detalle(id).estado()).isEqualTo(Estado.PROGRAMADO);
            assertThat(condicion(jdbc)).isEqualTo("FUERA_SERVICIO");
            correcta.iniciar(id,d.version(),true,UUID.randomUUID());
            var actual=correcta.detalle(id);
            assertThatThrownBy(()->correcta.cancelar(id,actual.version()," ",UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);
            var token=UUID.randomUUID();
            correcta.cancelar(id,actual.version(),"Cambio de plan",token);
            correcta.cancelar(id,actual.version(),"Cambio de plan",token);
            assertThat(correcta.detalle(id).motivo()).isEqualTo("Cambio de plan");
            assertThat(condicion(jdbc)).isEqualTo("FUERA_SERVICIO");
            assertThatThrownBy(()->correcta.documento(id)).isInstanceOf(ReglaNegocioException.class);
        });
    }

    private Entrada entrada(){
        return new Entrada(activo,"PREVENTIVO",Prioridad.MEDIA,hoy,hoy.plusDays(90),null,null,moneda,
            new BigDecimal("12500"),null,"Revisión programada",null);
    }

    @Test void completarYCancelarEnParaleloSoloConsolidanUnaTransicion() throws Exception {
        var jdbc=new JdbcTemplate(ds);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var s=servicio(jdbc,new AuditoriaService(jdbc));
        autenticar(tenant,empresa,true);
        UUID id=tx.execute(t->{
            preparar(jdbc);
            var orden=s.crear(entrada());
            s.iniciar(orden,s.detalle(orden).version(),true,UUID.randomUUID());
            return orden;
        });
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            long version=s.detalle(id).version();
            var salida=new java.util.concurrent.CountDownLatch(1);
            var completar=executor.submit(()->competir(salida,tx,()->s.completar(id,version,
                new Completar(hoy,BigDecimal.TEN,null,"Trabajo",null,null),UUID.randomUUID())));
            var cancelar=executor.submit(()->competir(salida,tx,()->s.cancelar(id,version,"Motivo",UUID.randomUUID())));
            salida.countDown();
            assertThat(java.util.List.of(completar.get(15,java.util.concurrent.TimeUnit.SECONDS),
                cancelar.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(s.detalle(id).estado()).isIn(Estado.COMPLETADO,Estado.CANCELADO);
            assertThat(condicion(jdbc)).isEqualTo("FUERA_SERVICIO");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE registro_id=?",Long.class,id)).isEqualTo(3);
        } finally {
            tx.executeWithoutResult(t->{
                for(var tabla:java.util.List.of("auditoria","activo_mantenimiento","activo_mantenimiento_secuencia",
                    "activo_fijo","usuarios","monedas","sucursales","condiciones_pago","unit_of_measure",
                    "inventory_adjustment_reason","sales_payment_method","sales_return_reason"))
                    jdbc.update("DELETE FROM "+tabla+" WHERE empresa_id=?",empresa);
                jdbc.update("DELETE FROM cuenta_contable WHERE empresa_id=?",empresa);
                jdbc.update("DELETE FROM empresas WHERE id=? AND tenant_id=?",empresa,tenant);
            });
        }
    }

    private boolean competir(java.util.concurrent.CountDownLatch salida,TransactionTemplate tx,Runnable operacion)
            throws InterruptedException {
        autenticar(tenant,empresa,true);
        try {
            if(!salida.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Espera excedida.");
            tx.executeWithoutResult(t->operacion.run());
            return true;
        } catch(ReglaNegocioException ex) {
            assertThat(ex).hasMessageContaining("cambió");
            return false;
        } finally { SecurityContextHolder.clearContext(); }
    }

    private String condicion(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT condicion FROM activo_fijo WHERE id=?",String.class,activo);
    }

    private MantenimientoService servicio(JdbcTemplate jdbc,AuditoriaService auditoria) {
        var named=new NamedParameterJdbcTemplate(jdbc);
        var empresas=mock(EmpresaRepository.class);
        var e=mock(Empresa.class);
        when(e.getZonaHoraria()).thenReturn("America/Santo_Domingo");
        when(e.getNombre()).thenReturn("Empresa de prueba");
        when(empresas.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(e));
        return new MantenimientoService(new MantenimientoRepository(named),new ActivoFijoRepository(named),empresas,
            mock(DatosEmpresaRepository.class),auditoria,30);
    }

    private void preparar(JdbcTemplate j) {
        j.update("INSERT INTO empresas(id,tenant_id,codigo,nombre) VALUES (?,?,?,'Prueba mantenimiento')",empresa,tenant,
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
