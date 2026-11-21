package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.*;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="CONTACLOUD_POSTGRES_TESTS",matches="true")
class ConfiguracionMotorPostgresTest {
 private static PGSimpleDataSource ds;
 private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),user=UUID.randomUUID(),moneda=UUID.randomUUID();
 private final LocalDate fecha=LocalDate.of(2026,11,15);
 @BeforeAll static void conectar() {
  ds=new PGSimpleDataSource();ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));
  ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));ds.setPassword(System.getenv("DB_PASSWORD"));
  assertThat(new JdbcTemplate(ds).queryForObject("select current_database()",String.class)).isEqualTo("conta_cloud");
  Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
 }
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 @Test void configuraPorUuidPreservaPreferenciasYAislaEmpresas() {
  ejecutar(j->{
   preparar(j);var s=servicio(j);var actual=s.obtener();
   var ar=actual.cuentas().get("ACCOUNTS_RECEIVABLE_DEFAULT");
   var e=new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(actual.version(),"DEVENGADO",true,Map.of(),Map.of(Modulo.VENTAS,true));
   var on=s.guardar(e);assertThat(ventas(on).efectivo()).isTrue();
   var off=s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(on.version(),"DEVENGADO",false,Map.of(),Map.of()));
   assertThat(ventas(off).automatico()).isTrue();assertThat(ventas(off).efectivo()).isFalse();
   var re=s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(off.version(),"DEVENGADO",true,Map.of(),Map.of()));
   assertThat(ventas(re).efectivo()).isTrue();
   assertThatThrownBy(()->s.guardar(e)).hasMessageContaining("cambió");
   assertThatThrownBy(()->s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(re.version(),"DEVENGADO",true,Map.of(),Map.of(Modulo.ACTIVOS_FIJOS,true))))
    .hasMessageContaining("Depreciación acumulada");
   autenticar(UUID.randomUUID(),empresa);
   assertThat(s.buscar("ACCOUNTS_RECEIVABLE_DEFAULT","",0,30)).isEmpty();
   assertThatThrownBy(()->s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(0,"DEVENGADO",false,Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",ar.id()),Map.of())))
    .isInstanceOf(RuntimeException.class);
   autenticar(tenant,UUID.randomUUID());
   assertThat(s.buscar("ACCOUNTS_RECEIVABLE_DEFAULT","",0,30)).isEmpty();
  });
 }
 @Test void asientoAutomaticoIdempotenteRespetaMaestroHistoriaYPeriodo() {
  ejecutar(j->{
   preparar(j);var s=servicio(j);var e=s.obtener();
   s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(e.version(),"DEVENGADO",true,Map.of(),Map.of(Modulo.VENTAS,true)));
   var a=asientos(j,s);var fuente=new Fuente(Origen.VENTAS,"FACTURA",UUID.randomUUID(),"FAC-01","EMISION");
   var lineas=List.of(new LineaConfigurada("ACCOUNTS_RECEIVABLE_DEFAULT",null,BigDecimal.TEN,BigDecimal.ZERO),
    new LineaConfigurada("SALES_REVENUE_DEFAULT",null,BigDecimal.ZERO,BigDecimal.TEN));
   var id=a.registrarAutomatico(fuente,fecha,moneda,"Venta",lineas);
   assertThat(a.registrarAutomatico(fuente,fecha,moneda,"Venta",lineas)).isEqualTo(id);
   assertThat(j.queryForObject("SELECT count(*) FROM asiento_movimiento_contable WHERE asiento_id=?",Long.class,id)).isEqualTo(2);
   UUID anterior=j.queryForObject("SELECT cuenta_id FROM asiento_contable_linea WHERE asiento_id=? AND orden=2",UUID.class,id);
   var otra=j.queryForObject("SELECT id FROM cuenta_contable WHERE empresa_id=? AND tipo='MOVIMIENTO' AND clasificacion='INGRESOS' AND id<>? LIMIT 1",UUID.class,empresa,anterior);
   var c=s.obtener();
   s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(c.version(),"DEVENGADO",true,Map.of("SALES_REVENUE_DEFAULT",otra),Map.of()));
   var segundo=a.registrarAutomatico(new Fuente(Origen.VENTAS,"FACTURA",UUID.randomUUID(),"FAC-02","EMISION"),fecha,moneda,"Otra venta",lineas);
   assertThat(j.queryForObject("SELECT cuenta_id FROM asiento_contable_linea WHERE asiento_id=? AND orden=2",UUID.class,segundo)).isEqualTo(otra);
   assertThat(j.queryForObject("SELECT cuenta_id FROM asiento_contable_linea WHERE asiento_id=? AND orden=2",UUID.class,id)).isEqualTo(anterior);
   j.update("UPDATE periodos_fiscales SET estado='CERRADO' WHERE empresa_id=?",empresa);
   assertThatThrownBy(()->a.registrarAutomatico(new Fuente(Origen.VENTAS,"FACTURA",UUID.randomUUID(),"FAC-03","EMISION"),fecha,moneda,"Cerrado",lineas))
    .hasMessageContaining("período fiscal abierto");
   c=s.obtener();s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(c.version(),"DEVENGADO",false,Map.of(),Map.of()));
   assertThat(a.registrarAutomatico(new Fuente(Origen.VENTAS,"FACTURA",UUID.randomUUID(),"FAC-04","EMISION"),fecha,moneda,"Manual",lineas)).isNull();
   assertThat(j.queryForObject("SELECT count(*) FROM asiento_contable WHERE empresa_id=?",Long.class,empresa)).isEqualTo(2);
  });
 }
 @Test void lookupFiltraFuncionCuentaAjenaNoSeGuardaYAuditaCambios() {
  ejecutar(j->{
   preparar(j);var s=servicio(j);var e=s.obtener();
   assertThat(s.buscar("FISCAL_RESULT_DEFAULT","",0,50)).allMatch(c->c.resultado()&&c.tipo().equals("MOVIMIENTO"));
   assertThat(s.buscar("SALES_REVENUE_DEFAULT","' OR 1=1 --",0,50)).isEmpty();
   assertThatThrownBy(()->s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(e.version(),"DEVENGADO",false,Map.of("SALES_REVENUE_DEFAULT",UUID.randomUUID()),Map.of()))).hasMessageContaining("no está disponible");
   var ar=e.cuentas().get("ACCOUNTS_RECEIVABLE_DEFAULT");
   j.update("UPDATE cuenta_contable SET activo=false WHERE id=?",ar.id());
   assertThatThrownBy(()->s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(e.version(),"DEVENGADO",false,Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",ar.id()),Map.of()))).hasMessageContaining("compatible");
  });
 }
 @Test void finanzasUsaRecursosExplicitamenteConfiguradosYRevierteHistoricoConMaestroApagado() {
  ejecutar(j->{
   preparar(j);var s=servicio(j);var n=new NamedParameterJdbcTemplate(j);
   var modulo=mock(EmpresaModuloService.class);when(modulo.habilitado("CAJA_BANCOS")).thenReturn(true);
   var recursos=new CuentaFinancieraContableService(n,new ConfiguracionMotorRepository(n),new CuentaContableRepository(n),modulo,new AuditoriaService(j));
   var sucursal=UUID.randomUUID();var origen=UUID.randomUUID();var destino=UUID.randomUUID();var noUsada=UUID.randomUUID();
   j.update("INSERT INTO sucursales(id,tenant_id,empresa_id,codigo,nombre) VALUES (?,?,?,'PRUEBA','Prueba')",sucursal,tenant,empresa);
   for(var id:List.of(origen,destino,noUsada))j.update(
    "INSERT INTO cajas(id,tenant_id,empresa_id,sucursal_id,moneda_id,codigo,nombre) VALUES (?,?,?,?,?,?,?)",
    id,tenant,empresa,sucursal,moneda,id.toString(),"Caja "+id);
   var cuentas=s.obtener().cuentas();var caja=cuentas.get("CASH_DEFAULT").id();var banco=cuentas.get("BANK_DEFAULT").id();
   var cash=com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero.CASH_REGISTER;
   assertThat(s.obtener().modulos()).noneMatch(m->m.modulo()==Modulo.FINANZAS);
   recursos.guardar(cash,origen,null,caja);recursos.guardar(cash,destino,null,banco);
   // Las operaciones usan la asociación física del recurso, aunque no existan defaults globales.
   j.update("DELETE FROM cuenta_contable_clave_empresa WHERE empresa_id=? AND clave IN ('CASH_DEFAULT','BANK_DEFAULT')",empresa);
   assertThat(recursos.obtener(cash,origen).id()).isEqualTo(caja);
   assertThat(s.obtener().modulos()).noneMatch(m->m.modulo()==Modulo.FINANZAS);
   assertThatThrownBy(()->recursos.paraOperacion(cash,noUsada)).hasMessageContaining("Configura una cuenta");
   assertThatThrownBy(()->recursos.guardar(cash,origen,null,banco)).hasMessageContaining("cambió");
   assertThatThrownBy(()->recursos.guardar(cash,origen,caja,UUID.randomUUID())).hasMessageContaining("esta empresa");
   var estado=s.obtener();
   s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(estado.version(),"DEVENGADO",true,Map.of(),Map.of(Modulo.VENTAS,true)));
   var motor=asientos(j,s);var adaptador=new ContabilizacionOperativaService(s,new AccountingPostingService(IntegracionesContablesTestSupport.registro(recursos),s,motor),mock(MonedaRepository.class));
   var t=mock(com.citacloud.springboot.contacloud.app.models.TransferenciaFinanciera.class);
   var documento=UUID.randomUUID();when(t.getId()).thenReturn(documento);when(t.getFecha()).thenReturn(fecha);
   when(t.getTipoCuentaOrigen()).thenReturn(cash);when(t.getTipoCuentaDestino()).thenReturn(cash);
   when(t.getCajaOrigenId()).thenReturn(origen);when(t.getCajaDestinoId()).thenReturn(destino);
   when(t.getMonedaOrigenId()).thenReturn(moneda);when(t.getMonedaDestinoId()).thenReturn(moneda);
   when(t.getMontoOrigen()).thenReturn(BigDecimal.TEN);when(t.getReferencia()).thenReturn("TR-PRUEBA");
   var id=adaptador.transferencia(t);
   assertThat(adaptador.transferencia(t)).isEqualTo(id);
   assertThat(motor.detalle(id).lineas()).extracting(l->l.cuenta().id()).containsExactly(banco,caja);
   estado=s.obtener();s.guardar(new com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Entrada(estado.version(),"DEVENGADO",false,Map.of(),Map.of()));
   recursos.guardar(cash,origen,caja,banco);
   var reversa=adaptador.anularTransferencia(t,"Anulación de prueba");
   assertThat(adaptador.anularTransferencia(t,"Reintento")).isEqualTo(reversa);
   assertThat(motor.detalle(reversa).lineas()).extracting(l->l.cuenta().id()).containsExactly(banco,caja);
   assertThat(motor.detalle(reversa).lineas().getFirst().credito()).isEqualByComparingTo("10");
   autenticar(UUID.randomUUID(),empresa);
   assertThatThrownBy(()->recursos.obtener(cash,origen)).isInstanceOf(RecursoNoEncontradoException.class);
   autenticar(tenant,UUID.randomUUID());
   assertThatThrownBy(()->recursos.obtener(cash,origen)).isInstanceOf(RecursoNoEncontradoException.class);
   var sinPermiso=new TenantPrincipal(user,tenant,empresa,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("cajas.ver","cajas.editar"));
   SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(sinPermiso,null,sinPermiso.getAuthorities()));
   assertThatThrownBy(()->recursos.guardar(cash,origen,banco,caja)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  });
 }
 private EstadoModulo ventas(com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Estado s){return s.modulos().stream().filter(m->m.modulo()==Modulo.VENTAS).findFirst().orElseThrow();}
 private void ejecutar(java.util.function.Consumer<JdbcTemplate> trabajo) {
  var j=new JdbcTemplate(ds);new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{tx.setRollbackOnly();trabajo.accept(j);});
 }
 private void preparar(JdbcTemplate j) {
  j.update("INSERT INTO empresas(id,tenant_id,codigo,nombre) VALUES (?,?,?,'Prueba configuración')",empresa,tenant,"TEST-"+empresa.toString().substring(0,8));
  j.update("INSERT INTO usuarios(id,tenant_id,empresa_id,usuario,nombre,password_hash) VALUES (?,?,?,'prueba','Prueba','inutilizado')",user,tenant,empresa);
  j.update("INSERT INTO monedas(id,tenant_id,empresa_id,codigo_iso,nombre,simbolo,moneda_base) VALUES (?,?,?,'USD','Dólar','$',true)",moneda,tenant,empresa);
  j.update("INSERT INTO configuracion_contable(tenant_id,empresa_id,moneda_base_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",tenant,empresa,moneda);
  j.update("INSERT INTO periodos_fiscales(tenant_id,empresa_id,anio,mes,nombre,fecha_inicial,fecha_final,estado) VALUES (?,?,2026,11,'Noviembre',?,?,'ABIERTO')",tenant,empresa,fecha.withDayOfMonth(1),fecha.withDayOfMonth(30));
  autenticar(tenant,empresa);
 }
 private ConfiguracionMotorService servicio(JdbcTemplate j) {
  var n=new NamedParameterJdbcTemplate(j);
  return new ConfiguracionMotorService(new ConfiguracionMotorRepository(n),new CuentaContableRepository(n),
   mock(ConfiguracionContableService.class),new AuditoriaService(j),IntegracionesContablesTestSupport.registro(mock(CuentaFinancieraContableService.class)));
 }
 private AsientoContableService asientos(JdbcTemplate j,ConfiguracionMotorService s) {
  var n=new NamedParameterJdbcTemplate(j);var empresas=mock(EmpresaRepository.class);var emisor=mock(Empresa.class);
  when(emisor.getNombre()).thenReturn("Prueba");when(emisor.getZonaHoraria()).thenReturn("America/Caracas");
  when(empresas.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(emisor));
  return new AsientoContableService(new AsientoContableRepository(n),new CuentaContableRepository(n),empresas,mock(DatosEmpresaRepository.class),new AuditoriaService(j),s);
 }
 private void autenticar(UUID t,UUID e) {
  var p=new TenantPrincipal(user,t,e,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("configuracion_contable.editar","configuracion_contable.automatizacion","cajas.ver","cajas.editar","cuentas_bancarias.ver","cuentas_bancarias.editar"));
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
 }
}

