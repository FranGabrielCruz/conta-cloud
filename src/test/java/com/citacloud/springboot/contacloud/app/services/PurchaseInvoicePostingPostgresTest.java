package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.mappers.FacturaProveedorMapper;
import com.citacloud.springboot.contacloud.app.security.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CONTACLOUD_POSTGRES_TESTS",matches="true")
class PurchaseInvoicePostingPostgresTest {
 private static PGSimpleDataSource ds;
 private static LocalContainerEntityManagerFactoryBean factory;
 private static EntityManager em;
 private static JpaTransactionManager tm;
 private static JpaRepositoryFactory repositories;
 private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),user=UUID.randomUUID(),moneda=UUID.randomUUID(),
  sucursal=UUID.randomUUID(),proveedor=UUID.randomUUID(),producto=UUID.randomUUID(),servicioProducto=UUID.randomUUID();
 private final LocalDate fecha=LocalDate.of(2026,11,21);
 private UUID condicion;
 private JdbcTemplate j;
 private ConfiguracionMotorService config;
 private PurchaseInvoiceService compras;
 private AccountingPostingService posting;
 private ContabilizacionTesoreriaService tesoreria;
 private InventarioEventoContableService eventosInventario;
 private SupplierPaymentService pagos;
 private UUID banco,almacen,cliente,facturaCliente,cuentaCobrar,metodoBanco;
 @BeforeAll static void conectar() {
  ds=new PGSimpleDataSource();ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));
  ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));ds.setPassword(System.getenv("DB_PASSWORD"));
  assertThat(new JdbcTemplate(ds).queryForObject("select current_database()",String.class)).isEqualTo("conta_cloud");
  Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
  factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
  factory.setPackagesToScan("com.citacloud.springboot.contacloud.app.models");
  factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
  factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy",
   "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
  factory.afterPropertiesSet();
  em=SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
  tm=new JpaTransactionManager(factory.getObject());tm.setDataSource(ds);
  repositories=new JpaRepositoryFactory(em);
 }
 @AfterAll static void cerrar(){if(factory!=null)factory.destroy();}
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 @Test void borradorNoContabilizaRegistroCreditoGeneraUnaObligacionYAnulacionUnaReversion() {
  ejecutar(()->{
   preparar();activar();operador();
   var borrador=compras.create(input(false,null,"DRAFT-1"));
   assertThat(contar("asiento_contable")).isZero();assertThat(contar("accounts_payable")).isZero();
   var registrada=compras.register(borrador.id(),borrador.version());
   var id=asiento(registrada.id());
   assertThat(id).isNotNull();assertThat(contar("accounts_payable")).isEqualTo(1);
   assertThat(j.queryForObject("SELECT fuente_version FROM asiento_contable WHERE id=?",Long.class,id)).isGreaterThanOrEqualTo(1);
   assertThat(j.queryForObject("SELECT fuente_trace_id FROM asiento_contable WHERE id=?",String.class,id)).isNotBlank();
   assertThat(j.queryForObject("SELECT sum(debito)-sum(credito) FROM asiento_contable_linea WHERE asiento_id=?",BigDecimal.class,id)).isEqualByComparingTo("0");
   assertThat(compras.register(registrada.id(),borrador.version()).id()).isEqualTo(registrada.id());
   assertThat(contar("asiento_contable")).isEqualTo(1);
   // Una preferencia apagada después del registro no suprime la reversión histórica.
   j.update("UPDATE configuracion_contable SET contabilizacion_automatica=false WHERE empresa_id=?",empresa);
   var anulada=compras.voidInvoice(registrada.id(),registrada.version(),"Prueba de anulación");
   assertThat(anulada.estado()).isEqualTo(EstadoFacturaProveedor.VOIDED);
   assertThat(j.queryForObject("SELECT estado FROM asiento_contable WHERE id=?",String.class,id)).isEqualTo("REVERSADO");
   assertThat(j.queryForObject("SELECT count(*) FROM asiento_contable WHERE reversa_de=?",Long.class,id)).isEqualTo(1);
   compras.voidInvoice(registrada.id(),registrada.version(),"Reintento");
   assertThat(contar("asiento_contable")).isEqualTo(2);
  });
 }
 @Test void inventarioUsaActivoYGastoUsaGastoSinExigirPermisoDeAdministrarAutomatizacion() {
  ejecutar(()->{
   preparar();activar();operador();
   var inventario=compras.createAndRegister(input(true,null,"INV-1"));
   var gasto=compras.createAndRegister(input(false,null,"GAS-1"));
   assertThat(claseDebito(asiento(inventario.id()))).isEqualTo("ACTIVO");
   assertThat(claseDebito(asiento(gasto.id()))).isEqualTo("GASTOS");
   assertThat(contar("accounts_payable")).isEqualTo(2);
   assertThat(j.queryForObject("SELECT count(*) FROM inventory_movement WHERE empresa_id=?",Long.class,empresa)).isZero();
  });
 }
 @Test void errorDePeriodoRevierteFacturaCuentaPorPagarYSecuenciaEnLaMismaTransaccion() {
  new TransactionTemplate(tm).executeWithoutResult(t->{
   preparar();activar();operador();
   j.update("UPDATE periodos_fiscales SET estado='CERRADO' WHERE empresa_id=?",empresa);
   assertThatThrownBy(()->compras.createAndRegister(input(false,null,"CERRADO-1")))
    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("período fiscal abierto");
   assertThat(t.isRollbackOnly()).isTrue();
   t.setRollbackOnly();
  });
  assertThat(contar("purchase_invoice")).isZero();assertThat(contar("accounts_payable")).isZero();
  assertThat(contar("asiento_contable")).isZero();assertThat(contar("purchase_invoice_sequence")).isZero();
 }
 @Test void sinAutomatizacionRegistraOperacionSinAsientoYAislaTenantEmpresa() {
  ejecutar(()->{
   preparar();operador();
   var f=compras.createAndRegister(input(false,null,"OFF-1"));
   assertThat(contar("asiento_contable")).isZero();assertThat(contar("accounts_payable")).isEqualTo(1);
   autenticar(UUID.randomUUID(),empresa,Set.of("facturas_proveedores.ver"));
   assertThatThrownBy(()->compras.get(f.id())).isInstanceOf(RecursoNoEncontradoException.class);
   autenticar(tenant,UUID.randomUUID(),Set.of("facturas_proveedores.ver"));
   assertThatThrownBy(()->compras.get(f.id())).isInstanceOf(RecursoNoEncontradoException.class);
  });
 }
 @Test void opcionalesVaciasNoBloqueanGastoSinImpuesto() {
  ejecutar(()->{
   preparar();activar();operador();
   j.update("DELETE FROM cuenta_contable_clave_empresa WHERE empresa_id=? AND clave IN ('INVENTORY_DEFAULT','RECOVERABLE_TAX','PURCHASE_DISCOUNTS')",empresa);
   var f=compras.createAndRegister(input(false,null,"SIN-OPCIONALES"));
   assertThat(asiento(f.id())).isNotNull();
  });
 }
 @Test void inventarioSinCuentaCondicionalRevierteTodaLaOperacion() {
  new TransactionTemplate(tm).executeWithoutResult(t->{
   preparar();activar();operador();
   j.update("DELETE FROM cuenta_contable_clave_empresa WHERE empresa_id=? AND clave='INVENTORY_DEFAULT'",empresa);
   assertThatThrownBy(()->compras.createAndRegister(input(true,null,"SIN-INVENTARIO")))
    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Inventario");
   assertThat(t.isRollbackOnly()).isTrue();
   t.setRollbackOnly();
  });
  assertThat(contar("purchase_invoice")).isZero();assertThat(contar("accounts_payable")).isZero();assertThat(contar("asiento_contable")).isZero();
 }
 @Test void impuestoSinCuentaCondicionalFallaConMensajeClaro() {
  new TransactionTemplate(tm).executeWithoutResult(t->{
   preparar();activar();operador();
   var impuesto=repo(ImpuestoRepository.class).saveAndFlush(new Impuesto(tenant,empresa,"TAX-PRUEBA","Impuesto prueba","impuesto prueba",new BigDecimal("18"),TipoImpuesto.PERCENTAGE,null,true,user));
   j.update("DELETE FROM cuenta_contable_clave_empresa WHERE empresa_id=? AND clave='RECOVERABLE_TAX'",empresa);
   assertThatThrownBy(()->compras.createAndRegister(input(false,impuesto.getId(),"SIN-IMPUESTO")))
    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Impuesto recuperable");
   assertThat(t.isRollbackOnly()).isTrue();
   t.setRollbackOnly();
  });
  assertThat(contar("purchase_invoice")).isZero();assertThat(contar("accounts_payable")).isZero();assertThat(contar("asiento_contable")).isZero();
 }
 @Test void anularEnPeriodoCerradoConservaFacturaObligacionYAsientoOriginal() {
  ejecutar(()->{
   preparar();activar();operador();var f=compras.createAndRegister(input(false,null,"ANULAR-CERRADO"));var id=asiento(f.id());
   j.update("UPDATE periodos_fiscales SET estado='CERRADO' WHERE empresa_id=?",empresa);
   assertThatThrownBy(()->compras.voidInvoice(f.id(),f.version(),"Anular prueba")).hasMessageContaining("período fiscal abierto");
   assertThat(j.queryForObject("SELECT status FROM purchase_invoice WHERE id=?",String.class,f.id())).isEqualTo("REGISTERED");
   assertThat(j.queryForObject("SELECT estado FROM asiento_contable WHERE id=?",String.class,id)).isEqualTo("CONTABILIZADO");
   assertThat(j.queryForObject("SELECT voided FROM accounts_payable WHERE purchase_invoice_id=?",Boolean.class,f.id())).isFalse();
   assertThat(j.queryForObject("SELECT count(*) FROM asiento_contable WHERE reversa_de=?",Long.class,id)).isZero();
  });
 }
 @Test void solicitudesConcurrentesNoAtraviesanElBloqueoDeRegistroYAnulacion() throws Exception {
  var tx=new TransactionTemplate(tm);
  var borrador=tx.execute(t->{preparar();activar();operador();return compras.create(input(false,null,"CONCURRENTE"));});
  var listo=new CountDownLatch(1);var liberar=new CountDownLatch(1);
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
   var primera=executor.submit(()->{
    operador();
    try {tx.executeWithoutResult(t->{
     t.setRollbackOnly();var f=compras.register(borrador.id(),borrador.version());
     var id=asiento(f.id());assertThat(compras.register(f.id(),borrador.version()).id()).isEqualTo(f.id());
     compras.voidInvoice(f.id(),f.version(),"Concurrencia de prueba");
     assertThat(j.queryForObject("SELECT count(*) FROM asiento_contable WHERE reversa_de=?",Long.class,id)).isEqualTo(1);
     listo.countDown();esperar(liberar);
    });}finally{SecurityContextHolder.clearContext();}
   });
   try {
    esperar(listo);
    var segunda=executor.submit(()->{
     operador();
     try {
      for(boolean anular:List.of(false,true))assertThatThrownBy(()->tx.executeWithoutResult(t->{
       j.execute("SET LOCAL lock_timeout = '300ms'");
       if(anular)compras.voidInvoice(borrador.id(),borrador.version(),"Solicitud simultánea");
       else compras.register(borrador.id(),borrador.version());
      })).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
     }finally{SecurityContextHolder.clearContext();}
    });
    segunda.get(15,TimeUnit.SECONDS);
   }finally{liberar.countDown();}
   primera.get(15,TimeUnit.SECONDS);operador();
   assertThat(j.queryForObject("SELECT status FROM purchase_invoice WHERE id=?",String.class,borrador.id())).isEqualTo("DRAFT");
   assertThat(contar("asiento_contable")).isZero();assertThat(contar("accounts_payable")).isZero();
  }finally {
   liberar.countDown();
   tx.executeWithoutResult(t->{
    for(String tabla:List.of("auditoria","purchase_invoice","purchase_invoice_sequence","supplier","product",
     "configuracion_contable_modulo","cuenta_contable_clave_empresa","asiento_secuencia","periodos_fiscales","cuenta_contable",
     "configuracion_contable","sucursales","usuarios","monedas","condiciones_pago","unit_of_measure",
     "inventory_adjustment_reason","sales_payment_method","sales_return_reason"))
     j.update("DELETE FROM "+tabla+" WHERE empresa_id=?",empresa);
    j.update("DELETE FROM empresas WHERE id=? AND tenant_id=?",empresa,tenant);
   });
  }
 }
 private static void esperar(CountDownLatch señal) {
  try {if(!señal.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Tiempo de prueba excedido.");}
  catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
 }
 private void ejecutar(Runnable accion) {
  new TransactionTemplate(tm).executeWithoutResult(t->{t.setRollbackOnly();accion.run();});
 }
 @Test void convierteCompraConTasaExactaDirectaOInversaYReversionConservaImportesHistoricos() {
  ejecutar(()->{
   preparar();activar();operador();var extranjera=monedaExtranjera();
   var tasa=repo(TasaCambioRepository.class).saveAndFlush(new TasaCambio(tenant,empresa,extranjera,moneda,new BigDecimal("2.5"),fecha,true,user));
   var f=compras.createAndRegister(inputMoneda(extranjera,"DIVISA-DIRECTA"));var id=asiento(f.id());
   assertThat(j.queryForObject("SELECT moneda_id FROM asiento_contable WHERE id=?",UUID.class,id)).isEqualTo(moneda);
   assertThat(j.queryForObject("SELECT fuente_moneda_id FROM asiento_contable WHERE id=?",UUID.class,id)).isEqualTo(extranjera);
   assertThat(j.queryForObject("SELECT fuente_tasa_cambio FROM asiento_contable WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("2.5");
   assertThat(j.queryForObject("SELECT sum(debito) FROM asiento_contable_linea WHERE asiento_id=?",BigDecimal.class,id)).isEqualByComparingTo("250");
   j.update("UPDATE tasas_cambio SET activo=false WHERE id=?",tasa.getId());
   repo(TasaCambioRepository.class).saveAndFlush(new TasaCambio(tenant,empresa,moneda,extranjera,new BigDecimal("0.4"),fecha,true,user));
   var segunda=compras.createAndRegister(inputMoneda(extranjera,"DIVISA-INVERSA"));
   assertThat(j.queryForObject("SELECT fuente_tasa_cambio FROM asiento_contable WHERE id=?",BigDecimal.class,asiento(segunda.id()))).isEqualByComparingTo("2.5");
   j.update("UPDATE tasas_cambio SET tasa=0.5 WHERE empresa_id=? AND activo",empresa);
   compras.voidInvoice(f.id(),f.version(),"Reversión histórica divisa");
   assertThat(j.queryForObject("SELECT sum(l.debito) FROM asiento_contable_linea l JOIN asiento_contable a ON a.id=l.asiento_id WHERE a.reversa_de=?",BigDecimal.class,id)).isEqualByComparingTo("250");
  });
 }
 @Test void compraEnDivisaSinTasaNoInventaraUnaConversionYRevierteDocumento() {
  new TransactionTemplate(tm).executeWithoutResult(t->{
   preparar();activar();operador();var extranjera=monedaExtranjera();
   assertThatThrownBy(()->compras.createAndRegister(inputMoneda(extranjera,"DIVISA-SIN-TASA"))).hasMessageContaining("tasa de cambio disponible");
   assertThat(t.isRollbackOnly()).isTrue();t.setRollbackOnly();
  });
  assertThat(contar("purchase_invoice")).isZero();assertThat(contar("accounts_payable")).isZero();assertThat(contar("asiento_contable")).isZero();
 }
 private UUID monedaExtranjera() {
  var id=UUID.randomUUID();j.update("INSERT INTO monedas(id,tenant_id,empresa_id,codigo_iso,nombre,simbolo,moneda_base) VALUES (?,?,?,'EUR','Euro','€',false)",id,tenant,empresa);return id;
 }
 private FacturaProveedorInput inputMoneda(UUID id,String numero) {
  var i=input(false,null,numero);return new FacturaProveedorInput(i.proveedorId(),i.sucursalId(),i.numeroFactura(),i.numeroFiscal(),i.fecha(),i.vencimiento(),i.condicionPagoId(),id,i.ordenCompraId(),i.referencia(),i.notas(),i.lineas(),i.version());
 }
 private void preparar() {
  j=new JdbcTemplate(ds);autenticar(tenant,empresa,Set.of("configuracion_contable.editar","configuracion_contable.automatizacion"));
  j.update("INSERT INTO empresas(id,tenant_id,codigo,nombre) VALUES (?,?,?,'Prueba compras')",empresa,tenant,"TEST-"+empresa.toString().substring(0,8));
  j.update("INSERT INTO usuarios(id,tenant_id,empresa_id,usuario,nombre,password_hash) VALUES (?,?,?,'prueba','Prueba','inutilizado')",user,tenant,empresa);
  j.update("INSERT INTO monedas(id,tenant_id,empresa_id,codigo_iso,nombre,simbolo,moneda_base) VALUES (?,?,?,'USD','Dólar','$',true)",moneda,tenant,empresa);
  j.update("INSERT INTO configuracion_contable(tenant_id,empresa_id,moneda_base_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",tenant,empresa,moneda);
  j.update("INSERT INTO sucursales(id,tenant_id,empresa_id,codigo,nombre) VALUES (?,?,?,'PRUEBA','Prueba')",sucursal,tenant,empresa);
  j.update("INSERT INTO periodos_fiscales(tenant_id,empresa_id,anio,mes,nombre,fecha_inicial,fecha_final,estado) VALUES (?,?,2026,11,'Noviembre',?,?,'ABIERTO')",tenant,empresa,fecha.withDayOfMonth(1),fecha.withDayOfMonth(30));
  condicion=UUID.randomUUID();
  j.update("INSERT INTO condiciones_pago(id,tenant_id,empresa_id,codigo,nombre,dias,tipo) VALUES (?,?,?,'CREDITO-PRUEBA','Crédito prueba',30,'CREDIT')",condicion,tenant,empresa);
  j.update("INSERT INTO supplier(id,tenant_id,empresa_id,code,trade_name,supplier_type,created_by,updated_by) VALUES (?,?,?,'PROV-1','Proveedor','NATIONAL',?,?)",proveedor,tenant,empresa,user,user);
  var unidad=UUID.randomUUID();
  j.update("INSERT INTO unit_of_measure(id,tenant_id,empresa_id,name,normalized_name,abbreviation,normalized_abbreviation) VALUES (?,?,?,'Unidad prueba','unidad prueba','ut','ut')",unidad,tenant,empresa);
  for(var id:List.of(producto,servicioProducto)) {
   boolean stock=id.equals(producto);
   j.update("INSERT INTO product(id,tenant_id,empresa_id,code,name,product_type,unit_of_measure_id,track_inventory,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
    id,tenant,empresa,stock?"PROD-1":"SERV-1",stock?"Mercancía":"Servicio",stock?"PRODUCT":"SERVICE",unidad,stock,user,user);
  }
  var n=new NamedParameterJdbcTemplate(j);var audit=new AuditoriaService(j);
  var modulos=mock(EmpresaModuloService.class);when(modulos.habilitado(anyString())).thenReturn(true);
  var recursos=new CuentaFinancieraContableService(n,new ConfiguracionMotorRepository(n),new CuentaContableRepository(n),modulos,audit);
  var registro=IntegracionesContablesTestSupport.registro(recursos);
  config=new ConfiguracionMotorService(new ConfiguracionMotorRepository(n),new CuentaContableRepository(n),mock(ConfiguracionContableService.class),audit,registro);
  var empresas=mock(EmpresaRepository.class);var emisor=mock(Empresa.class);when(emisor.getNombre()).thenReturn("Prueba");when(emisor.getZonaHoraria()).thenReturn("America/Caracas");
  when(empresas.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(emisor));
  var asientos=new AsientoContableService(new AsientoContableRepository(n),new CuentaContableRepository(n),empresas,mock(DatosEmpresaRepository.class),audit,config);
  posting=new AccountingPostingService(registro,config,asientos);
  tesoreria=new ContabilizacionTesoreriaService(posting);
  eventosInventario=new InventarioEventoContableService(config,posting,new ValoracionInventarioContableService(repo(ProductRepository.class),new AsientoContableRepository(n)));
  var cuentasDinero=new CuentaDineroResolver(repo(CashRegisterRepository.class),repo(BankAccountRepository.class));
  pagos=transaccional(new SupplierPaymentService(repo(SupplierPaymentRepository.class),repo(SupplierPaymentApplicationRepository.class),repo(SupplierPaymentDraftAllocationRepository.class),
   repo(FinancialMovementRepository.class),cuentasDinero,mock(CashRegisterSessionService.class),audit,repo(SupplierRepository.class),repo(MonedaRepository.class),
   repo(PurchaseInvoiceRepository.class),repo(AccountsPayableRepository.class),repo(SupplierCreditNoteRepository.class),new SupplierPaymentNumberService(em),
   repo(BankReconciliationMatchRepository.class),new IdempotentOperationService(j,new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),5000),tesoreria));
  var target=new PurchaseInvoiceService(repo(PurchaseInvoiceRepository.class),repo(AccountsPayableRepository.class),repo(SupplierRepository.class),
   repo(SucursalRepository.class),repo(MonedaRepository.class),repo(CondicionPagoRepository.class),repo(ImpuestoRepository.class),
   repo(ProductRepository.class),repo(PurchaseOrderRepository.class),new PurchaseOrderCalculationService(),new FacturaProveedorMapper(),
   audit,pagos,cuentasDinero,new PurchaseInvoiceNumberService(em),
   new CompraEventoContableService(repo(ProductRepository.class),config,posting));
  var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
  proxy.addAdvice(new TransactionInterceptor(tm,new AnnotationTransactionAttributeSource()));
  compras=(PurchaseInvoiceService)proxy.getProxy();
 }
 private void activar() {
  var actual=config.obtener();var gasto=j.queryForObject("SELECT id FROM cuenta_contable WHERE empresa_id=? AND clasificacion='GASTOS' AND tipo='MOVIMIENTO' AND activo LIMIT 1",UUID.class,empresa);
  var inventario=j.queryForObject("SELECT cuenta_id FROM cuenta_contable_clave_empresa WHERE empresa_id=? AND clave='INVENTORY_DEFAULT'",UUID.class,empresa);
  config.guardar(new ConfiguracionMotorDto.Entrada(actual.version(),"DEVENGADO",true,Map.of("PURCHASE_EXPENSE",gasto,"INVENTORY_DEFAULT",inventario),Map.of(Modulo.COMPRAS,true)));
 }
 private void activarSeis() {
  var actual=config.obtener();Map<String,UUID> cuentas=new HashMap<>();Map<Modulo,Boolean> flags=new EnumMap<>(Modulo.class);
  for(var m:Modulo.automatizables())flags.put(m,true);
  for(var f:FuncionesContables.TODAS)if(f.modulos().stream().anyMatch(Modulo.automatizables()::contains)) {
   if(actual.cuentas().containsKey(f.clave())&&FuncionesContables.compatible(f,actual.cuentas().get(f.clave())))continue;
   var clase=f.clasificaciones().iterator().next();
   cuentas.put(f.clave(),j.queryForObject("SELECT id FROM cuenta_contable WHERE empresa_id=? AND clasificacion=? AND tipo='MOVIMIENTO' AND activo LIMIT 1",UUID.class,empresa,clase));
  }
  var guardada=config.guardar(new ConfiguracionMotorDto.Entrada(actual.version(),"DEVENGADO",true,cuentas,flags));
  assertThat(guardada.modulos()).hasSize(6).allMatch(m->m.integrationAvailable()&&m.completo()&&m.efectivo());
  assertThat(config.obtener().modulos()).extracting(ConfiguracionMotorDto.EstadoModulo::modulo).containsExactlyElementsOf(Modulo.automatizables());
 }
 private void recursosYCliente() {
  banco=UUID.randomUUID();almacen=UUID.randomUUID();cliente=UUID.randomUUID();
  var cuenta=j.queryForObject("SELECT cuenta_id FROM cuenta_contable_clave_empresa WHERE empresa_id=? AND clave='CASH_DEFAULT'",UUID.class,empresa);
  j.update("INSERT INTO cuentas_bancarias(id,tenant_id,empresa_id,moneda_id,codigo,banco_nombre,nombre_cuenta,tipo_cuenta,numero_cuenta_cifrado,numero_cuenta_ultimos4,numero_cuenta_fingerprint,cuenta_contable_id) VALUES (?,?,?,?,'BANCO-TEST','Banco prueba','Banco prueba','CHECKING','dato-prueba','0001',?,?)",banco,tenant,empresa,moneda,UUID.randomUUID().toString(),cuenta);
  j.update("INSERT INTO warehouse(id,tenant_id,empresa_id,code,name,normalized_name,branch_id,warehouse_type,active) VALUES (?,?,?,'ALM-TEST','Almacén prueba','almacén prueba',?,'GENERAL',true)",almacen,tenant,empresa,sucursal);
  j.update("INSERT INTO customer(id,tenant_id,empresa_id,code,person_type,first_names,last_names,payment_term_id,currency_id,created_by,updated_by) VALUES (?,?,?,'CLI-TEST','FISICA','Cliente','Prueba',?,?,?,?)",cliente,tenant,empresa,condicion,moneda,user,user);
  metodoBanco=UUID.randomUUID();
  j.update("INSERT INTO sales_payment_method(id,tenant_id,empresa_id,code,name,type,active,display_order,requires_reference,requires_bank_account,allows_change,is_cash,is_electronic,is_default) VALUES (?,?,?,'BANK-TEST','Banco prueba','BANK_TRANSFER',true,1,false,true,false,false,true,false)",metodoBanco,tenant,empresa);
 }
 private CobroClienteService cobros() {
  return transaccional(new CobroClienteService(new CobroClienteRepository(j),repo(ClienteRepository.class),repo(MonedaRepository.class),repo(SucursalRepository.class),repo(MetodoPagoVentaRepository.class),
   new CuentaDineroResolver(repo(CashRegisterRepository.class),repo(BankAccountRepository.class)),mock(CashRegisterSessionService.class),repo(FinancialMovementRepository.class),repo(BankReconciliationMatchRepository.class),new AuditoriaService(j),tesoreria));
 }
 private void facturaCliente() {
  var caja=UUID.randomUUID();var sesion=UUID.randomUUID();var terminal=UUID.randomUUID();facturaCliente=UUID.randomUUID();cuentaCobrar=UUID.randomUUID();
  j.update("INSERT INTO cajas(id,tenant_id,empresa_id,sucursal_id,moneda_id,codigo,nombre) VALUES (?,?,?,?,?,'CAJA-TEST','Caja sin cuenta no utilizada')",caja,tenant,empresa,sucursal,moneda);
  j.update("INSERT INTO cash_register_session(id,tenant_id,empresa_id,cash_register_id,branch_id,currency_id,business_date,shift_number,display_code,opened_by,opening_amount) VALUES (?,?,?,?,?,?,?,1,'CAJA-TEST',?,0)",sesion,tenant,empresa,caja,sucursal,moneda,fecha,user);
  j.update("INSERT INTO pos_terminal(id,tenant_id,empresa_id,branch_id,cash_register_id,token_hash,name,created_by) VALUES (?,?,?,?,?,?,'Terminal prueba',?)",terminal,tenant,empresa,sucursal,caja,UUID.randomUUID().toString(),user);
  j.update("""
   INSERT INTO sales_invoice(id,tenant_id,empresa_id,internal_number,status,invoice_date,confirmed_at,confirmed_by,terminal_id,cash_register_id,cash_register_session_id,branch_id,warehouse_id,customer_id,customer_name,seller_name,currency_id,currency_code,currency_symbol,payment_condition,payment_term_id,credit_days,due_date,subtotal,discount_total,surcharge_total,tax_total,total,received_amount,change_amount,idempotency_key)
   VALUES (?,?,?,'FV-TEST','CONFIRMED',?,CURRENT_TIMESTAMP,?,?,?,?,?,?,?,'Cliente Prueba','Prueba',?,'USD','$','Crédito',?,30,?,100,0,0,0,100,0,0,?)
   """,facturaCliente,tenant,empresa,fecha,user,terminal,caja,sesion,sucursal,almacen,cliente,moneda,condicion,fecha.plusDays(30),UUID.randomUUID());
  j.update("INSERT INTO customer_accounts_receivable(id,tenant_id,empresa_id,sales_invoice_id,customer_id,currency_id,original_amount,applied_amount,due_date,status) VALUES (?,?,?,?,?,?,100,0,?,'PENDING')",cuentaCobrar,tenant,empresa,facturaCliente,cliente,moneda,fecha.plusDays(30));
 }
 private UUID asientoOrigen(UUID documento,String origen) {return j.queryForObject("SELECT id FROM asiento_contable WHERE tenant_id=? AND empresa_id=? AND fuente_id=? AND origen=? AND tipo='AUTOMATICO'",UUID.class,tenant,empresa,documento,origen);}
 @Test void cxpConfirmaPagoAplicadoABancoYRevierteSinDuplicarLaCompra() {
  ejecutar(()->{
   preparar();activarSeis();recursosYCliente();operador();var compra=compras.createAndRegister(input(false,null,"COMPRA-PAGADA"));
   var i=new PagoProveedorInput(proveedor,fecha,moneda,new BigDecimal("120"),MedioPagoMovimiento.BANK_TRANSFER,TipoCuentaDinero.BANK_ACCOUNT,banco,null,null,null,
    List.of(new PagoProveedorInput.Aplicacion(compra.id(),new BigDecimal("100"))),null,UUID.randomUUID());
   var d=pagos.createAndConfirm(i);var id=asientoOrigen(d.id(),"CXP");
   assertThat(j.queryForObject("SELECT sum(debito) FROM asiento_contable_linea WHERE asiento_id=?",BigDecimal.class,id)).isEqualByComparingTo("120");
   assertThat(pagos.confirm(d.id(),d.version()).id()).isEqualTo(d.id());assertThat(contar("asiento_contable")).isEqualTo(2);
   var otra=compras.createAndRegister(input(false,null,"COMPRA-ANTICIPO"));
   var aplicada=pagos.apply(d.id(),new AplicarPagoProveedorInput(List.of(new PagoProveedorInput.Aplicacion(otra.id(),new BigDecimal("20"))),d.version()));
   assertThat(j.queryForObject("SELECT count(*) FROM asiento_contable WHERE empresa_id=? AND origen='CXP' AND tipo='AUTOMATICO'",Long.class,empresa)).isEqualTo(2);
   pagos.voidPayment(d.id(),aplicada.version(),"Anulación pago prueba");
   assertThat(j.queryForObject("SELECT estado FROM asiento_contable WHERE id=?",String.class,id)).isEqualTo("REVERSADO");
   assertThat(j.queryForObject("SELECT applied_amount FROM accounts_payable WHERE purchase_invoice_id=?",BigDecimal.class,compra.id())).isEqualByComparingTo("0");
   assertThat(j.queryForObject("SELECT applied_amount FROM accounts_payable WHERE purchase_invoice_id=?",BigDecimal.class,otra.id())).isEqualByComparingTo("0");
  });
 }
 @Test void cxcConfirmaCobroAplicadoYAnticipoConBancoSinBloquearCajaNoUtilizada() {
  ejecutar(()->{
   preparar();activarSeis();recursosYCliente();facturaCliente();operador();var servicio=cobros();
   var input=new CobroClienteInput(cliente,moneda,sucursal,fecha,new BigDecimal("120"),null,null,null,null,UUID.randomUUID(),
    List.of(new CobroClienteInput.AplicacionFactura(cuentaCobrar,facturaCliente,new BigDecimal("100"))),
    List.of(new AplicacionCobroInput(metodoBanco,new BigDecimal("120"),new BigDecimal("120"),banco,null,null)));
   var d=servicio.confirmar(input);var id=asientoOrigen(d.id(),"CXC");
   assertThat(j.queryForObject("SELECT count(*) FROM asiento_contable_linea WHERE asiento_id=?",Long.class,id)).isEqualTo(3);
   assertThat(servicio.confirmar(input).id()).isEqualTo(d.id());assertThat(contar("asiento_contable")).isEqualTo(1);
   var otraFactura=UUID.randomUUID();var otraCuenta=UUID.randomUUID();
   j.update("INSERT INTO sales_invoice SELECT (jsonb_populate_record(NULL::sales_invoice,to_jsonb(f)||jsonb_build_object('id',?::uuid,'internal_number','FV-TEST-2','idempotency_key',?::uuid))).* FROM sales_invoice f WHERE f.tenant_id=? AND f.empresa_id=? AND f.id=?",otraFactura,UUID.randomUUID(),tenant,empresa,facturaCliente);
   j.update("INSERT INTO customer_accounts_receivable(id,tenant_id,empresa_id,sales_invoice_id,customer_id,currency_id,original_amount,applied_amount,due_date,status) VALUES (?,?,?,?,?,?,100,0,?,'PENDING')",otraCuenta,tenant,empresa,otraFactura,cliente,moneda,fecha.plusDays(30));
   servicio.aplicarDisponible(d.id(),new AplicarSaldoClienteInput(List.of(new CobroClienteInput.AplicacionFactura(otraCuenta,otraFactura,new BigDecimal("20")))));
   assertThat(contar("asiento_contable")).isEqualTo(2);
   servicio.anular(d.id(),"Anulación cobro prueba");
   assertThat(j.queryForObject("SELECT estado FROM asiento_contable WHERE id=?",String.class,id)).isEqualTo("REVERSADO");
   assertThat(j.queryForObject("SELECT applied_amount FROM customer_accounts_receivable WHERE id=?",BigDecimal.class,cuentaCobrar)).isEqualByComparingTo("0");
   assertThat(j.queryForObject("SELECT applied_amount FROM customer_accounts_receivable WHERE id=?",BigDecimal.class,otraCuenta)).isEqualByComparingTo("0");
   assertThat(j.queryForObject("SELECT count(*) FROM asiento_contable WHERE empresa_id=? AND tipo='REVERSION'",Long.class,empresa)).isEqualTo(2);
  });
 }
 @Test void inventarioConfirmaAjustePropioYRevierteValorHistoricoAunqueCambieCosto() {
  ejecutar(()->{
   preparar();activarSeis();recursosYCliente();operador();j.update("UPDATE product SET purchase_cost=10,currency_id=? WHERE id=?",moneda,producto);
   var motivo=UUID.randomUUID();j.update("INSERT INTO inventory_adjustment_reason(id,tenant_id,empresa_id,code,name,allowed_direction) VALUES (?,?,?,'RAZON-TEST','Prueba','BOTH')",motivo,tenant,empresa);
   var movimientos=repo(InventoryMovementRepository.class);
   var servicio=transaccional(new AjusteInventarioService(repo(AjusteInventarioRepository.class),repo(LineaAjusteInventarioRepository.class),repo(MotivoAjusteInventarioRepository.class),repo(AlmacenRepository.class),repo(ProductRepository.class),movimientos,
    new InventoryMovementService(movimientos,new InventoryMovementNumberService(j)),new AjusteInventarioNumeroService(j),new AuditoriaService(j),mock(IdempotentOperationService.class),eventosInventario));
   var creado=servicio.crear(new AjusteInventarioInput(fecha,almacen,motivo,null,null,null,List.of(new AjusteInventarioInput.Linea(producto,null,new BigDecimal("2")))));
   servicio.confirmar(creado.id());var id=asientoOrigen(creado.id(),"INVENTARIO");
   assertThat(j.queryForObject("SELECT sum(debito) FROM asiento_contable_linea WHERE asiento_id=?",BigDecimal.class,id)).isEqualByComparingTo("20");
   assertThat(j.queryForObject("SELECT fuente_datos->'valoraciones'->0->>'costoUnitario' FROM asiento_contable WHERE id=?",String.class,id)).isNotBlank();
   servicio.confirmar(creado.id());assertThat(contar("asiento_contable")).isEqualTo(1);
   j.update("UPDATE product SET purchase_cost=100 WHERE id=?",producto);
   servicio.revertir(creado.id(),"Prueba histórica");
   assertThat(j.queryForObject("SELECT estado FROM asiento_contable WHERE id=?",String.class,id)).isEqualTo("REVERSADO");
   assertThat(j.queryForObject("SELECT sum(l.debito) FROM asiento_contable_linea l JOIN asiento_contable a ON a.id=l.asiento_id WHERE a.reversa_de=?",BigDecimal.class,id)).isEqualByComparingTo("20");
  });
 }
 @Test void facturaAlContadoTieneUnReconocimientoEnComprasYUnaLiquidacionEnCxP() {
  ejecutar(()->{
   preparar();activarSeis();recursosYCliente();operador();var contado=UUID.randomUUID();
   j.update("INSERT INTO condiciones_pago(id,tenant_id,empresa_id,codigo,nombre,dias,tipo) VALUES (?,?,?,'CASH-TEST','Contado prueba',0,'CASH')",contado,tenant,empresa);
   var i=input(false,null,"CONTADO-TEST");
   var entrada=new FacturaProveedorInput(i.proveedorId(),i.sucursalId(),i.numeroFactura(),null,fecha,fecha,contado,moneda,null,null,null,i.lineas(),null);
   var f=compras.createAndRegister(entrada,new PagoFacturaProveedorInput(TipoCuentaDinero.BANK_ACCOUNT,banco,MedioPagoMovimiento.BANK_TRANSFER,null,UUID.randomUUID()));
   assertThat(contar("asiento_contable")).isEqualTo(2);
   assertThat(j.queryForList("SELECT origen FROM asiento_contable WHERE empresa_id=? AND tipo='AUTOMATICO' ORDER BY origen",String.class,empresa)).containsExactly("COMPRAS","CXP");
   var ap=j.queryForObject("SELECT cuenta_id FROM cuenta_contable_clave_empresa WHERE empresa_id=? AND clave='ACCOUNTS_PAYABLE_DEFAULT'",UUID.class,empresa);
   assertThat(j.queryForObject("SELECT sum(l.debito-l.credito) FROM asiento_contable_linea l WHERE l.empresa_id=? AND l.cuenta_id=?",BigDecimal.class,empresa,ap)).isEqualByComparingTo("0");
   compras.voidInvoice(f.id(),f.version(),"Anular contado prueba");
   assertThat(j.queryForObject("SELECT count(*) FROM asiento_contable WHERE empresa_id=? AND estado='REVERSADO'",Long.class,empresa)).isEqualTo(2);
  });
 }
 private FacturaProveedorInput input(boolean stock,UUID impuesto,String numero) {
  return new FacturaProveedorInput(proveedor,sucursal,numero,null,fecha,fecha.plusDays(30),condicion,moneda,null,null,null,
   List.of(new LineaFacturaProveedorInput(stock?producto:servicioProducto,"Compra prueba",BigDecimal.ONE,new BigDecimal("100"),BigDecimal.ZERO,impuesto)),null);
 }
 private UUID asiento(UUID factura) { return j.queryForObject("SELECT id FROM asiento_contable WHERE tenant_id=? AND empresa_id=? AND fuente_id=? AND fuente_evento='REGISTRO'",UUID.class,tenant,empresa,factura); }
 private String claseDebito(UUID asiento) { return j.queryForObject("SELECT c.clasificacion FROM asiento_contable_linea l JOIN cuenta_contable c ON (c.tenant_id,c.empresa_id,c.id)=(l.tenant_id,l.empresa_id,l.cuenta_id) WHERE l.asiento_id=? AND l.debito>0 LIMIT 1",String.class,asiento); }
 private long contar(String tabla) { return j.queryForObject("SELECT count(*) FROM "+tabla+" WHERE empresa_id=?",Long.class,empresa); }
 private static <T>T repo(Class<T> tipo){return repositories.getRepository(tipo);}
 @SuppressWarnings("unchecked") private static <T>T transaccional(T target) {
  var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(tm,new AnnotationTransactionAttributeSource()));return (T)proxy.getProxy();
 }
 private void operador(){autenticar(tenant,empresa,Set.of("facturas_proveedores.crear","facturas_proveedores.registrar","facturas_proveedores.anular","facturas_proveedores.ver"));}
 private void autenticar(UUID t,UUID e,Set<String> permisos) {
  var p=new TenantPrincipal(user,t,e,null,"TEST","Prueba","operador","",true,true,Set.of(),permisos);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
 }
}

