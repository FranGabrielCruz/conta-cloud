package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Only random test invoices are written; existing company/customer/catalog records are read only. */
@EnabledIfEnvironmentVariable(named="CONTACLOUD_POSTGRES_TESTS",matches="true")
class NotaCreditoVentaPostgresTest {
    static PGSimpleDataSource ds;static LocalContainerEntityManagerFactoryBean emf;
    JdbcTemplate jdbc;TransactionTemplate tx;NotaCreditoVentaConsultaRepository query;NotaCreditoVentaService service;
    UUID tenant,company,branch,user,customer,currency,reason;final UUID invoice=UUID.randomUUID(),line=UUID.randomUUID();
    final List<UUID> intents=new CopyOnWriteArrayList<>();
    @BeforeAll static void connect(){
        ds=new PGSimpleDataSource();ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));ds.setPassword(System.getenv("DB_PASSWORD"));
        assertThat(new JdbcTemplate(ds).queryForObject("select current_database()",String.class)).isEqualTo("conta_cloud");
        emf=new LocalContainerEntityManagerFactoryBean();emf.setDataSource(ds);emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());emf.setPackagesToScan("com.citacloud.springboot.contacloud.app.models");emf.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));emf.afterPropertiesSet();
    }
    @AfterAll static void close(){if(emf!=null)emf.destroy();}
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new JpaTransactionManager(emf.getObject()));
        var source=jdbc.queryForMap("select f.id,f.tenant_id,f.empresa_id,f.branch_id,f.confirmed_by,f.customer_id,f.currency_id,l.id line_id from sales_invoice f join sales_invoice_line l on l.sales_invoice_id=f.id and l.tenant_id=f.tenant_id and l.empresa_id=f.empresa_id where f.status='CONFIRMED' and f.customer_id is not null and exists(select 1 from sales_return_reason r where r.tenant_id=f.tenant_id and r.empresa_id=f.empresa_id and r.active=true) order by f.confirmed_at desc limit 1");
        tenant=(UUID)source.get("tenant_id");company=(UUID)source.get("empresa_id");branch=(UUID)source.get("branch_id");user=(UUID)source.get("confirmed_by");customer=(UUID)source.get("customer_id");currency=(UUID)source.get("currency_id");
        reason=jdbc.queryForObject("select id from sales_return_reason where tenant_id=? and empresa_id=? and active=true order by id limit 1",UUID.class,tenant,company);
        jdbc.update("""
            insert into sales_invoice select (jsonb_populate_record(null::sales_invoice,to_jsonb(f)||jsonb_build_object(
            'id',?::uuid,'internal_number',?,'idempotency_key',?::uuid,'invoice_date',current_date,
            'confirmed_at',current_timestamp,'created_at',current_timestamp,'total',100,'subtotal',100,
            'discount_total',0,'surcharge_total',0,'tax_total',0,'received_amount',100,'change_amount',0,
            'payment_condition','Contado','payment_term_id',null,'credit_days',null,'due_date',null))).*
            from sales_invoice f where f.id=? and f.tenant_id=? and f.empresa_id=?
            """,invoice,"TEST-NCV-"+invoice.toString().substring(0,20),UUID.randomUUID(),source.get("id"),tenant,company);
        jdbc.update("""
            insert into sales_invoice_line select (jsonb_populate_record(null::sales_invoice_line,to_jsonb(l)||jsonb_build_object(
            'id',?::uuid,'sales_invoice_id',?::uuid,'quantity',1,'unit_price',100,'gross_subtotal',100,
            'discount_amount',0,'surcharge_amount',0,'taxable_base_amount',100,'tax_rate',0,'tax_amount',0,'subtotal',100,'total',100,'line_order',1))).*
            from sales_invoice_line l where l.id=? and l.tenant_id=? and l.empresa_id=?
            """,line,invoice,source.get("line_id"),tenant,company);
        auth(tenant,company,true,Set.of());
        var repositories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf.getObject()));query=spy(new NotaCreditoVentaConsultaRepository(jdbc));
        // Exercise all writes but never consume an operational document number in committed fixtures.
        doAnswer(x->"TEST-NC-"+UUID.randomUUID().toString().substring(0,20)).when(query).siguiente();
        service=new NotaCreditoVentaService(repositories.getRepository(FacturaVentaRepository.class),repositories.getRepository(LineaFacturaVentaRepository.class),repositories.getRepository(MotivoDevolucionVentaRepository.class),query,repositories.getRepository(EmpresaRepository.class),repositories.getRepository(DatosEmpresaRepository.class),repositories.getRepository(PeriodoFiscalRepository.class),new IdempotentOperationService(jdbc,new SimpleMeterRegistry(),10000),new AuditoriaService(jdbc));
    }
    void auth(UUID t,UUID c,boolean all,Set<UUID> branches){var p=new TenantPrincipal(user,t,c,null,"TEST","Prueba","test","",true,all,branches,Set.of("notas_credito.ver","notas_credito.crear","notas_credito.emitir","notas_credito.pdf"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    EmitirNotaCreditoVentaInput input(String qty){var key=UUID.randomUUID();intents.add(key);return new EmitirNotaCreditoVentaInput(invoice,LocalDate.now(),reason,"Ajuste de prueba aislada",null,List.of(new EmitirNotaCreditoVentaInput.Linea(line,new BigDecimal(qty))),key);}
    NotaCreditoVentaDocumento issue(EmitirNotaCreditoVentaInput input){return tx.execute(x->service.emitir(input));}
    void receivable(int applied){jdbc.update("insert into customer_accounts_receivable(id,tenant_id,empresa_id,sales_invoice_id,customer_id,currency_id,original_amount,applied_amount,due_date,status) values(?,?,?,?,?,?,100,?,current_date+30,?)",UUID.randomUUID(),tenant,company,invoice,customer,currency,applied,applied==100?"PAID":"PENDING");}
    int notes(){return jdbc.queryForObject("select count(*) from sales_credit_note where tenant_id=? and empresa_id=? and sales_invoice_id=?",Integer.class,tenant,company,invoice);}
    BigDecimal applied(){return jdbc.queryForObject("select applied_amount from customer_accounts_receivable where tenant_id=? and empresa_id=? and sales_invoice_id=?",BigDecimal.class,tenant,company,invoice);}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();if(jdbc==null||company==null)return;
        jdbc.update("delete from auditoria where empresa_id=? and entidad='NotaCreditoVenta' and registro_id in(select id from sales_credit_note where tenant_id=? and empresa_id=? and sales_invoice_id=?)",company,tenant,company,invoice);
        jdbc.update("delete from customer_credit_ledger where tenant_id=? and empresa_id=? and sales_credit_note_id in(select id from sales_credit_note where tenant_id=? and empresa_id=? and sales_invoice_id=?)",tenant,company,tenant,company,invoice);
        jdbc.update("delete from sales_credit_note_line where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        jdbc.update("delete from sales_credit_note where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        for(UUID key:intents)jdbc.update("delete from operaciones_idempotentes where tenant_id=? and empresa_id=? and tipo_operacion in('SALES_CREDIT_NOTE_ISSUE','SALES_CREDIT_NOTE_VOID') and clave_idempotencia=?",tenant,company,key);
        jdbc.update("delete from customer_accounts_receivable where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        jdbc.update("delete from sales_return_line where tenant_id=? and empresa_id=? and sales_return_id in(select id from sales_return where tenant_id=? and empresa_id=? and sales_invoice_id=?)",tenant,company,tenant,company,invoice);
        jdbc.update("delete from sales_return where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        jdbc.update("delete from sales_invoice_line where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        jdbc.update("delete from sales_invoice where tenant_id=? and empresa_id=? and id=?",tenant,company,invoice);
    }
    @Test void parcialmentePendienteReintentoYDocumentoHistorico(){
        receivable(0);var intent=input("0.25");var d=issue(intent);assertThat(issue(intent).id()).isEqualTo(d.id());assertThat(notes()).isEqualTo(1);assertThat(applied()).isEqualByComparingTo("25");
        assertThat(d.aplicadoCxC()).isEqualByComparingTo("25");assertThat(d.creditoCliente()).isZero();
        jdbc.update("update sales_invoice set customer_name='Nombre cambiado en prueba' where tenant_id=? and empresa_id=? and id=?",tenant,company,invoice);
        assertThat(query.documento(d.id())).isEqualTo(d);assertThat(service.buscar("TEST-NC-","",null,null,0,10).getContent()).extracting(NotaCreditoVentaConsultaRepository.Resumen::id).contains(d.id());
        assertThat(service.facturas("TEST-NCV-"+invoice.toString().substring(0,20),0,25).getContent()).hasSize(1);
        issue(input("0.5"));issue(input("0.25"));assertThat(applied()).isEqualByComparingTo("100");assertThat(query.consumido(line).cantidad()).isEqualByComparingTo("1");
        assertThat(service.facturas("TEST-NCV-"+invoice.toString().substring(0,20),0,25).getContent()).isEmpty();assertThatThrownBy(()->issue(input("0.01"))).isInstanceOf(ReglaNegocioException.class);assertThat(notes()).isEqualTo(3);
    }
    @Test void facturaPagadaGeneraCreditoSinSalidaDeCaja(){
        receivable(100);var d=issue(input("1"));assertThat(d.aplicadoCxC()).isZero();assertThat(d.creditoCliente()).isEqualByComparingTo("100");assertThat(applied()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select available_amount from customer_credit_ledger where tenant_id=? and empresa_id=? and sales_credit_note_id=?",BigDecimal.class,tenant,company,d.id())).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select count(*) from movimientos_financieros where tenant_id=? and empresa_id=? and origen_id=?",Integer.class,tenant,company,d.id())).isZero();
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void dosEmisionesConcurrentesUnaUnidadSoloUnGanador(boolean anonymous)throws Exception{
        if(anonymous)anonymous("Consumidor Final");else receivable(0);var first=input("1");var second=input("1");var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var tasks=List.of(first,second).stream().map(i->pool.submit(()->{auth(tenant,company,true,Set.of());try{start.await();issue(i);return true;}catch(ReglaNegocioException e){return false;}finally{SecurityContextHolder.clearContext();}})).toList();
            start.countDown();int success=0;for(var f:tasks)if(f.get(30,TimeUnit.SECONDS))success++;assertThat(success).isEqualTo(1);
        }
        assertThat(notes()).isEqualTo(1);if(!anonymous)assertThat(applied()).isEqualByComparingTo("100");assertThat(query.consumido(line).cantidad()).isEqualByComparingTo("1");
    }
    @Test void falloDuranteInsercionRevierteSaldoNotaEIdempotencia(){
        receivable(0);doThrow(new IllegalStateException("Fallo de prueba")).when(query).insertar(any(),any(),any());var intent=input("1");assertThatThrownBy(()->issue(intent)).isInstanceOf(IllegalStateException.class);
        assertThat(notes()).isZero();assertThat(applied()).isZero();assertThat(jdbc.queryForObject("select count(*) from operaciones_idempotentes where tenant_id=? and empresa_id=? and clave_idempotencia=?",Integer.class,tenant,company,intent.idempotencia())).isZero();
    }
    @Test void pdfPosteriorNoReemiteYProtegeTenantEmpresaYSucursal(){
        receivable(0);var d=issue(input("1"));var pdf=spy(new NotaCreditoVentaPdfService(query));doThrow(new ReglaNegocioException("Fallo PDF de prueba")).when(pdf).render(any());assertThatThrownBy(()->pdf.generar(d.id())).isInstanceOf(ReglaNegocioException.class);
        assertThat(new NotaCreditoVentaPdfService(query).generar(d.id()).contenido()).isNotEmpty();assertThat(notes()).isEqualTo(1);assertThat(applied()).isEqualByComparingTo("100");
        auth(UUID.randomUUID(),company,true,Set.of());assertThatThrownBy(()->query.documento(d.id())).isInstanceOf(RecursoNoEncontradoException.class);assertThat(service.buscar("","",null,null,0,10)).isEmpty();
        auth(tenant,UUID.randomUUID(),true,Set.of());assertThatThrownBy(()->service.preparar(invoice)).isInstanceOf(RecursoNoEncontradoException.class);
        auth(tenant,company,false,Set.of(UUID.randomUUID()));assertThatThrownBy(()->query.documento(d.id())).isInstanceOf(RecursoNoEncontradoException.class);assertThat(service.buscar("TEST-NC-","",null,null,0,10)).isEmpty();
    }
    void anonymous(String name){jdbc.update("update sales_invoice set customer_id=null,customer_name=?,customer_identification=null where tenant_id=? and empresa_id=? and id=?",name,tenant,company,invoice);}
    String number(){return "TEST-NCV-"+invoice.toString().substring(0,20);}
    @Test void consumidorFinalAdmiteParcialTotalEIdempotenciaSinCrearClienteSaldoNiReembolso(){
        anonymous("Consumidor Final");long customers=jdbc.queryForObject("select count(*) from customer where tenant_id=? and empresa_id=?",Long.class,tenant,company);
        assertThat(service.facturas(number(),0,25).getContent()).hasSize(1);var prep=tx.execute(x->service.preparar(invoice));assertThat(prep.cliente()).isEqualTo("Consumidor Final");assertThat(prep.disponible()).isEqualByComparingTo("100");
        var intent=input("0.25");var first=issue(intent);assertThat(issue(intent).id()).isEqualTo(first.id());assertThat(first.clienteId()).isNull();assertThat(first.creditoCliente()).isZero();assertThat(first.aplicadoCxC()).isZero();assertThat(query.documento(first.id()).cliente()).isEqualTo("Consumidor Final");
        assertThat(tx.execute(x->service.preparar(invoice)).disponible()).isEqualByComparingTo("75");issue(input("0.75"));assertThat(notes()).isEqualTo(2);assertThat(service.facturas(number(),0,25)).isEmpty();assertThatThrownBy(()->issue(input("0.01"))).isInstanceOf(ReglaNegocioException.class);
        assertThat(jdbc.queryForObject("select customer_id from sales_invoice where tenant_id=? and empresa_id=? and id=?",UUID.class,tenant,company,invoice)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from customer where tenant_id=? and empresa_id=?",Long.class,tenant,company)).isEqualTo(customers);
        assertThat(jdbc.queryForObject("select count(*) from customer_credit_ledger where tenant_id=? and empresa_id=? and sales_credit_note_id in(select id from sales_credit_note where sales_invoice_id=? and tenant_id=? and empresa_id=?)",Integer.class,tenant,company,invoice,tenant,company)).isZero();
        assertThat(new NotaCreditoVentaPdfService(query).generar(first.id()).contenido()).isNotEmpty();
    }
    @Test void nombreHistoricoYFallbackDeFacturaSinClienteConservanAislamiento(){
        anonymous("Comprador histórico sin ficha");assertThat(service.facturas(number(),0,25).getContent().getFirst().cliente()).isEqualTo("Comprador histórico sin ficha");
        anonymous("");assertThat(service.facturas(number(),0,25).getContent().getFirst().cliente()).isEqualTo("Consumidor Final");
        auth(UUID.randomUUID(),company,true,Set.of());assertThat(service.facturas(number(),0,25)).isEmpty();assertThatThrownBy(()->tx.execute(x->service.preparar(invoice))).isInstanceOf(RecursoNoEncontradoException.class);
        auth(tenant,UUID.randomUUID(),true,Set.of());assertThat(service.facturas(number(),0,25)).isEmpty();assertThatThrownBy(()->tx.execute(x->service.preparar(invoice))).isInstanceOf(RecursoNoEncontradoException.class);
        auth(tenant,company,false,Set.of(UUID.randomUUID()));assertThat(service.facturas(number(),0,25)).isEmpty();assertThatThrownBy(()->tx.execute(x->service.preparar(invoice))).isInstanceOf(RecursoNoEncontradoException.class);
    }
    @Test void logoSeCongelaAlEmitirYNoDependeDelArchivoActual(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory){
        var storage=new LocalFileStorageService(directory.toString());byte[] logo=NotaCreditoVentaPdfServiceTest.testLogo();String key=storage.store("test-credit-note","png",logo);
        var companies=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf.getObject())).getRepository(EmpresaRepository.class);
        var real=tx.execute(x->companies.findByIdAndTenantId(company,tenant).orElseThrow());var snapshot=spy(real);doReturn(key).when(snapshot).getLogoObjectKey();var source=mock(EmpresaRepository.class);when(source.findByIdAndTenantId(company,tenant)).thenReturn(Optional.of(snapshot));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"empresas",source);org.springframework.test.util.ReflectionTestUtils.setField(service,"storage",storage);
        var issued=issue(input("1"));assertThat(issued.logoEmpresa()).containsExactly(logo);storage.delete(key);
        assertThat(query.documento(issued.id()).logoEmpresa()).containsExactly(logo);var pdf=new NotaCreditoVentaPdfService(query);assertThat(pdf.generar(issued.id()).contenido()).isNotEmpty();assertThat(pdf.generar(issued.id()).contenido()).isNotEmpty();assertThat(notes()).isEqualTo(1);
    }
    AnularNotaCreditoVentaInput voidInput(NotaCreditoVentaDocumento d){var key=UUID.randomUUID();intents.add(key);return new AnularNotaCreditoVentaInput(d.id(),query.ciclo(d.id(),false).version(),"Error autorizado de prueba",key);}
    NotaCreditoVentaDocumento annul(AnularNotaCreditoVentaInput input){return tx.execute(x->service.anular(input));}
    @Test void anulaCxCConservaSnapshotLiberaDisponibilidadYReintentoNoDuplica(){
        receivable(0);var d=issue(input("0.25"));String snapshot=jdbc.queryForObject("select document_snapshot from sales_credit_note where tenant_id=? and empresa_id=? and id=?",String.class,tenant,company,d.id());var command=voidInput(d);
        assertThat(annul(command).estado()).isEqualTo("VOIDED");assertThat(annul(command).id()).isEqualTo(d.id());assertThat(applied()).isZero();assertThat(notes()).isEqualTo(1);assertThat(query.ciclo(d.id(),false).version()).isEqualTo(1);assertThat(query.ciclo(d.id(),false).motivo()).isEqualTo(command.motivo());assertThat(query.ciclo(d.id(),false).anuladaEn()).isNotNull();
        assertThat(jdbc.queryForObject("select document_snapshot from sales_credit_note where tenant_id=? and empresa_id=? and id=?",String.class,tenant,company,d.id())).isEqualTo(snapshot);
        assertThat(query.totalAcreditado(invoice)).isZero();assertThat(query.consumido(line).cantidad()).isZero();assertThat(tx.execute(x->service.preparar(invoice)).disponible()).isEqualByComparingTo("100");
        assertThat(service.buscar("TEST-NC-","",null,null,0,10,"VOIDED")).hasSize(1);assertThat(service.buscar("TEST-NC-","",null,null,0,10,"CONFIRMED")).isEmpty();
        assertThatThrownBy(()->annul(voidInput(d))).isInstanceOf(ReglaNegocioException.class);assertThat(applied()).isZero();
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(new NotaCreditoVentaPdfService(query).generar(d.id()).contenido())){assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf)).contains("ANULADA");}catch(java.io.IOException e){throw new AssertionError(e);}
    }
    @Test void anulaSaldoAFavorNoConsumidoYBloqueaSaldoUtilizado(){
        receivable(100);var d=issue(input("1"));jdbc.update("update customer_credit_ledger set available_amount=50 where tenant_id=? and empresa_id=? and sales_credit_note_id=?",tenant,company,d.id());
        assertThat(query.ciclo(d.id(),false).anulable()).isFalse();assertThatThrownBy(()->annul(voidInput(d))).isInstanceOf(ReglaNegocioException.class);assertThat(query.documento(d.id()).estado()).isEqualTo("CONFIRMED");
        jdbc.update("update customer_credit_ledger set available_amount=amount where tenant_id=? and empresa_id=? and sales_credit_note_id=?",tenant,company,d.id());annul(voidInput(d));
        assertThat(jdbc.queryForObject("select available_amount from customer_credit_ledger where tenant_id=? and empresa_id=? and sales_credit_note_id=?",BigDecimal.class,tenant,company,d.id())).isZero();assertThat(applied()).isEqualByComparingTo("100");
    }
    @Test void falloAlMarcarAnulacionRevierteLiquidacionYSnapshot(){
        receivable(0);var d=issue(input("1"));doThrow(new IllegalStateException("Fallo de prueba después de revertir")).when(query).marcarAnulada(any(),anyLong(),any(),any(),any());
        assertThatThrownBy(()->annul(voidInput(d))).isInstanceOf(IllegalStateException.class);assertThat(applied()).isEqualByComparingTo("100");assertThat(query.documento(d.id()).estado()).isEqualTo("CONFIRMED");assertThat(query.ciclo(d.id(),false).version()).isZero();
    }
    @Test void consumidorFinalSeAnulaSinCrearCuentaDeCliente(){
        anonymous("Consumidor Final");var d=issue(input("1"));annul(voidInput(d));assertThat(query.documento(d.id()).clienteId()).isNull();assertThat(query.totalAcreditado(invoice)).isZero();assertThat(query.consumido(line).cantidad()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from customer_accounts_receivable where tenant_id=? and empresa_id=? and sales_invoice_id=?",Integer.class,tenant,company,invoice)).isZero();
    }
    @Test void dosAnulacionesConcurrentesSoloReviertenUnaVez()throws Exception{
        receivable(0);var d=issue(input("1"));var commands=List.of(voidInput(d),voidInput(d));var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){var tasks=commands.stream().map(command->pool.submit(()->{auth(tenant,company,true,Set.of());try{start.await();annul(command);return true;}catch(ReglaNegocioException e){return false;}finally{SecurityContextHolder.clearContext();}})).toList();start.countDown();int success=0;for(var result:tasks)if(result.get(30,TimeUnit.SECONDS))success++;assertThat(success).isEqualTo(1);}
        assertThat(applied()).isZero();assertThat(query.ciclo(d.id(),false).version()).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from auditoria where empresa_id=? and registro_id=? and accion='SALES_CREDIT_NOTE_VOIDED'",Integer.class,company,d.id())).isEqualTo(1);
    }
    @Test void notaDeDevolucionSeAnulaSinModificarRetornoFisico(){
        UUID returned=UUID.randomUUID(),note=UUID.randomUUID();
        jdbc.update("""
            insert into sales_return(id,tenant_id,empresa_id,return_number,sales_invoice_id,invoice_number,reason_id,reason_code,reason_name,returns_inventory,customer_id,customer_name,customer_identification,currency_id,currency_code,currency_symbol,gross_subtotal,discount_total,surcharge_total,tax_total,total,operation_key,confirmed_at,confirmed_by)
            select ?,f.tenant_id,f.empresa_id,?,f.id,f.internal_number,r.id,r.code,r.name,false,f.customer_id,f.customer_name,f.customer_identification,f.currency_id,f.currency_code,f.currency_symbol,100,0,0,0,100,?,current_timestamp,?
            from sales_invoice f join sales_return_reason r on r.id=? and r.tenant_id=f.tenant_id and r.empresa_id=f.empresa_id where f.tenant_id=? and f.empresa_id=? and f.id=?
            """,returned,"TEST-DEV-"+returned.toString().substring(0,20),UUID.randomUUID(),user,reason,tenant,company,invoice);
        jdbc.update("""
            insert into sales_return_line(tenant_id,empresa_id,sales_return_id,sales_invoice_line_id,product_id,product_code,description,unit_of_measure_id,unit_name,returned_quantity,unit_price,gross_subtotal,discount_amount,surcharge_amount,taxable_base_amount,tax_rate,tax_amount,total,track_inventory,line_order)
            select tenant_id,empresa_id,?,id,product_id,product_code,description,unit_of_measure_id,unit_name,1,100,100,0,0,100,0,0,100,false,1 from sales_invoice_line where tenant_id=? and empresa_id=? and id=?
            """,returned,tenant,company,line);
        assertThat(notes()).isZero();
        jdbc.update("insert into sales_credit_note(id,tenant_id,empresa_id,credit_note_number,sales_return_id,sales_invoice_id,customer_id,currency_id,amount,customer_credit_amount,confirmed_at,confirmed_by) values(?,?,?,?,?,?,?,?,100,100,current_timestamp,?)",note,tenant,company,"TEST-NC-"+note.toString().substring(0,20),returned,invoice,customer,currency,user);
        jdbc.update("insert into customer_credit_ledger(tenant_id,empresa_id,customer_id,currency_id,sales_credit_note_id,amount,available_amount,created_by) values(?,?,?,?,?,100,100,?)",tenant,company,customer,currency,note,user);
        var d=query.documento(note);assertThat(query.ciclo(note,false).devolucionId()).isEqualTo(returned);assertThat(query.ciclo(note,false).estadoOrigen()).isEqualTo("CONFIRMED");annul(voidInput(d));
        assertThat(jdbc.queryForObject("select status from sales_return where tenant_id=? and empresa_id=? and id=?",String.class,tenant,company,returned)).isEqualTo("CONFIRMED");assertThat(jdbc.queryForObject("select returned_quantity from sales_return_line where tenant_id=? and empresa_id=? and sales_return_id=?",BigDecimal.class,tenant,company,returned)).isEqualByComparingTo("1");assertThat(query.consumido(line).cantidad()).isZero();assertThat(notes()).isEqualTo(1);
    }
    @Test void anularConTenantEmpresaOSucursalAjenosNoCambiaSaldos(){
        receivable(0);var d=issue(input("1"));var command=voidInput(d);
        auth(UUID.randomUUID(),company,true,Set.of());assertThatThrownBy(()->annul(command)).isInstanceOf(RecursoNoEncontradoException.class);
        auth(tenant,UUID.randomUUID(),true,Set.of());assertThatThrownBy(()->annul(command)).isInstanceOf(RecursoNoEncontradoException.class);
        auth(tenant,company,false,Set.of(UUID.randomUUID()));assertThatThrownBy(()->annul(command)).isInstanceOf(RecursoNoEncontradoException.class);
        auth(tenant,company,true,Set.of());assertThat(query.documento(d.id()).estado()).isEqualTo("CONFIRMED");assertThat(applied()).isEqualByComparingTo("100");
    }
    @Test void anulaNotaMixtaSinRevertirPagosPrevios(){
        receivable(40);var d=issue(input("1"));assertThat(d.aplicadoCxC()).isEqualByComparingTo("60");assertThat(d.creditoCliente()).isEqualByComparingTo("40");annul(voidInput(d));assertThat(applied()).isEqualByComparingTo("40");
        assertThat(jdbc.queryForObject("select available_amount from customer_credit_ledger where tenant_id=? and empresa_id=? and sales_credit_note_id=?",BigDecimal.class,tenant,company,d.id())).isZero();
    }
}

