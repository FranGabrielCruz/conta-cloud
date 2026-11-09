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
class NotaDebitoVentaPostgresTest {
    static PGSimpleDataSource ds;static LocalContainerEntityManagerFactoryBean emf;
    JdbcTemplate jdbc;TransactionTemplate tx;NotaDebitoVentaRepository query;NotaDebitoVentaService service;
    UUID tenant,company,branch,user,customer,currency,reason,testBranch;
    final UUID testCash=UUID.randomUUID(),testSession=UUID.randomUUID(),testOperator=UUID.randomUUID();boolean cashFixture;FinancialMovementRepository moneyMovements;CobroClienteService receiptService;final UUID invoice=UUID.randomUUID(),line=UUID.randomUUID();
    final List<UUID> intents=new CopyOnWriteArrayList<>();
    @BeforeAll static void connect(){
        ds=new PGSimpleDataSource();ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));ds.setPassword(System.getenv("DB_PASSWORD"));
        org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        assertThat(new JdbcTemplate(ds).queryForObject("select current_database()",String.class)).isEqualTo("conta_cloud");
        emf=new LocalContainerEntityManagerFactoryBean();emf.setDataSource(ds);emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());emf.setPackagesToScan("com.citacloud.springboot.contacloud.app.models");emf.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));emf.afterPropertiesSet();
    }
    @AfterAll static void close(){if(emf!=null)emf.destroy();}
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new JpaTransactionManager(emf.getObject()));
        var source=jdbc.queryForMap("select f.id,f.tenant_id,f.empresa_id,f.branch_id,f.confirmed_by,f.customer_id,f.currency_id,l.id line_id from sales_invoice f join sales_invoice_line l on l.sales_invoice_id=f.id and l.tenant_id=f.tenant_id and l.empresa_id=f.empresa_id where f.status='CONFIRMED' and f.customer_id is not null and exists(select 1 from sales_return_reason r where r.tenant_id=f.tenant_id and r.empresa_id=f.empresa_id and r.active=true) order by f.confirmed_at desc limit 1");
        tenant=(UUID)source.get("tenant_id");company=(UUID)source.get("empresa_id");branch=(UUID)source.get("branch_id");user=(UUID)source.get("confirmed_by");customer=(UUID)source.get("customer_id");currency=(UUID)source.get("currency_id");
        reason=jdbc.queryForObject("select id from sales_debit_note_reason where tenant_id=? and empresa_id=? and active=true order by id limit 1",UUID.class,tenant,company);
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
        var repositories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf.getObject()));query=spy(new NotaDebitoVentaRepository(jdbc));
        // Exercise all writes but never consume an operational document number in committed fixtures.
        doAnswer(x->"TEST-NC-"+UUID.randomUUID().toString().substring(0,20)).when(query).siguiente();
        moneyMovements=repositories.getRepository(FinancialMovementRepository.class);
        var cashRegisters=repositories.getRepository(CashRegisterRepository.class);var bankAccounts=repositories.getRepository(BankAccountRepository.class);
        var cashSessions=new CashRegisterSessionService(repositories.getRepository(CashRegisterSessionRepository.class),cashRegisters,moneyMovements,repositories.getRepository(EmpresaRepository.class),new com.citacloud.springboot.contacloud.app.mappers.SesionCajaMapper(),new AuditoriaService(jdbc),repositories.getRepository(SucursalRepository.class));
        receiptService=new CobroClienteService(new CobroClienteRepository(jdbc),repositories.getRepository(ClienteRepository.class),repositories.getRepository(MonedaRepository.class),repositories.getRepository(SucursalRepository.class),repositories.getRepository(MetodoPagoVentaRepository.class),new CuentaDineroResolver(cashRegisters,bankAccounts),cashSessions,moneyMovements,repositories.getRepository(BankReconciliationMatchRepository.class),new AuditoriaService(jdbc));
        var cashCollections=new CobroContadoDocumentoService(repositories.getRepository(MetodoPagoVentaRepository.class),bankAccounts,new CuentaDineroResolver(cashRegisters,bankAccounts),cashSessions,moneyMovements,repositories.getRepository(BankReconciliationMatchRepository.class),query);
        service=new NotaDebitoVentaService(repositories.getRepository(FacturaVentaRepository.class),query,repositories.getRepository(ImpuestoRepository.class),repositories.getRepository(EmpresaRepository.class),repositories.getRepository(DatosEmpresaRepository.class),repositories.getRepository(PeriodoFiscalRepository.class),new IdempotentOperationService(jdbc,new SimpleMeterRegistry(),10000),new AuditoriaService(jdbc),new PurchaseOrderCalculationService(),null,cashCollections);
    }
    void auth(UUID t,UUID c,boolean all,Set<UUID> branches){var p=new TenantPrincipal(user,t,c,null,"TEST","Prueba","test","",true,all,branches,Set.of("notas_debito.ver","notas_debito.crear","notas_debito.emitir","notas_debito.pdf"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    EmitirNotaDebitoVentaInput input(String qty){var key=UUID.randomUUID();intents.add(key);return new EmitirNotaDebitoVentaInput(invoice,LocalDate.now(),reason,"Ajuste de prueba aislada",List.of(new EmitirNotaDebitoVentaInput.Linea("Cargo",BigDecimal.ONE,new BigDecimal(qty),BigDecimal.ZERO,null)),key);}
    NotaDebitoVentaDocumento issue(EmitirNotaDebitoVentaInput input){return tx.execute(x->service.emitir(input));}
    void receivable(int applied){jdbc.update("insert into customer_accounts_receivable(id,tenant_id,empresa_id,sales_invoice_id,customer_id,currency_id,original_amount,applied_amount,due_date,status) values(?,?,?,?,?,?,100,?,current_date+30,?)",UUID.randomUUID(),tenant,company,invoice,customer,currency,applied,applied==100?"PAID":"PENDING");}
    int notes(){return jdbc.queryForObject("select count(*) from sales_debit_note where tenant_id=? and empresa_id=? and sales_invoice_id=?",Integer.class,tenant,company,invoice);}
    BigDecimal applied(){return jdbc.queryForObject("select applied_amount from customer_accounts_receivable where tenant_id=? and empresa_id=? and sales_invoice_id=?",BigDecimal.class,tenant,company,invoice);}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();if(jdbc==null||company==null)return;
        jdbc.update("delete from auditoria where empresa_id=? and entidad='NotaDebitoVenta' and registro_id in(select id from sales_debit_note where tenant_id=? and empresa_id=? and sales_invoice_id=?)",company,tenant,company,invoice);
        jdbc.update("delete from auditoria where empresa_id=? and entidad='NotaCreditoVenta' and registro_id in(select id from sales_credit_note where tenant_id=? and empresa_id=? and sales_invoice_id=?)",company,tenant,company,invoice);
        jdbc.update("delete from sales_credit_note_line where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        jdbc.update("delete from sales_credit_note where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        var movementIds=jdbc.queryForList("select p.financial_movement_id from sales_debit_note_payment p join sales_debit_note n on n.id=p.sales_debit_note_id and n.tenant_id=p.tenant_id and n.empresa_id=p.empresa_id where n.tenant_id=? and n.empresa_id=? and n.sales_invoice_id=?",UUID.class,tenant,company,invoice);
        jdbc.update("delete from sales_debit_note_payment where tenant_id=? and empresa_id=? and sales_debit_note_id in(select id from sales_debit_note where sales_invoice_id=?)",tenant,company,invoice);
        for(var mid:movementIds)jdbc.update("delete from movimientos_financieros where tenant_id=? and empresa_id=? and id=?",tenant,company,mid);
        jdbc.update("delete from customer_accounts_receivable where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        jdbc.update("delete from sales_debit_note_line where tenant_id=? and empresa_id=? and sales_debit_note_id in(select id from sales_debit_note where sales_invoice_id=?)",tenant,company,invoice);
        jdbc.update("delete from sales_debit_note where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        for(UUID key:intents)jdbc.update("delete from operaciones_idempotentes where tenant_id=? and empresa_id=? and tipo_operacion in('SALES_DEBIT_NOTE_ISSUE','SALES_DEBIT_NOTE_VOID','SALES_CREDIT_NOTE_ISSUE') and clave_idempotencia=?",tenant,company,key);
        jdbc.update("delete from customer_accounts_receivable where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        jdbc.update("delete from sales_return_line where tenant_id=? and empresa_id=? and sales_return_id in(select id from sales_return where tenant_id=? and empresa_id=? and sales_invoice_id=?)",tenant,company,tenant,company,invoice);
        jdbc.update("delete from sales_return where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        jdbc.update("delete from sales_invoice_line where tenant_id=? and empresa_id=? and sales_invoice_id=?",tenant,company,invoice);
        jdbc.update("delete from sales_invoice where tenant_id=? and empresa_id=? and id=?",tenant,company,invoice);
        if(cashFixture){jdbc.update("delete from cash_register_session where tenant_id=? and empresa_id=? and id=?",tenant,company,testSession);jdbc.update("delete from cajas where tenant_id=? and empresa_id=? and id=?",tenant,company,testCash);jdbc.update("delete from usuarios where id=? and empresa_id=?",testOperator,company);}
        if(testBranch!=null)jdbc.update("delete from sucursales where id=? and empresa_id=?",testBranch,company);
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void facturaCreditoConOSinCuentaPreviaConservaDeudaOriginal(boolean existing){
      UUID term=jdbc.queryForObject("select id from condiciones_pago where tenant_id=? and empresa_id=? and activo and dias>0 order by dias limit 1",UUID.class,tenant,company);
      jdbc.update("update sales_invoice set payment_condition='Crédito',payment_term_id=?,credit_days=30,due_date=current_date+30,received_amount=0,change_amount=0 where id=?",term,invoice);
      if(existing)receivable(0);var d=issue(input("50"));assertThat(d.saldoAnterior()).isEqualByComparingTo("100");assertThat(balance()).isEqualByComparingTo("150");tx.execute(x->service.anular(voidInput(d.id())));assertThat(balance()).isEqualByComparingTo("100");
    }
    @Test void notasCreditoYDebitoCoexistenSinCambiarFactura(){
      receivable(0);var factories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf.getObject()));
      var credits=new NotaCreditoVentaService(factories.getRepository(FacturaVentaRepository.class),factories.getRepository(LineaFacturaVentaRepository.class),factories.getRepository(MotivoDevolucionVentaRepository.class),new NotaCreditoVentaConsultaRepository(jdbc),factories.getRepository(EmpresaRepository.class),factories.getRepository(DatosEmpresaRepository.class),factories.getRepository(PeriodoFiscalRepository.class),new IdempotentOperationService(jdbc,new SimpleMeterRegistry(),10000),new AuditoriaService(jdbc));
      UUID creditReason=jdbc.queryForObject("select id from sales_return_reason where tenant_id=? and empresa_id=? and active order by id limit 1",UUID.class,tenant,company),key=UUID.randomUUID();intents.add(key);
      var creditInput=new EmitirNotaCreditoVentaInput(invoice,LocalDate.now(),creditReason,"Crédito financiero de prueba",null,List.of(new EmitirNotaCreditoVentaInput.Linea(line,new BigDecimal("0.25"))),key);
      // Roll back this mixed-document scenario so it never consumes a real NCV number.
      tx.execute(status->{credits.emitir(creditInput);var d=service.emitir(input("50"));assertThat(balance()).isEqualByComparingTo("125");service.anular(voidInput(d.id()));assertThat(balance()).isEqualByComparingTo("75");assertThat(jdbc.queryForObject("select total from sales_invoice where id=?",BigDecimal.class,invoice)).isEqualByComparingTo("100");status.setRollbackOnly();return null;});
      assertThat(balance()).isEqualByComparingTo("100");
    }
    @Test void numeracionPropiaAtomicaSinConsumirNumeroOperativo(){
      tx.execute(status->{var real=new NotaDebitoVentaRepository(jdbc);String one=real.siguiente(),two=real.siguiente();assertThat(one).startsWith("NDV-");assertThat(Long.parseLong(two.substring(4))).isEqualTo(Long.parseLong(one.substring(4))+1);status.setRollbackOnly();return null;});
    }
    BigDecimal balance(){return query.saldo(invoice);}
    AnularNotaDebitoVentaInput voidInput(UUID id){UUID key=UUID.randomUUID();intents.add(key);return new AnularNotaDebitoVentaInput(id,0,"Error en cargo de prueba",key);}
    @Test void impuestosConfiguradosDetallesSnapshotYFacturaInmutable(){
      receivable(0);UUID tax=jdbc.queryForObject("select id from impuestos where tenant_id=? and empresa_id=? and activo and tipo='PERCENTAGE' order by porcentaje desc limit 1",UUID.class,tenant,company);
      BigDecimal rate=jdbc.queryForObject("select porcentaje from impuestos where id=?",BigDecimal.class,tax);var key=UUID.randomUUID();intents.add(key);
      var request=new EmitirNotaDebitoVentaInput(invoice,LocalDate.now(),reason,"Cargos con impuestos configurados",List.of(new EmitirNotaDebitoVentaInput.Linea("Servicio gravado",new BigDecimal("2"),new BigDecimal("50"),new BigDecimal("10"),tax),new EmitirNotaDebitoVentaInput.Linea("Cargo exento",BigDecimal.ONE,new BigDecimal("20"),BigDecimal.ZERO,null)),key);
      var d=issue(request);BigDecimal expectedTax=new BigDecimal("90").multiply(rate).divide(new BigDecimal("100"),4,java.math.RoundingMode.HALF_UP);
      assertThat(d.impuesto()).isEqualByComparingTo(expectedTax);assertThat(d.total()).isEqualByComparingTo(new BigDecimal("110").add(expectedTax));
      assertThat(jdbc.queryForObject("select sum(tax_amount) from sales_debit_note_line where sales_debit_note_id=?",BigDecimal.class,d.id())).isEqualByComparingTo(expectedTax);
      assertThat(query.documento(d.id()).lineas()).hasSize(2);
      String historical=d.cliente();jdbc.update("update sales_invoice set customer_name='Cambio de nombre de prueba' where id=?",invoice);assertThat(query.documento(d.id()).cliente()).isEqualTo(historical);
      var financial=jdbc.queryForMap("select total,subtotal,tax_total,discount_total,surcharge_total from sales_invoice where id=?",invoice);
      assertThat((BigDecimal)financial.get("total")).isEqualByComparingTo("100");assertThat((BigDecimal)financial.get("subtotal")).isEqualByComparingTo("100");assertThat((BigDecimal)financial.get("tax_total")).isZero();assertThat((BigDecimal)financial.get("discount_total")).isZero();assertThat((BigDecimal)financial.get("surcharge_total")).isZero();
    }
    @Test void dosCargosConcurrentesDiferentesSeAcumulanSinPerderSaldo()throws Exception{
      receivable(0);var requests=List.of(input("50"),input("30"));var start=new CountDownLatch(1);
      try(var pool=Executors.newFixedThreadPool(2)){var tasks=requests.stream().map(i->pool.submit(()->{auth(tenant,company,true,Set.of());try{start.await();return issue(i).id();}finally{SecurityContextHolder.clearContext();}})).toList();start.countDown();for(var task:tasks)assertThat(task.get(30,TimeUnit.SECONDS)).isNotNull();}
      assertThat(notes()).isEqualTo(2);assertThat(balance()).isEqualByComparingTo("180");
    }
    @Test void persistenciaSnapshotIdempotenciaYAnulacion(){receivable(0);var i=input("50");var d=issue(i);assertThat(issue(i).id()).isEqualTo(d.id());assertThat(notes()).isEqualTo(1);assertThat(balance()).isEqualByComparingTo("150");assertThat(query.documento(d.id()).cliente()).isEqualTo(d.cliente());assertThat(service.buscar("TEST-NC-","",null,null,0,10).getContent()).extracting(NotaDebitoVentaRepository.Resumen::id).contains(d.id());assertThat(jdbc.queryForObject("select count(*) from sales_debit_note_line where sales_debit_note_id=?",Integer.class,d.id())).isEqualTo(1);
      var v=voidInput(d.id());tx.execute(x->service.anular(v));tx.execute(x->service.anular(v));assertThat(balance()).isEqualByComparingTo("100");assertThat(query.ciclo(d.id(),false).estado()).isEqualTo("VOIDED");assertThatThrownBy(()->tx.execute(x->service.anular(voidInput(d.id())))).isInstanceOf(ReglaNegocioException.class);
      assertThat(jdbc.queryForObject("select total from sales_invoice where id=?",BigDecimal.class,invoice)).isEqualByComparingTo("100");}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void pagadaConClienteCreaOIncrementaObligacion(boolean existing){if(existing)receivable(100);var d=issue(input("50"));assertThat(balance()).isEqualByComparingTo("50");assertThat(d.saldoAnterior()).isZero();assertThat(d.saldoPosterior()).isEqualByComparingTo("50");assertThat(jdbc.queryForObject("select payment_condition from sales_invoice where id=?",String.class,invoice)).isEqualTo("Contado");tx.execute(x->service.anular(voidInput(d.id())));assertThat(balance()).isZero();if(!existing){issue(input("30"));assertThat(balance()).isEqualByComparingTo("30");}}
    @Test void selectorIncluyeFacturasConYSinClienteYPermitePrepararlas(){
      String number=jdbc.queryForObject("select internal_number from sales_invoice where id=?",String.class,invoice);
      assertThat(service.facturas(number,0,25).getContent()).extracting(NotaDebitoVentaRepository.FacturaOpcion::id).containsExactly(invoice);
      jdbc.update("update sales_invoice set customer_id=null,customer_name='',customer_identification=null where id=?",invoice);
      var found=service.facturas(number,0,25);assertThat(found.getContent()).hasSize(1);assertThat(found.getContent().getFirst().cliente()).isEqualTo("Consumidor Final");
      assertThat(service.facturas("Consumidor Final",0,25).getTotalElements()).isGreaterThanOrEqualTo(1);
      var prep=service.preparar(invoice);assertThat(prep.cliente()).isEqualTo("Consumidor Final");assertThat(prep.saldo()).isZero();assertThat(prep.estado()).isEqualTo("Pagada");
      var draft=input("50");assertThat(service.calcular(new EmitirNotaDebitoVentaInput(draft.facturaId(),draft.fecha(),draft.motivoId(),draft.descripcion(),draft.lineas(),draft.idempotencia(),com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CASH,List.of(),null,null)).cliente()).isEqualTo("Consumidor Final");assertThat(notes()).isZero();
      auth(UUID.randomUUID(),company,true,Set.of());assertThat(service.facturas(number,0,25).getContent()).isEmpty();assertThatThrownBy(()->service.preparar(invoice)).isInstanceOf(RecursoNoEncontradoException.class);
    }
    @Test void falloPdfPosteriorAlCommitNoRevierteNiDuplicaNota(){
      receivable(0);var request=input("50");var d=issue(request);assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      var reader=spy(new NotaDebitoVentaRepository(jdbc));var pdf=new NotaDebitoVentaPdfService(reader);
      doThrow(new ReglaNegocioException("Fallo PDF simulado")).when(reader).documento(d.id());
      assertThatThrownBy(()->pdf.generar(d.id())).hasMessageContaining("simulado");assertThat(query.ciclo(d.id(),false).estado()).isEqualTo("CONFIRMED");assertThat(balance()).isEqualByComparingTo("150");
      doCallRealMethod().when(reader).documento(d.id());assertThat(pdf.generar(d.id()).contenido()).isNotEmpty();assertThat(pdf.generar(d.id()).contenido()).isNotEmpty();
      assertThat(notes()).isEqualTo(1);assertThat(issue(request).id()).isEqualTo(d.id());assertThat(balance()).isEqualByComparingTo("150");
    }
    @Test void consumidorFinalNoCreaNotaNiObligacion(){jdbc.update("update sales_invoice set customer_id=null,customer_name='Consumidor Final' where id=?",invoice);assertThatThrownBy(()->issue(input("50"))).hasMessageContaining("a crédito debe existir un cliente identificado");assertThat(notes()).isZero();assertThat(balance()).isZero();}
    @Test void rollbackCompletoAnteErrorDePersistencia(){receivable(0);doAnswer(a->{a.callRealMethod();throw new ReglaNegocioException("Fallo simulado");}).when(query).insertar(any());assertThatThrownBy(()->issue(input("50"))).hasMessageContaining("simulado");assertThat(notes()).isZero();assertThat(balance()).isEqualByComparingTo("100");}
    @Test void aislamientoTenantEmpresaSucursal(){var d=issue(input("50"));auth(UUID.randomUUID(),company,true,Set.of());assertThatThrownBy(()->service.obtener(d.id())).isInstanceOf(RecursoNoEncontradoException.class);auth(tenant,UUID.randomUUID(),true,Set.of());assertThatThrownBy(()->service.obtener(d.id())).isInstanceOf(RecursoNoEncontradoException.class);auth(tenant,company,false,Set.of(UUID.randomUUID()));assertThatThrownBy(()->service.obtener(d.id())).isInstanceOf(RecursoNoEncontradoException.class);assertThatThrownBy(()->issue(input("50"))).isInstanceOf(RecursoNoEncontradoException.class);}
    @Test void cobroAplicadoImpideAnularSinSaldoSuficiente(){receivable(100);var d=issue(input("50"));jdbc.update("update customer_accounts_receivable set applied_amount=50,status='PAID' where sales_debit_note_id=?",d.id());assertThatThrownBy(()->tx.execute(x->service.anular(voidInput(d.id())))).isInstanceOf(ReglaNegocioException.class);assertThat(query.ciclo(d.id(),false).estado()).isEqualTo("CONFIRMED");}
    @Test void emisionConcurrenteMismaClaveUnaSolaNota()throws Exception{receivable(0);var i=input("50");var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var tasks=java.util.stream.IntStream.range(0,2).mapToObj(n->pool.submit(()->{auth(tenant,company,true,Set.of());try{start.await();return issue(i).id();}finally{SecurityContextHolder.clearContext();}})).toList();start.countDown();assertThat(tasks.get(0).get(30,TimeUnit.SECONDS)).isEqualTo(tasks.get(1).get(30,TimeUnit.SECONDS));}assertThat(notes()).isEqualTo(1);assertThat(balance()).isEqualByComparingTo("150");}
    @Test void anulacionConcurrenteSoloRevierteUnaVez()throws Exception{receivable(0);var d=issue(input("50"));var requests=List.of(voidInput(d.id()),voidInput(d.id()));var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var tasks=requests.stream().map(i->pool.submit(()->{auth(tenant,company,true,Set.of());try{start.await();tx.execute(x->service.anular(i));return true;}catch(ReglaNegocioException ex){return false;}finally{SecurityContextHolder.clearContext();}})).toList();start.countDown();int winners=0;for(var task:tasks)if(task.get(30,TimeUnit.SECONDS))winners++;assertThat(winners).isEqualTo(1);}assertThat(balance()).isEqualByComparingTo("100");}

    void prepararCajaPrueba(){
      cashFixture=true;
      jdbc.update("insert into usuarios select (jsonb_populate_record(null::usuarios,to_jsonb(u)||jsonb_build_object('id',?::uuid,'usuario',?,'usuario_normalizado',?,'correo',?,'email',?))).* from usuarios u where u.id=? and u.empresa_id=?",testOperator,"test-nd-"+testOperator.toString().substring(0,16),"test-nd-"+testOperator.toString().substring(0,16),testOperator+"@example.invalid",testOperator+"@example.invalid",user,company);
      jdbc.update("insert into cajas(id,tenant_id,empresa_id,sucursal_id,moneda_id,codigo,nombre,creado_por) values(?,?,?,?,?,?,?,?)",testCash,tenant,company,branch,currency,"TEST-ND-"+testCash.toString().substring(0,20),"TEST ND "+testCash,user);
      jdbc.update("insert into cash_register_session(id,tenant_id,empresa_id,cash_register_id,branch_id,currency_id,business_date,shift_number,display_code,opened_by,opening_amount) values(?,?,?,?,?,?,current_date,1,?,?,0)",testSession,tenant,company,testCash,branch,currency,"TEST-ND-"+testSession,testOperator);
    }
    UUID method(String type){return jdbc.queryForObject("select id from sales_payment_method where tenant_id=? and empresa_id=? and active and type=? order by display_order,id limit 1",UUID.class,tenant,company,type);}
    EmitirNotaDebitoVentaInput cashInput(List<AplicacionCobroInput> payments){var old=input("100");return new EmitirNotaDebitoVentaInput(old.facturaId(),old.fecha(),old.motivoId(),old.descripcion(),old.lineas(),old.idempotencia(),com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CASH,payments,testCash,testSession);}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void contadoConYSinClienteCobraUnaVezYAnulaSinDeuda(boolean identified){
      prepararCajaPrueba();if(!identified)jdbc.update("update sales_invoice set customer_id=null,customer_name='' where id=?",invoice);
      var request=cashInput(List.of(new AplicacionCobroInput(method("CASH"),new BigDecimal("100"),new BigDecimal("120"),null,null,null)));
      var d=issue(request);assertThat(issue(request).id()).isEqualTo(d.id());assertThat(d.pagado()).isEqualByComparingTo("100");assertThat(d.saldoNota()).isZero();assertThat(d.cambio()).isEqualByComparingTo("20");assertThat(balance()).isZero();assertThat(query.ciclo(d.id(),false).saldo()).isZero();
      assertThat(jdbc.queryForObject("select count(*) from sales_debit_note_payment where sales_debit_note_id=?",Integer.class,d.id())).isEqualTo(1);
      assertThat(jdbc.queryForObject("select sum(monto) from movimientos_financieros where cash_register_session_id=? and estado='REGISTERED'",BigDecimal.class,testSession)).isEqualByComparingTo("100");
      tx.execute(x->{assertThat(moneyMovements.formasPagoSesion(tenant,company,testSession)).hasSize(1);assertThat(moneyMovements.formasPagoSesion(tenant,company,testSession).getFirst().getImporte()).isEqualByComparingTo("100");var summary=CierreCajaService.resumen(moneyMovements.movimientosValidosSesion(tenant,company,testSession));assertThat(summary.totalEntradas()).isEqualByComparingTo("100");return null;});
      tx.execute(x->service.anular(voidInput(d.id())));assertThat(query.ciclo(d.id(),false).estado()).isEqualTo("VOIDED");assertThat(jdbc.queryForObject("select count(*) from movimientos_financieros where cash_register_session_id=? and estado='REGISTERED'",Integer.class,testSession)).isZero();
      assertThat(jdbc.queryForObject("select total from sales_invoice where id=?",BigDecimal.class,invoice)).isEqualByComparingTo("100");assertThat(jdbc.queryForObject("select count(*) from customer_accounts_receivable where sales_invoice_id=?",Integer.class,invoice)).isZero();
    }
    @Test void pagoMixtoConservaMetodosSinDuplicarCaja(){
      prepararCajaPrueba();UUID bank=jdbc.queryForObject("select id from cuentas_bancarias where tenant_id=? and empresa_id=? and moneda_id=? and activo order by id limit 1",UUID.class,tenant,company,currency);
      var request=cashInput(List.of(new AplicacionCobroInput(method("CASH"),new BigDecimal("60"),new BigDecimal("80"),null,null,null),new AplicacionCobroInput(method("CREDIT_CARD"),new BigDecimal("40"),null,bank,"REF-TEST",null)));
      tx.execute(status->{var d=service.emitir(request);assertThat(d.pagos()).hasSize(2);assertThat(d.cambio()).isEqualByComparingTo("20");var forms=moneyMovements.formasPagoSesion(tenant,company,testSession);assertThat(forms).hasSize(2);assertThat(forms.stream().map(FormaPagoSesionProjection::getImporte).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100");assertThat(jdbc.queryForObject("select sum(monto) from movimientos_financieros where cash_register_session_id=? and estado='REGISTERED'",BigDecimal.class,testSession)).isEqualByComparingTo("60");status.setRollbackOnly();return null;});
    }
    @Test void turnoCerradoYFalloDeCobroReviertenTodo(){
      prepararCajaPrueba();var request=cashInput(List.of(new AplicacionCobroInput(method("CASH"),new BigDecimal("100"),new BigDecimal("100"),null,null,null)));
      doThrow(new ReglaNegocioException("Fallo al guardar cobro")).when(query).insertarPago(any(),any(),any());assertThatThrownBy(()->issue(request)).hasMessageContaining("Fallo");assertThat(notes()).isZero();assertThat(jdbc.queryForObject("select count(*) from movimientos_financieros where cash_register_session_id=?",Integer.class,testSession)).isZero();
      doCallRealMethod().when(query).insertarPago(any(),any(),any());var d=issue(request);jdbc.update("update cash_register_session set status='CLOSED',closed_at=current_timestamp,closed_by=?,expected_cash_amount=100,counted_cash_amount=100,difference_amount=0 where id=?",user,testSession);assertThatThrownBy(()->tx.execute(x->service.anular(voidInput(d.id())))).isInstanceOf(ReglaNegocioException.class);assertThat(query.ciclo(d.id(),false).estado()).isEqualTo("CONFIRMED");assertThatThrownBy(()->issue(cashInput(request.pagos()))).isInstanceOf(ReglaNegocioException.class);
    }
    @Test void recibosIdentificanNotaCreditoSeparadaDeFactura(){
      jdbc.update("update sales_invoice set invoice_date=current_date-1 where id=?",invoice);receivable(0);var d=issue(input("50"));var receipts=new CobroClienteRepository(jdbc);var pending=receipts.facturas(tenant,company,customer,currency,0,50);
      assertThat(pending.getContent()).anySatisfy(row->{assertThat(row.numero()).isEqualTo(d.numero());assertThat(row.fecha()).isEqualTo(d.fecha());});
      UUID account=jdbc.queryForObject("select id from customer_accounts_receivable where sales_debit_note_id=?",UUID.class,d.id());
      tx.execute(x->{var locked=receipts.bloquearCuenta(account,tenant,company);assertThat(locked.numero()).isEqualTo(d.numero());receipts.aplicarCuenta(locked,new BigDecimal("20"));return null;});
      assertThat(query.ciclo(d.id(),false).pagado()).isEqualByComparingTo("20");assertThat(query.ciclo(d.id(),false).saldo()).isEqualByComparingTo("30");assertThatThrownBy(()->tx.execute(x->service.anular(voidInput(d.id())))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test void notasPreviasConSnapshotAntiguoConservanConsultaYReversion(){
      receivable(0);var d=issue(input("50"));jdbc.update("delete from customer_accounts_receivable where sales_debit_note_id=?",d.id());jdbc.update("update customer_accounts_receivable set original_amount=150 where sales_invoice_id=? and sales_debit_note_id is null",invoice);
      var json=tools.jackson.databind.json.JsonMapper.builder().build();@SuppressWarnings("unchecked") Map<String,Object> snapshot=json.readValue(json.writeValueAsString(d),Map.class);for(String key:List.of("condicionNota","pagado","saldoNota","recibido","cambio","pagos"))snapshot.remove(key);
      jdbc.update("update sales_debit_note set receivable_mode='LEGACY_INVOICE',document_snapshot=? where id=?",json.writeValueAsString(snapshot),d.id());
      var historical=query.documento(d.id());assertThat(historical.condicionNota()).isEqualTo(com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CREDIT);assertThat(historical.pagos()).isEmpty();tx.execute(x->service.anular(voidInput(d.id())));assertThat(balance()).isEqualByComparingTo("100");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void unReciboPuedeCobrarFacturaYNotaEnLaMismaTransaccion(boolean cerrarTurno){
      prepararCajaPrueba();receivable(0);
      tx.execute(status->{
        UUID termino=jdbc.queryForObject("select id from condiciones_pago where tenant_id=? and empresa_id=? and activo and dias>0 order by dias limit 1",UUID.class,tenant,company);
        jdbc.update("update sales_invoice set payment_condition='Crédito',payment_term_id=?,credit_days=30,due_date=current_date+30,received_amount=0,change_amount=0 where id=?",termino,invoice);
        var cartera=new CuentasCobrarConsultaRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var alcance=new CuentasCobrarConsultaRepository.Alcance(tenant,company,true,Set.of(),java.time.ZoneId.of("America/Santo_Domingo"));
        var consulta=new CuentasCobrarConsultaRepository.Consulta(alcance,
            new CarteraCobrarDto.Filtro(customer,CarteraCobrarDto.Estado.TODOS,null,null,""),LocalDate.now(alcance.zona()));
        var d=service.emitir(input("50"));UUID original=jdbc.queryForObject("select id from customer_accounts_receivable where sales_invoice_id=? and sales_debit_note_id is null",UUID.class,invoice);
        UUID noteAccount=jdbc.queryForObject("select id from customer_accounts_receivable where sales_debit_note_id=?",UUID.class,d.id());
        assertThat(cartera.cuenta(consulta,original).orElseThrow().saldo()).isEqualByComparingTo("100");
        assertThat(cartera.cuenta(consulta,noteAccount).orElseThrow().total()).isEqualByComparingTo("50");
        UUID banco=jdbc.queryForObject("select id from cuentas_bancarias where tenant_id=? and empresa_id=? and moneda_id=? and activo order by id limit 1",UUID.class,tenant,company,currency);
        var request=new CobroClienteInput(customer,currency,branch,LocalDate.now(),new BigDecimal("50"),"REF-TEST","Cobro aislado de factura y nota",testCash,testSession,UUID.randomUUID(),List.of(new CobroClienteInput.AplicacionFactura(original,invoice,new BigDecimal("20")),new CobroClienteInput.AplicacionFactura(noteAccount,invoice,new BigDecimal("30"))),List.of(new AplicacionCobroInput(method("CASH"),new BigDecimal("30"),null,null,null,null),
            new AplicacionCobroInput(method("CREDIT_CARD"),new BigDecimal("20"),null,banco,"REF-TEST",null)));
        var receipt=receiptService.confirmar(request);
        assertThat(cartera.cuenta(consulta,original).orElseThrow().saldo()).isEqualByComparingTo("80");
        assertThat(cartera.cuenta(consulta,noteAccount).orElseThrow().saldo()).isEqualByComparingTo("20");
        assertThat(cartera.aplicaciones(consulta,noteAccount)).singleElement().satisfies(a->{
            assertThat(a.documento()).isEqualTo(receipt.numero());assertThat(a.revertida()).isFalse();
        });
        assertThat(receipt.aplicado()).isEqualByComparingTo("50");assertThat(query.ciclo(d.id(),false).saldo()).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("select count(*) from customer_receipt_application where customer_receipt_id=?",Integer.class,receipt.id())).isEqualTo(2);
        assertThat(jdbc.queryForList("select invoice_number_snapshot from customer_receipt_application where customer_receipt_id=?",String.class,receipt.id())).contains(d.numero());
        var recibos=new CobroClienteRepository(jdbc);
        var historial=recibos.historial(tenant,company,true,Set.of(),d.numero(),"",null,null,false,0,10);
        assertThat(historial.getContent()).singleElement().satisfies(r->{
          assertThat(r.id()).isEqualTo(receipt.id());assertThat(r.documentos()).containsExactlyInAnyOrder(d.numero(),recibos.detalle(receipt.id(),tenant,company).aplicaciones().stream().filter(a->a.notaDebitoId()==null).findFirst().orElseThrow().factura());
        });
        var detalle=recibos.detalle(receipt.id(),tenant,company);
        assertThat(detalle.aplicaciones()).anySatisfy(a->{assertThat(a.notaDebitoId()).isEqualTo(d.id());assertThat(a.totalDocumento()).isEqualByComparingTo("50");assertThat(a.monto()).isEqualByComparingTo("30");});
        assertThat(detalle.aplicaciones()).anySatisfy(a->{assertThat(a.notaDebitoId()).isNull();assertThat(a.totalDocumento()).isEqualByComparingTo("100");assertThat(a.monto()).isEqualByComparingTo("20");});
        assertThat(recibos.historial(tenant,company,false,Set.of(UUID.randomUUID()),receipt.numero(),"",null,null,null,0,10)).isEmpty();
        assertThat(recibos.recibosAnulables(tenant,company,List.of(receipt.id()),false,Set.of(branch))).containsExactly(receipt.id());
        assertThat(recibos.recibosAnulables(tenant,company,List.of(receipt.id()),false,Set.of(UUID.randomUUID()))).isEmpty();
        assertThat(recibos.recibosAnulables(UUID.randomUUID(),company,List.of(receipt.id()),true,Set.of())).isEmpty();
        if(cerrarTurno){
          jdbc.update("update cash_register_session set status='CLOSED',closed_at=current_timestamp,closed_by=?,expected_cash_amount=30,counted_cash_amount=30,difference_amount=0 where id=?",user,testSession);
          // Simula que el cierre se observó en otra petición, sin reutilizar el turno leído durante el cobro.
          EntityManagerFactoryUtils.getTransactionalEntityManager(emf.getObject()).clear();
          var p=new TenantPrincipal(user,tenant,company,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("recibos.ver","recibos.anular"));
          SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
          var factory=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf.getObject()));
          var vista=new ReciboHistorialService(receiptService,recibos,new PaymentReceiptSnapshotRepository(jdbc),
              factory.getRepository(EmpresaRepository.class),factory.getRepository(DatosEmpresaRepository.class));
          var documento=vista.detalle(receipt.id());
          assertThat(documento.recibo().id()).isEqualTo(receipt.id());
          assertThat(documento.anulable()).isFalse();
          assertThat(vista.buscar(receipt.numero(),null,null,false,0,10).getContent())
              .singleElement().satisfies(x->assertThat(x.id()).isEqualTo(receipt.id()));
          assertThatThrownBy(()->receiptService.anular(receipt.id(),"Prueba"))
              .hasMessage("El recibo no puede anularse porque pertenece a una sesión de caja cerrada.");
          status.setRollbackOnly();return null;
        }
        assertThatThrownBy(()->receiptService.anularConResultado(receipt.id(),"   "))
            .hasMessage("El motivo de anulación es obligatorio.");
        String razon="Dijo \"error\"\n al cobrar";
        var anulado=receiptService.anularConResultado(receipt.id(),"  "+razon+"  ");
        assertThat(anulado.yaAnulado()).isFalse();
        assertThat(anulado.recibo().id()).isEqualTo(receipt.id());
        assertThat(anulado.recibo().numero()).isEqualTo(receipt.numero());
        assertThat(anulado.recibo().motivoAnulacion()).isEqualTo(razon);
        assertThat(anulado.recibo().aplicaciones()).allMatch(CobroClienteDto.Aplicacion::revertida);
        assertThat(cartera.cuenta(consulta,original).orElseThrow().saldo()).isEqualByComparingTo("100");
        assertThat(cartera.cuenta(consulta,noteAccount).orElseThrow().saldo()).isEqualByComparingTo("50");
        assertThat(cartera.aplicaciones(consulta,original)).singleElement().satisfies(a->assertThat(a.revertida()).isTrue());
        jdbc.update("update sales_invoice set invoice_date=current_date-3,confirmed_at=current_timestamp-interval '3 days' where id=? and tenant_id=? and empresa_id=?",invoice,tenant,company);
        jdbc.update("update customer_receipt set receipt_date=current_date-2,confirmed_at=current_timestamp-interval '2 days' where id=? and tenant_id=? and empresa_id=?",receipt.id(),tenant,company);
        jdbc.update("update customer_receipt_application set applied_at=current_timestamp-interval '2 days' where customer_receipt_id=? and accounts_receivable_id=? and tenant_id=? and empresa_id=?",receipt.id(),original,tenant,company);
        var facturaCartera=cartera.cuenta(consulta,original).orElseThrow();
        var corteAnterior=new CuentasCobrarConsultaRepository.Consulta(alcance,
            new CarteraCobrarDto.Filtro(customer,CarteraCobrarDto.Estado.ACTIVA,null,null,facturaCartera.documento()),
            consulta.corte().minusDays(1));
        assertThat(cartera.corteReconstruible(corteAnterior)).isTrue();
        assertThat(cartera.antiguedad(corteAnterior,true)).singleElement()
            .satisfies(edad->assertThat(edad.total()).isEqualByComparingTo("80"));
        assertThat(recibos.datosAnulacion(receipt.id(),tenant,company).fecha()).isNotNull();
        assertThat(receiptService.anularConResultado(receipt.id(),"Otro intento").yaAnulado()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from auditoria where registro_id=? and accion='CUSTOMER_RECEIPT_VOIDED'",Integer.class,receipt.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select detalle->>'motivo' from auditoria where registro_id=? and accion='CUSTOMER_RECEIPT_VOIDED'",String.class,receipt.id())).isEqualTo(razon);
        moneyMovements.flush();
        assertThat(jdbc.queryForObject("""
            select count(*) from customer_receipt_payment p join movimientos_financieros m on m.id=p.financial_movement_id
            where p.customer_receipt_id=? and m.estado='VOIDED'
            """,Integer.class,receipt.id())).isEqualTo(2);
        assertThat(recibos.recibosAnulables(tenant,company,List.of(receipt.id()),true,Set.of())).isEmpty();assertThat(query.ciclo(d.id(),false).saldo()).isEqualByComparingTo("50");service.anular(voidInput(d.id()));assertThat(balance()).isEqualByComparingTo("100");
        status.setRollbackOnly();return null;
      });
    }

    void sucursalAislada(){
      testBranch=UUID.randomUUID();
      jdbc.update("insert into sucursales select (jsonb_populate_record(null::sucursales,to_jsonb(s)||jsonb_build_object('id',?::uuid,'codigo',?,'nombre','Prueba ND automática','principal',false))).* from sucursales s where s.id=? and s.empresa_id=?",testBranch,"TEST-"+testBranch.toString().substring(0,20),branch,company);
      jdbc.update("update sales_invoice set branch_id=? where id=? and tenant_id=? and empresa_id=?",testBranch,invoice,tenant,company);branch=testBranch;
    }
    EmitirNotaDebitoVentaInput pagoAutomatico(){var i=cashInput(List.of(new AplicacionCobroInput(method("CASH"),new BigDecimal("100"),new BigDecimal("120"),null,null,null)));return new EmitirNotaDebitoVentaInput(i.facturaId(),i.fecha(),i.motivoId(),i.descripcion(),i.lineas(),i.idempotencia(),i.condicion(),i.pagos(),null,null);}
    @Test void contextoUnicoAbiertoSeDeterminaSinCajaEnLaSolicitud(){
      sucursalAislada();prepararCajaPrueba();var d=issue(pagoAutomatico());assertThat(d.pagos()).hasSize(1);assertThat(d.pagos().getFirst().cuentaId()).isEqualTo(testCash);assertThat(d.pagos().getFirst().sesionId()).isEqualTo(testSession);assertThat(d.pagos().getFirst().cambio()).isEqualByComparingTo("20");assertThat(balance()).isZero();
    }
    @Test void ausenciaDeSesionAbiertaNoEmiteNiRegistraDeuda(){
      sucursalAislada();assertThatThrownBy(()->issue(pagoAutomatico())).hasMessage("No existe una sesión de caja abierta disponible para registrar este cobro.");assertThat(notes()).isZero();assertThat(balance()).isZero();
    }

    @Test void reciboContadoConsumidorFinalCompartePagoPosSinDuplicarlo(){
      tx.execute(status->{
        jdbc.update("update sales_invoice set customer_id=null, customer_name='Consumidor Final',customer_identification=null where id=? and tenant_id=? and empresa_id=?",invoice,tenant,company);
        var factory=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf.getObject()));
        var f=factory.getRepository(FacturaVentaRepository.class).findByIdAndTenantIdAndEmpresaId(invoice,tenant,company).orElseThrow();
        var metodo=factory.getRepository(MetodoPagoVentaRepository.class).findById(method("CASH")).orElseThrow();
        var pago=factory.getRepository(PagoVentaRepository.class).saveAndFlush(new com.citacloud.springboot.contacloud.app.models.PagoVenta(f,new BigDecimal("100"),new BigDecimal("20"),user));
        var medio=factory.getRepository(AplicacionPagoVentaRepository.class).saveAndFlush(new com.citacloud.springboot.contacloud.app.models.AplicacionPagoVenta(pago,metodo,new BigDecimal("100"),new BigDecimal("120"),new BigDecimal("20"),null,null,null,1));
        var r=new CobroClienteRepository(jdbc);
        String numero="TEST-REC-"+invoice.toString().substring(0,18);
        String nombreSucursal=jdbc.queryForObject("select nombre from sucursales where id=? and tenant_id=? and empresa_id=?",String.class,branch,tenant,company);
        UUID id=r.insertarReciboVentaPos(f,nombreSucursal,numero);r.conservarImpresionVentaPos(id,tenant,company,f.getCajaId());
        var h=r.historial(tenant,company,true,Set.of(),f.getNumero(),"",f.getFecha(),f.getFecha(),false,0,10);
        assertThat(h.getContent()).singleElement().satisfies(x->{assertThat(x.id()).isEqualTo(id);assertThat(x.cliente()).isEqualTo("Consumidor Final");assertThat(x.documentos()).containsExactly(f.getNumero());});
        assertThat(r.recibosAnulables(tenant,company,List.of(id),true,Set.of())).isEmpty();
        var d=r.detalle(id,tenant,company);assertThat(d.clienteId()).isNull();assertThat(d.recibido()).isEqualByComparingTo("100");
        assertThat(d.medios()).singleElement().satisfies(x->{assertThat(x.id()).isEqualTo(medio.getId());assertThat(x.monto()).isEqualByComparingTo("100");});
        var snapshots=new PaymentReceiptSnapshotRepository(jdbc);
        var doc=snapshots.documento(snapshots.obtener(id,tenant,company));
        assertThat(doc.receiptNumber()).isEqualTo(numero);assertThat(doc.documents().getFirst().originalTotal()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select count(*) from customer_receipt_payment where customer_receipt_id=?",Integer.class,id)).isZero();
        assertThat(r.detalle(id,UUID.randomUUID(),company)).isNull();
        assertThat(r.detalle(id,tenant,UUID.randomUUID())).isNull();
        assertThatThrownBy(()->receiptService.anular(id,"Prueba")).hasMessageContaining("flujo de devolución");
        status.setRollbackOnly();return null;
      });
    }

    @Test void carteraPaginadaYAntiguedadUsanElConjuntoCompletoYRespetanAlcance(){
      tx.execute(status->{
        UUID termino=jdbc.queryForObject("select id from condiciones_pago where tenant_id=? and empresa_id=? and activo and dias>0 order by dias limit 1",UUID.class,tenant,company);
        String codigo="CXC-"+invoice.toString().substring(0,8);
        jdbc.update("""
            insert into sales_invoice
            select (jsonb_populate_record(null::sales_invoice,to_jsonb(f)||jsonb_build_object(
                'id',gen_random_uuid(),'idempotency_key',gen_random_uuid(),'internal_number',?||(case when i<=37 then '-F-' else '-O-' end)||i,
                'invoice_date',current_date-180,'confirmed_at',current_timestamp-interval '180 days',
                'payment_condition','Crédito','payment_term_id',?::uuid,'credit_days',30,
                'due_date',current_date-(case mod(i,5) when 0 then -15 when 1 then 15 when 2 then 45 when 3 then 75 else 120 end),
                'received_amount',0,'change_amount',0))).*
              from sales_invoice f cross join generate_series(1,100) i where f.id=? and f.tenant_id=? and f.empresa_id=?
            """,codigo,termino,invoice,tenant,company);
        jdbc.update("""
            insert into customer_accounts_receivable(tenant_id,empresa_id,sales_invoice_id,customer_id,currency_id,
                due_date,original_amount,applied_amount,status)
            select tenant_id,empresa_id,id,customer_id,currency_id,due_date,total,0,'PENDING'
              from sales_invoice where tenant_id=? and empresa_id=? and internal_number like ?
            """,tenant,company,codigo+"-%");
        var repo=new CuentasCobrarConsultaRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var zona=java.time.ZoneId.of("America/Santo_Domingo");
        var alcance=new CuentasCobrarConsultaRepository.Alcance(tenant,company,true,Set.of(),zona);
        LocalDate hoy=jdbc.queryForObject("select current_date",LocalDate.class);
        var filtro=new CarteraCobrarDto.Filtro(customer,CarteraCobrarDto.Estado.ACTIVA,null,null,codigo);
        var consulta=new CuentasCobrarConsultaRepository.Consulta(alcance,filtro,hoy);
        var pagina=repo.cuentas(consulta,org.springframework.data.domain.PageRequest.of(0,10));
        assertThat(pagina.getContent()).hasSize(10);
        assertThat(pagina.getTotalElements()).isEqualTo(100);
        assertThat(repo.carteraCompleta(consulta)).hasSize(100);
        var filtrada=new CuentasCobrarConsultaRepository.Consulta(alcance,
            new CarteraCobrarDto.Filtro(customer,CarteraCobrarDto.Estado.ACTIVA,null,null,codigo+"-F-"),hoy);
        assertThat(repo.cuentas(filtrada,org.springframework.data.domain.PageRequest.of(0,10)).getTotalElements()).isEqualTo(37);
        assertThat(repo.carteraCompleta(filtrada)).hasSize(37);
        assertThat(repo.indicadores(filtrada).monedas()).singleElement()
            .satisfies(m->assertThat(m.saldo()).isEqualByComparingTo("3700"));
        assertThat(repo.clientes(consulta,org.springframework.data.domain.PageRequest.of(0,10)).getContent())
            .singleElement().satisfies(c->assertThat(c.saldo()).isEqualByComparingTo("10000"));
        assertThat(repo.opciones(consulta,0,10)).singleElement().satisfies(c->assertThat(c.id()).isEqualTo(customer));
        var resumen=repo.indicadores(consulta);
        assertThat(resumen.clientes()).isEqualTo(1);
        assertThat(resumen.monedas()).singleElement().satisfies(m->{
          assertThat(m.saldo()).isEqualByComparingTo("10000");
          assertThat(m.corriente()).isEqualByComparingTo("2000");
          assertThat(m.vencido()).isEqualByComparingTo("8000");
        });
        assertThat(repo.antiguedad(consulta,false)).singleElement().satisfies(m->{
          assertThat(List.of(m.corriente(),m.hasta30(),m.hasta60(),m.hasta90(),m.mas90()))
              .allSatisfy(x->assertThat(x).isEqualByComparingTo("2000"));
          assertThat(m.total()).isEqualByComparingTo("10000");
        });
        var historica=new CuentasCobrarConsultaRepository.Consulta(alcance,filtro,hoy.minusDays(1));
        assertThat(repo.corteReconstruible(historica)).isTrue();
        assertThat(repo.antiguedad(historica,true)).singleElement().satisfies(m->assertThat(m.total()).isEqualByComparingTo("10000"));
        var ajena=new CuentasCobrarConsultaRepository.Consulta(
            new CuentasCobrarConsultaRepository.Alcance(UUID.randomUUID(),company,true,Set.of(),zona),filtro,hoy);
        assertThat(repo.carteraCompleta(ajena)).isEmpty();
        assertThat(repo.cuenta(ajena,pagina.getContent().getFirst().id())).isEmpty();
        var otraSucursal=new CuentasCobrarConsultaRepository.Consulta(
            new CuentasCobrarConsultaRepository.Alcance(tenant,company,false,Set.of(UUID.randomUUID()),zona),filtro,hoy);
        assertThat(repo.carteraCompleta(otraSucursal)).isEmpty();
        var literal=new CuentasCobrarConsultaRepository.Consulta(alcance,
            new CarteraCobrarDto.Filtro(customer,CarteraCobrarDto.Estado.ACTIVA,null,null,codigo+"-%"),hoy);
        assertThat(repo.carteraCompleta(literal)).isEmpty();
        status.setRollbackOnly();return null;
      });
    }
}
