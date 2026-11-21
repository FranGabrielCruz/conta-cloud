package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfiguracionMotorServiceTest {
 private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),user=UUID.randomUUID();
 private final ConfiguracionMotorRepository repo=mock(ConfiguracionMotorRepository.class);
 private final CuentaContableRepository catalogo=mock(CuentaContableRepository.class);
 private final AuditoriaService auditoria=mock(AuditoriaService.class);
 private ConfiguracionMotorService servicio;
 @BeforeEach void preparar() {
  autenticar(Set.of("configuracion_contable.editar","configuracion_contable.automatizacion"));
  when(repo.general(any())).thenReturn(new ConfiguracionMotorRepository.General(3,"DEVENGADO",false));
  when(repo.cuentas(any())).thenReturn(Map.of());
  when(repo.existe(any(),eq("cuenta_contable"))).thenReturn(true);
  when(repo.modulos(any())).thenAnswer(i->new EnumMap<Modulo,Boolean>(Modulo.class));
  servicio=new ConfiguracionMotorService(repo,catalogo,mock(ConfiguracionContableService.class),auditoria,IntegracionesContablesTestSupport.registro(mock(CuentaFinancieraContableService.class)));
 }
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 @Test void sinConfiguracionPreviaNoActivaContabilidadNiInterrumpeOperacionManual() {
  when(repo.tieneConfiguracion(any())).thenReturn(false);
  assertThat(servicio.efectivo(Modulo.FINANZAS)).isFalse();
  verify(repo,never()).general(any());
 }
 @Test void funcionesCompartidasNoSeDuplicanYMaestroConservaPreferencias() {
  var ar=cuenta("ACTIVO",true,"MOVIMIENTO",false);
  var sales=cuenta("INGRESOS",true,"MOVIMIENTO",false);
  var cuentas=Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",ar,"SALES_REVENUE_DEFAULT",sales);
  var on=FuncionesContables.estados(cuentas,Map.of(Modulo.VENTAS,true),true);
  assertThat(on).filteredOn(x->x.modulo()==Modulo.VENTAS).singleElement().satisfies(x->assertThat(x.efectivo()).isTrue());
  assertThat(FuncionesContables.estados(cuentas,Map.of(Modulo.VENTAS,true),false)).allMatch(x->!x.efectivo());
  assertThat(FuncionesContables.TODAS).filteredOn(x->x.clave().equals("ACCOUNTS_RECEIVABLE_DEFAULT")).hasSize(1);
  assertThat(on).filteredOn(x->x.modulo()==Modulo.CXC).singleElement().satisfies(x->assertThat(x.completo()).isTrue());
 }
 @Test void rechazaIncompletoYNoModificaOtrosModulos() {
  assertThatThrownBy(()->servicio.guardar(entrada(Map.of(),Map.of(Modulo.ACTIVOS_FIJOS,true),true)))
   .hasMessageContaining("Activos fijos");
  verify(repo,never()).guardar(any(),any());
 }
 @Test void sinCatalogoNoPermiteActivarNiFinanzasSinCuentasGlobales() {
  when(repo.existe(any(),eq("cuenta_contable"))).thenReturn(false);
  assertThatThrownBy(()->servicio.guardar(entrada(Map.of(),Map.of(Modulo.VENTAS,true),true)))
   .hasMessageContaining("Catálogo de cuentas");
  verify(repo,never()).guardar(any(),any());
 }
 @Test void maestroRequiereModuloSeleccionadoPeroModuloNoRequiereMaestro() {
  assertThatThrownBy(()->servicio.guardar(entrada(Map.of(),Map.of(),true)))
   .hasMessageContaining("Configura al menos un módulo");
  var ar=cuenta("ACTIVO",true,"MOVIMIENTO",false);var ingreso=cuenta("INGRESOS",true,"MOVIMIENTO",false);
  when(repo.cuentas(any())).thenReturn(Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",ar,"SALES_REVENUE_DEFAULT",ingreso));
  var evaluada=servicio.evaluar(entrada(Map.of(),Map.of(Modulo.VENTAS,true),false));
  assertThat(evaluada.puedeActivarMaestro()).isTrue();
  assertThat(evaluada.modulos()).filteredOn(x->x.modulo()==Modulo.VENTAS).singleElement()
   .satisfies(x->{assertThat(x.automatico()).isTrue();assertThat(x.efectivo()).isFalse();});
  servicio.guardar(entrada(Map.of(),Map.of(Modulo.VENTAS,true),false));
  verify(repo).guardar(any(),any());
 }
 @Test void evaluacionNoPersisteNiAceptaCuentasAjenas() {
  assertThatThrownBy(()->servicio.evaluar(entrada(Map.of("SALES_REVENUE_DEFAULT",UUID.randomUUID()),Map.of(),false)))
   .hasMessageContaining("no está disponible");
  verify(repo,never()).guardar(any(),any());
 }
 @Test void cuentasCompletasNoSimulanUnaIntegracionOperativaTodaviaAusente() {
  when(repo.cuentas(any())).thenReturn(Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",cuenta("ACTIVO",true,"MOVIMIENTO",false)));
  servicio=new ConfiguracionMotorService(repo,catalogo,mock(ConfiguracionContableService.class),auditoria,new RegistroIntegracionesContables(List.of()));
  var resultado=servicio.evaluar(entrada(Map.of(),Map.of(Modulo.CXC,true),false));
  assertThat(resultado.modulos()).filteredOn(m->m.modulo()==Modulo.CXC).singleElement().satisfies(m->{
   assertThat(m.estado()).isEqualTo(EstadoConfiguracion.UNAVAILABLE);assertThat(m.efectivo()).isFalse();
   assertThat(m.motivo()).contains("integración");
   assertThat(m.completo()).isTrue();assertThat(m.integrationAvailable()).isFalse();
   assertThat(m.unavailableReason()).isEqualTo("INTEGRATION_NOT_IMPLEMENTED");
  });
  assertThatThrownBy(()->servicio.guardar(entrada(Map.of(),Map.of(Modulo.CXC,true),false))).hasMessageContaining("integración");
 }
 @Test void comprasDisponibleConObligatoriasValidasAunqueOpcionalesEstenVaciasYPermisoSeparado() {
  when(repo.cuentas(any())).thenReturn(Map.of("ACCOUNTS_PAYABLE_DEFAULT",cuenta("PASIVO",true,"MOVIMIENTO",false),
   "PURCHASE_EXPENSE",cuenta("GASTOS",true,"MOVIMIENTO",false)));
  autenticar(Set.of("configuracion_contable.ver"));
  var e=servicio.evaluar(entrada(Map.of(),Map.of(Modulo.COMPRAS,true),true));
  assertThat(e.modulos()).filteredOn(x->x.modulo()==Modulo.COMPRAS).singleElement().satisfies(m->{
   assertThat(m.completo()).isTrue();assertThat(m.integrationAvailable()).isTrue();assertThat(m.efectivo()).isTrue();
   assertThat(m.userCanManageAutomation()).isFalse();assertThat(m.pendientes()).isEmpty();
  });
  assertThat(FuncionesContables.compatible(FuncionesContables.funcion("PURCHASE_EXPENSE"),cuenta("COSTOS",true,"MOVIMIENTO",false))).isFalse();
 }
 @Test void soloSeisModulosYFinanzasNoPuedeReintroducirseEnPayload() {
  var estado=servicio.evaluar(entrada(Map.of(),Map.of(),false));
  assertThat(estado.modulos()).extracting(EstadoModulo::modulo).containsExactlyElementsOf(Modulo.automatizables());
  assertThat(estado.cuentasModulos()).noneMatch(m->m.modulo()==Modulo.FINANZAS);
  for(var modulo:List.of(Modulo.FINANZAS,Modulo.CIERRE)) {
   assertThatThrownBy(()->servicio.guardar(entrada(Map.of(),Map.of(modulo,true),false))).hasMessageContaining("no es válida");
   assertThatThrownBy(()->servicio.evaluar(entrada(Map.of(),Map.of(modulo,true),false))).hasMessageContaining("no es válida");
  }
  verify(repo,never()).guardar(any(),any());
 }
 @Test void endpointDevuelveExactamenteSeisModulosSinFinanzas() throws Exception {
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
   new com.citacloud.springboot.contacloud.app.controllers.ConfiguracionMotorController(servicio)).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/accounting/configuration"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.modulos.length()").value(6))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.modulos[*].modulo")
    .value(org.hamcrest.Matchers.contains("VENTAS","COMPRAS","INVENTARIO","CXC","CXP","ACTIVOS_FIJOS")));
 }
 @Test void permisoAdministrarAutomatizacionNoAutorizaEditarMapeos() {
  autenticar(Set.of("configuracion_contable.automatizacion"));
  var ar=cuenta("ACTIVO",true,"MOVIMIENTO",false);
  when(repo.cuentas(any())).thenReturn(Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",ar));
  servicio.guardar(entrada(Map.of(),Map.of(Modulo.CXC,true),true));
  verify(repo).guardar(any(),argThat(e->e.automatico()&&Boolean.TRUE.equals(e.modulos().get(Modulo.CXC))));
  assertThatThrownBy(()->servicio.guardar(entrada(Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",ar.id()),Map.of(),false)))
   .isInstanceOf(AccessDeniedException.class);
 }
 @Test void seisIntegracionesRealesDisponiblesConConfiguracionIndependienteDePermisos() {
  Map<String,Cuenta> cuentas=new HashMap<>();
  for(var f:FuncionesContables.TODAS)if(!f.obligatoria().isEmpty())cuentas.put(f.clave(),cuenta(f.clasificaciones().iterator().next(),true,"MOVIMIENTO",true));
  when(repo.cuentas(any())).thenReturn(cuentas);autenticar(Set.of("configuracion_contable.ver"));
  var resultado=servicio.evaluar(entrada(Map.of(),Map.of(),false));
  assertThat(resultado.modulos()).hasSize(6).allSatisfy(m->{
   assertThat(m.completo()).isTrue();assertThat(m.integrationAvailable()).isTrue();assertThat(m.efectivo()).isFalse();
   assertThat(m.userCanManageAutomation()).isFalse();assertThat(m.estado()).isEqualTo(EstadoConfiguracion.READY);
  });
  assertThat(resultado.puedeActivarMaestro()).isTrue();assertThat(resultado.canManageAutomation()).isFalse();
 }
 @Test void rechazaCuentaAjenaInactivaAgrupadoraOIncompatible() {
  var id=UUID.randomUUID();
  assertThatThrownBy(()->servicio.guardar(entrada(Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",id),Map.of(),false)))
   .hasMessageContaining("no está disponible");
  for(var c:List.of(cuenta("ACTIVO",false,"MOVIMIENTO",false),cuenta("ACTIVO",true,"AGRUPADORA",false),cuenta("PASIVO",true,"MOVIMIENTO",false))) {
   when(repo.cuenta(any(),eq(id))).thenReturn(Optional.of(c));
   assertThatThrownBy(()->servicio.guardar(entrada(Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",id),Map.of(),false)))
    .hasMessageContaining("compatible");
  }
  verify(repo,never()).guardar(any(),any());
 }
 @Test void capitalNoEsResultadoYSoloSeAceptaMovimientoHabilitado() {
  var f=FuncionesContables.funcion("FISCAL_RESULT_DEFAULT");
  assertThat(FuncionesContables.compatible(f,cuenta("PATRIMONIO",true,"MOVIMIENTO",false))).isFalse();
  assertThat(FuncionesContables.compatible(f,cuenta("PATRIMONIO",true,"MOVIMIENTO",true))).isTrue();
 }
 @Test void rechazaVersionAnteriorMetodoNoSoportadoYAutomatizacionSinPermiso() {
  assertThatThrownBy(()->servicio.guardar(new Entrada(2,"DEVENGADO",false,Map.of(),Map.of()))).hasMessageContaining("cambió");
  assertThatThrownBy(()->servicio.guardar(new Entrada(3,"EFECTIVO",false,Map.of(),Map.of()))).hasMessageContaining("no es válida");
  autenticar(Set.of("configuracion_contable.editar"));
  assertThatThrownBy(()->servicio.guardar(entrada(Map.of(),Map.of(),true))).isInstanceOf(AccessDeniedException.class);
  verify(repo,never()).guardar(any(),any());
 }
 @Test void cuentaMovimientoValidaSeGuardaPorUuidYAudita() {
  var c=cuenta("ACTIVO",true,"MOVIMIENTO",false);
  when(repo.cuenta(any(),eq(c.id()))).thenReturn(Optional.of(c));
  var e=entrada(Map.of("ACCOUNTS_RECEIVABLE_DEFAULT",c.id()),Map.of(),false);
  servicio.guardar(e);
  verify(repo).guardar(any(),eq(e));verify(auditoria).registrar(eq("AccountingConfigurationUpdated"),anyString(),eq(empresa),contains(c.id().toString()));
 }
 @Configuration @EnableMethodSecurity static class Seguridad {}
 @Test void seguridadBackendBloqueaLecturaYEscrituraSinPermiso() {
  try(var ctx=new AnnotationConfigApplicationContext()) {
   var m=mock(EmpresaModuloService.class);when(m.habilitado("CONFIGURACION_CONTABLE")).thenReturn(true);
   ctx.register(Seguridad.class);ctx.registerBean("empresaModuloService",EmpresaModuloService.class,()->m);
   ctx.registerBean(ConfiguracionMotorService.class,()->servicio);ctx.refresh();
   var s=ctx.getBean(ConfiguracionMotorService.class);autenticar(Set.of());
   assertThatThrownBy(s::obtener).isInstanceOf(AccessDeniedException.class);
   assertThatThrownBy(()->s.buscar("SALES_REVENUE_DEFAULT","",0,10)).isInstanceOf(AccessDeniedException.class);
   assertThatThrownBy(()->s.evaluar(entrada(Map.of(),Map.of(),false))).isInstanceOf(AccessDeniedException.class);
   assertThatThrownBy(()->s.guardar(entrada(Map.of(),Map.of(),false))).isInstanceOf(AccessDeniedException.class);
  }
 }
 private Entrada entrada(Map<String,UUID> c,Map<Modulo,Boolean> m,boolean a){return new Entrada(3,"DEVENGADO",a,c,m);}
 private Cuenta cuenta(String clasificacion,boolean activo,String tipo,boolean resultado) {
  return new Cuenta(UUID.randomUUID(),"TEST","Cuenta",activo,tipo,clasificacion,resultado,"OPCIONAL");
 }
 private void autenticar(Set<String> permisos) {
  var p=new TenantPrincipal(user,tenant,empresa,null,"TEST","Prueba","test","",true,true,Set.of(),permisos);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
 }
}

