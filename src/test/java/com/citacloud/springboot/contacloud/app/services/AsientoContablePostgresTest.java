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

    @Test void diarioPaginaAsientosCompletosYExportaTodoElFiltro() {
        var jdbc=new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            abrirPeriodo(jdbc);
            autenticar(tenant,empresa);
            var asientos=servicio(jdbc);
            var caja=cuenta(jdbc,"CASH_DEFAULT");
            var ventas=cuenta(jdbc,"SALES_REVENUE_DEFAULT");
            var entrada=entrada(caja,ventas,BigDecimal.TEN);
            var borrador=asientos.crear(entrada,UUID.randomUUID());
            for(int i=0;i<10;i++) asientos.guardarYContabilizar(null,0,entrada,UUID.randomUUID());
            var original=asientos.detalle(asientos.listar(new Filtro(null,null,null,Estado.CONTABILIZADO,null,"AS-000002"),0,10)
                .getContent().getFirst().id());
            var reversa=asientos.reversar(original.fila().id(),original.version(),fecha,"Prueba Diario",UUID.randomUUID());
            var empresaRepo=mock(EmpresaRepository.class);
            var emisor=mock(Empresa.class);
            when(emisor.getNombre()).thenReturn("Empresa del Diario");
            when(emisor.getZonaHoraria()).thenReturn("America/Caracas");
            when(empresaRepo.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(emisor));
            var diario=new LibroDiarioService(new LibroDiarioRepository(new NamedParameterJdbcTemplate(jdbc)),
                empresaRepo,mock(DatosEmpresaRepository.class));
            var f=new com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.Filtro(fecha,fecha,null,Origen.MANUAL,"Caja General");
            var primera=diario.consultar(f,0,10);
            var segunda=diario.consultar(f,1,10);
            assertThat(primera.totalAsientos()).isEqualTo(11);
            assertThat(primera.asientos()).hasSize(10).allSatisfy(a->{
                assertThat(a.id()).isNotEqualTo(borrador);
                assertThat(a.movimientos()).hasSize(2);
            });
            assertThat(primera.asientos().getFirst().estado()).isEqualTo(Estado.REVERSADO);
            assertThat(segunda.asientos()).singleElement().satisfies(a->assertThat(a.id()).isEqualTo(reversa));
            assertThat(segunda.totales()).isEqualTo(primera.totales());
            assertThat(primera.totales().getFirst().total().debito()).isEqualByComparingTo("110");
            assertThat(primera.totales().getFirst().total().diferencia()).isZero();
            var soloReversion=new com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.Filtro(fecha,fecha,Tipo.REVERSION,null,"");
            assertThat(diario.consultar(soloReversion,0,10).totalAsientos()).isEqualTo(1);
            var archivo=new LibroDiarioPdfService(diario,mock(LocalFileStorageService.class)).generar(f);
            try(var pdf=Loader.loadPDF(archivo.contenido())) {
                assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
                assertThat(new PDFTextStripper().getText(pdf)).contains("LIBRO DIARIO","Empresa del Diario","AS-000012",
                    "TOTALES DEL PERÍODO","Diferencia: 0.00","Origen: Manual").doesNotContain("AS-000001","ContaCloud");
            } catch(java.io.IOException ex) {throw new AssertionError(ex);}
            autenticar(tenant,UUID.randomUUID());
            assertThat(diario.consultar(f,0,10).asientos()).isEmpty();
        });
    }

    @Test void mayorConservaSaldoRealAlFiltrarPaginarYConsolidar() {
        var jdbc=new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            abrirPeriodo(jdbc);
            autenticar(tenant,empresa);
            var asientos=servicio(jdbc);
            var caja=cuenta(jdbc,"CASH_DEFAULT");
            var bancos=cuenta(jdbc,"BANK_DEFAULT");
            var venta=cuenta(jdbc,"SALES_REVENUE_DEFAULT");
            var activos=cuenta(jdbc,"ASSETS_ROOT");
            var base=entrada(caja,venta,BigDecimal.TEN);
            asientos.guardarYContabilizar(null,0,new Entrada(fecha.minusDays(1),Tipo.APERTURA,moneda,
                "Apertura histórica",null,null,base.lineas()),UUID.randomUUID());
            asientos.crear(base,UUID.randomUUID());
            for(int i=0;i<35;i++) asientos.guardarYContabilizar(null,0,base,UUID.randomUUID());
            var original=asientos.detalle(asientos.listar(new Filtro(null,null,null,Estado.CONTABILIZADO,null,"AS-000003"),0,10)
                .getContent().getFirst().id());
            asientos.reversar(original.fila().id(),original.version(),fecha,"Oculto por filtro",UUID.randomUUID());
            asientos.guardarYContabilizar(null,0,entrada(bancos,venta,BigDecimal.TEN),UUID.randomUUID());
            asientos.guardarYContabilizar(null,0,base,UUID.randomUUID());
            jdbc.update("UPDATE cuenta_contable SET activo=false WHERE id=?",caja);
            var empresaRepo=mock(EmpresaRepository.class);
            var emisor=mock(Empresa.class);
            when(emisor.getNombre()).thenReturn("Empresa del Mayor");
            when(emisor.getZonaHoraria()).thenReturn("America/Caracas");
            when(empresaRepo.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(emisor));
            var mayor=new LibroMayorService(new LibroMayorRepository(new NamedParameterJdbcTemplate(jdbc)),
                empresaRepo,mock(DatosEmpresaRepository.class));
            var f=new com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.Filtro(activos,fecha,fecha,Tipo.DIARIO,Origen.MANUAL,"REF-01");
            var primera=mayor.consultar(f,0,10);
            var segunda=mayor.consultar(f,1,10);
            var ultima=mayor.consultar(f,3,10);
            assertThat(primera.total()).isEqualTo(37);
            assertThat(primera.cuenta().agrupadora()).isTrue();
            assertThat(primera.movimientos().getFirst().estado()).isEqualTo(Estado.REVERSADO);
            assertThat(primera.movimientos().getLast().saldo().importe()).isEqualByComparingTo("110");
            assertThat(segunda.movimientos().getFirst().saldo().importe()).isEqualByComparingTo("120");
            assertThat(ultima.movimientos()).hasSize(7).extracting(m->m.cuentaId()).contains(bancos);
            assertThat(ultima.movimientos().getLast().saldo().importe()).isEqualByComparingTo("370");
            var t=primera.resumen().getFirst();
            assertThat(t.inicial().importe()).isEqualByComparingTo("10");
            assertThat(t.debitos()).isEqualByComparingTo("370");
            assertThat(t.creditos()).isZero();
            assertThat(t.cierre().importe()).isEqualByComparingTo("370");
            assertThat(ultima.resumen()).isEqualTo(primera.resumen());
            var inactiva=new com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.Filtro(caja,fecha,fecha,null,null,"");
            assertThat(mayor.cuentas()).anySatisfy(c->{assertThat(c.id()).isEqualTo(caja);assertThat(c.activo()).isFalse();});
            var resultado=mayor.consultar(inactiva,0,50);
            assertThat(resultado.movimientos()).hasSize(37).anySatisfy(m->assertThat(m.tipo()).isEqualTo(Tipo.REVERSION));
            assertThat(resultado.movimientos()).allSatisfy(m->assertThat(m.cuentaId()).isEqualTo(caja));
            var sinCoincidencias=new com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.Filtro(caja,fecha,fecha,null,null,"' OR 1=1 --");
            assertThat(mayor.consultar(sinCoincidencias,0,10).movimientos()).isEmpty();
            assertThat(mayor.consultar(sinCoincidencias,0,10).resumen().getFirst().cierre().importe()).isEqualByComparingTo("360");
            var pdfService=new LibroMayorPdfService(mayor,mock(LocalFileStorageService.class));
            try(var pdf=Loader.loadPDF(pdfService.generar(f).contenido())) {
                assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
                assertThat(new PDFTextStripper().getText(pdf)).contains("LIBRO MAYOR","Empresa del Mayor","CUENTA","AS-000040",
                    "Caja General","RESUMEN DEL PERÍODO","370.00 DB","Página 1 de").doesNotContain("ContaCloud","AS-000002");
            } catch(java.io.IOException ex) {throw new AssertionError(ex);}
            try(var pdf=Loader.loadPDF(pdfService.generar(inactiva).contenido())) {
                assertThat(new PDFTextStripper().getText(pdf)).contains("Inactiva","Reversión","360.00 DB");
            } catch(java.io.IOException ex) {throw new AssertionError(ex);}
            autenticar(tenant,UUID.randomUUID());
            assertThatThrownBy(()->mayor.consultar(f,0,10)).isInstanceOf(RecursoNoEncontradoException.class);
            autenticar(UUID.randomUUID(),empresa);
            assertThatThrownBy(()->pdfService.generar(f)).isInstanceOf(RecursoNoEncontradoException.class);
        });
    }

    @Test void mayorPaginaUnaCuentaConMilMovimientosYOrdenEstable() {
        var jdbc=new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            abrirPeriodo(jdbc);
            autenticar(tenant,empresa);
            var caja=cuenta(jdbc,"CASH_DEFAULT");
            var ventas=cuenta(jdbc,"SALES_REVENUE_DEFAULT");
            var asientos=servicio(jdbc);
            var lineas=new ArrayList<Movimiento>();
            for(int i=0;i<200;i++) lineas.add(new Movimiento(caja,null,BigDecimal.ONE,BigDecimal.ZERO));
            lineas.add(new Movimiento(ventas,null,BigDecimal.ZERO,BigDecimal.valueOf(200)));
            var e=new Entrada(fecha,Tipo.DIARIO,moneda,"Prueba de volumen",null,null,lineas);
            for(int i=0;i<5;i++) asientos.guardarYContabilizar(null,0,e,UUID.randomUUID());
            var mayor=new LibroMayorService(new LibroMayorRepository(new NamedParameterJdbcTemplate(jdbc)),null,null);
            var f=new com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.Filtro(caja,fecha,fecha,null,null,"");
            var inicio=mayor.consultar(f,0,10);
            var finalPeriodo=mayor.consultar(f,99,10);
            assertThat(inicio.total()).isEqualTo(1000);
            assertThat(inicio.movimientos().getFirst().saldo().importe()).isEqualByComparingTo("1");
            assertThat(finalPeriodo.movimientos().getFirst().saldo().importe()).isEqualByComparingTo("991");
            assertThat(finalPeriodo.movimientos().getLast().saldo().importe()).isEqualByComparingTo("1000");
            assertThat(finalPeriodo.resumen()).isEqualTo(inicio.resumen());
            var balanza=new BalanzaService(new BalanzaRepository(new NamedParameterJdbcTemplate(jdbc)),null,null);
            var totales=balanza.consultar(filtroBalanza(null,null,""),0,10).totales().generales().getFirst();
            assertThat(totales.debito()).isEqualByComparingTo("1000");
            assertThat(totales.credito()).isEqualByComparingTo("1000");
        });
    }

    @Test void balanzaConsolidaSaldosYExportaTodasLasCuentas() {
        var jdbc=new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            abrirPeriodo(jdbc);
            autenticar(tenant,empresa);
            var caja=cuenta(jdbc,"CASH_DEFAULT");
            var bancos=cuenta(jdbc,"BANK_DEFAULT");
            var activos=cuenta(jdbc,"ASSETS_ROOT");
            var venta=cuenta(jdbc,"SALES_REVENUE_DEFAULT");
            var asientos=servicio(jdbc);
            registrarBalanza(asientos,fecha.minusDays(1),caja,venta,100);
            registrarBalanza(asientos,fecha.minusDays(1),bancos,venta,20);
            var original=registrarBalanza(asientos,fecha,caja,venta,10);
            asientos.reversar(original,asientos.detalle(original).version(),fecha,"Reversión de prueba",UUID.randomUUID());
            registrarBalanza(asientos,fecha,venta,caja,150);
            asientos.crear(entrada(caja,venta,BigDecimal.TEN),UUID.randomUUID());
            jdbc.update("UPDATE periodos_fiscales SET estado='CERRADO' WHERE tenant_id=? AND empresa_id=?",tenant,empresa);
            jdbc.update("UPDATE cuenta_contable SET activo=false WHERE id=?",caja);
            jdbc.update("""
                INSERT INTO cuenta_contable(id,tenant_id,empresa_id,padre_id,codigo,nombre,tipo,clasificacion,naturaleza,solicitud_id,creado_por)
                SELECT gen_random_uuid(),?,?,?,'1.999.'||lpad(n::text,3,'0'),'Cuenta volumen '||lpad(n::text,3,'0'),
                       'MOVIMIENTO','ACTIVO','DEUDORA',gen_random_uuid(),?
                FROM generate_series(1,135) n
                """,tenant,empresa,activos,usuario);
            var empresaRepo=mock(EmpresaRepository.class);
            var emisor=mock(Empresa.class);
            when(emisor.getNombre()).thenReturn("Empresa prueba Balanza");
            when(emisor.getZonaHoraria()).thenReturn("America/Caracas");
            when(empresaRepo.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(emisor));
            var balanza=new BalanzaService(new BalanzaRepository(new NamedParameterJdbcTemplate(jdbc)),empresaRepo,mock(DatosEmpresaRepository.class));
            var completas=balanza.consultar(filtroBalanza(null,null,""),0,25);
            var total=completas.totales().generales().getFirst();
            assertThat(total.columnas()).usingElementComparator(BigDecimal::compareTo)
                .containsExactlyElementsOf(importesBalanza(120,120,170,170,50,50));
            assertThat(completas.totales().filtrados()).isEqualTo(completas.totales().generales());
            assertThat(completas.cuentas()).filteredOn(c->c.id().equals(caja)).singleElement().satisfies(c->{
                assertThat(c.activa()).isFalse();
                assertThat(c.importes().getFirst().columnas()).usingElementComparator(BigDecimal::compareTo)
                    .containsExactlyElementsOf(importesBalanza(100,0,10,160,0,50));
            });
            assertThat(completas.cuentas()).filteredOn(c->c.id().equals(activos)).singleElement().satisfies(c->
                assertThat(c.importes().getFirst().columnas()).usingElementComparator(BigDecimal::compareTo)
                    .containsExactlyElementsOf(importesBalanza(120,0,10,160,0,30)));
            var bancosSaldo=balanza.consultar(filtroBalanza(null,com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Mostrar.SALDO,"Bancos"),0,10);
            assertThat(bancosSaldo.cuentas()).singleElement().satisfies(c->assertThat(c.id()).isEqualTo(bancos));
            assertThat(bancosSaldo.totales().filtrados().getFirst().finalDebito()).isEqualByComparingTo("20");
            assertThat(balanza.consultar(filtroBalanza(null,com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Mostrar.MOVIMIENTOS,"Bancos"),0,10).total()).isZero();
            assertThat(balanza.consultar(filtroBalanza(1,null,"1"),0,10).cuentas()).allSatisfy(c->assertThat(c.nivel()).isEqualTo(1));
            assertThatThrownBy(()->balanza.consultar(filtroBalanza(999,null,""),0,10)).isInstanceOf(ReglaNegocioException.class);
            assertThat(balanza.consultar(filtroBalanza(null,null,"' OR 1=1 --"),0,10).total()).isZero();
            var filtro=filtroBalanza(2,com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Mostrar.TODAS,"Cuenta volumen");
            var pagina=balanza.consultar(filtro,0,10);
            assertThat(pagina.total()).isEqualTo(135);
            assertThat(pagina.cuentas()).hasSize(10);
            assertThat(balanza.consultar(filtro,13,10).cuentas()).hasSize(5);
            assertThat(balanza.consultar(filtro,13,10).totales()).isEqualTo(pagina.totales());
            var pdfService=new BalanzaPdfService(balanza,mock(LocalFileStorageService.class));
            try(var pdf=Loader.loadPDF(pdfService.generar(filtro).contenido())) {
                assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
                String texto=new PDFTextStripper().getText(pdf);
                assertThat(texto).contains("BALANZA DE COMPROBACIÓN","Empresa prueba Balanza","Cuenta volumen 001",
                    "Cuenta volumen 135","MOVIMIENTOS","Página 1 de","TOTALES DE LA CONSULTA","COMPROBACIÓN GENERAL")
                    .doesNotContain("ContaCloud","Caja General");
            } catch(java.io.IOException ex) {throw new AssertionError(ex);}
            autenticar(tenant,UUID.randomUUID());
            assertThat(balanza.consultar(filtroBalanza(null,null,""),0,10).total()).isZero();
            autenticar(UUID.randomUUID(),empresa);
            assertThat(balanza.consultar(filtroBalanza(null,null,""),0,10).total()).isZero();
        });
    }

    private UUID registrarBalanza(AsientoContableService asientos,LocalDate dia,UUID debe,UUID haber,int valor) {
        var importe=BigDecimal.valueOf(valor);
        var e=new Entrada(dia,Tipo.DIARIO,moneda,"Movimiento para balanza",null,null,List.of(
            new Movimiento(debe,null,importe,BigDecimal.ZERO),new Movimiento(haber,null,BigDecimal.ZERO,importe)));
        return asientos.guardarYContabilizar(null,0,e,UUID.randomUUID());
    }

    private com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Filtro filtroBalanza(Integer nivel,
            com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Mostrar mostrar,String buscar) {
        return new com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Filtro(fecha,fecha,nivel,mostrar,buscar);
    }

    private static List<BigDecimal> importesBalanza(int... valores) {
        return Arrays.stream(valores).mapToObj(v->BigDecimal.valueOf(v).setScale(2)).toList();
    }

    @Test void imputaCentrosPorLineaYConservaHistoriaAlReversar() {
        var jdbc=new JdbcTemplate(ds);
        new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            preparar(jdbc);
            abrirPeriodo(jdbc);
            autenticar(tenant,empresa);
            var named=new NamedParameterJdbcTemplate(jdbc);
            var centros=new CentroCostoService(new CentroCostoRepository(named),new CuentaContableRepository(named),new AuditoriaService(jdbc));
            var clave=UUID.randomUUID();
            var raiz=new com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.Entrada(" Tecnología ",null,null);
            var tecnologia=centros.crear(raiz,clave);
            assertThat(centros.crear(raiz,clave)).isEqualTo(tecnologia);
            var proyecto=centros.crear(new com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.Entrada("ERP",tecnologia,null),UUID.randomUUID());
            assertThat(centros.detalle(tecnologia).referencia().codigo()).isEqualTo("CC-001");
            assertThat(centros.detalle(proyecto).referencia().codigo()).isEqualTo("CC-002");
            assertThatThrownBy(()->centros.editar(tecnologia,0,new com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.Entrada(
                "Tecnología",proyecto,null))).hasMessageContaining("descendiente");
            var caja=cuenta(jdbc,"CASH_DEFAULT");
            var ventas=cuenta(jdbc,"SALES_REVENUE_DEFAULT");
            jdbc.update("UPDATE cuenta_contable SET uso_centro_costo='OBLIGATORIO' WHERE id=?",caja);
            var asientos=servicio(jdbc);
            var sinCentro=asientos.crear(entrada(caja,ventas,BigDecimal.TEN),UUID.randomUUID());
            assertThatThrownBy(()->asientos.contabilizar(sinCentro,0,UUID.randomUUID())).hasMessageContaining("centro de costos");
            var e=new Entrada(fecha,Tipo.DIARIO,moneda,"Imputación",null,null,List.of(
                new Movimiento(caja,null,BigDecimal.TEN,BigDecimal.ZERO,tecnologia),
                new Movimiento(ventas,null,BigDecimal.ZERO,BigDecimal.TEN,proyecto)));
            var id=asientos.guardarYContabilizar(null,0,e,UUID.randomUUID());
            assertThat(asientos.detalle(id).lineas()).extracting(l->l.centroCosto().id()).containsExactly(tecnologia,proyecto);
            var fuente=new Fuente(Origen.VENTAS,"Factura",UUID.randomUUID(),"FAC-CENTRO","CONTABILIZAR");
            var configuradas=List.of(new LineaConfigurada("CASH_DEFAULT",null,BigDecimal.TEN,BigDecimal.ZERO,tecnologia),
                new LineaConfigurada("SALES_REVENUE_DEFAULT",null,BigDecimal.ZERO,BigDecimal.TEN,proyecto));
            var automatico=asientos.registrarAutomatico(fuente,fecha,moneda,"Prueba automática",configuradas);
            assertThat(asientos.registrarAutomatico(fuente,fecha,moneda,"Prueba automática",configuradas)).isEqualTo(automatico);
            assertThat(asientos.detalle(automatico).lineas()).extracting(l->l.centroCosto().id()).containsExactly(tecnologia,proyecto);
            centros.desactivar(tecnologia,0);
            assertThat(asientos.centros("",0,10)).extracting(c->c.id()).containsExactly(proyecto);
            assertThatThrownBy(()->asientos.crear(e,UUID.randomUUID())).hasMessageContaining("activa");
            jdbc.update("UPDATE cuenta_contable SET uso_centro_costo='NO_PERMITIDO' WHERE id=?",ventas);
            var noPermitido=new Entrada(fecha,Tipo.DIARIO,moneda,"Imputación inválida",null,null,List.of(
                new Movimiento(caja,null,BigDecimal.TEN,BigDecimal.ZERO,proyecto),
                new Movimiento(ventas,null,BigDecimal.ZERO,BigDecimal.TEN,proyecto)));
            assertThatThrownBy(()->asientos.crear(noPermitido,UUID.randomUUID())).hasMessageContaining("si la admite");
            var reversa=asientos.reversar(id,1,fecha,"Corrige imputación",UUID.randomUUID());
            assertThat(asientos.detalle(reversa).lineas()).extracting(l->l.centroCosto().id()).containsExactly(tecnologia,proyecto);
            assertThat(asientos.detalle(id).lineas().getFirst().centroCosto().activo()).isFalse();
            assertThat(asientos.detalle(reversa).lineas().getFirst().credito()).isEqualByComparingTo("10");
            var diario=new LibroDiarioRepository(named);
            var principal=com.citacloud.springboot.contacloud.app.security.TenantContext.principalActual();
            assertThat(diario.asientos(principal,new com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.Filtro(
                fecha,fecha,null,null,""),0,10)).flatExtracting(a->a.movimientos()).extracting(m->m.centroCosto())
                .contains("CC-001 — Tecnología","CC-002 — ERP");
            autenticar(tenant,UUID.randomUUID());
            assertThatThrownBy(()->centros.detalle(tecnologia)).isInstanceOf(RecursoNoEncontradoException.class);
            assertThat(centros.listar(new com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.Filtro(null,""),0,10).total()).isZero();
        });
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
