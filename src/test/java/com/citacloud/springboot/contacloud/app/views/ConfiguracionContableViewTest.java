package com.citacloud.springboot.contacloud.app.views;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.*;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.tabs.*;
import com.vaadin.flow.router.BeforeEnterEvent;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfiguracionContableViewTest {
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 @Test void dosPestanasSinMetodoNiFinanzasGlobalYEstadosProcedentesDelBackend() {
  var vista=vista(true);var tabs=componentes(vista).filter(Tabs.class::isInstance).map(Tabs.class::cast).toList();
  assertThat(tabs.getFirst().getChildren().map(c->c.getElement().getTextRecursively()).toList())
   .containsExactly("Cuentas contables","Automatización");
  assertThat(tabs.get(1).getChildren().map(c->c.getElement().getTextRecursively()).toList())
   .containsExactly("Ventas","Compras","Inventario","CxC","CxP","Activos fijos","Cierre");
  assertThat(componentes(vista).filter(AppActionButton.class::isInstance)
   .filter(c->"Guardar".equals(c.getElement().getAttribute("aria-label")))).hasSize(2);
  assertThat(vista.getElement().getTextRecursively()).doesNotContain("Método contable","Manual","Períodos fiscales","ESTADO DE CONFIGURACIÓN")
   .doesNotContain("Finanzas").contains("Compras / gastos","Configuración incompleta");
  assertThat(checkbox(vista,"Automatizar Ventas").isEnabled()).isTrue();
  assertThat(checkbox(vista,"Automatizar Compras").isEnabled()).isFalse();
  var master=componentes(vista).filter(Checkbox.class::isInstance).map(Checkbox.class::cast)
   .filter(c->"Contabilización automática".equals(c.getLabel())).findFirst().orElseThrow();
  assertThat(master.isEnabled()).isTrue();
 }
 @Test void configurarLlevaDirectamenteACuentasDelModuloSinPersistir() {
  var vista=vista(true);
  var tabs=componentes(vista).filter(Tabs.class::isInstance).map(Tabs.class::cast).toList();
  tabs.getFirst().setSelectedIndex(1);
  var fila=componentes(vista).filter(c->c.hasClassName("cc-motor-module-row"))
   .filter(c->c.getChildren().findFirst().orElseThrow().getElement().getTextRecursively().equals("Compras")).findFirst().orElseThrow();
  var accion=componentes(fila).filter(AppActionButton.class::isInstance).map(AppActionButton.class::cast).findFirst().orElseThrow();
  assertThat(accion.getElement().getAttribute("aria-label")).isEqualTo("Configurar cuentas");accion.click();
  assertThat(tabs.getFirst().getSelectedIndex()).isZero();
  assertThat(tabs.get(1).getSelectedTab().getElement().getTextRecursively()).isEqualTo("Compras");
 }
 @Test void lecturaSinEdicionExplicaElBloqueoYSuprimeGuardar() {
  var vista=vista(false);
  assertThat(componentes(vista).filter(AppActionButton.class::isInstance)
   .filter(c->"Guardar".equals(c.getElement().getAttribute("aria-label")))).isEmpty();
  assertThat(vista.getElement().getTextRecursively()).contains("necesitas permiso para modificar la automatización");
  assertThat(checkbox(vista,"Automatizar Ventas").isEnabled()).isFalse();
 }
 private ConfiguracionContableView vista(boolean editar) {
  var permisos=new HashSet<>(Set.of("configuracion_contable.ver"));
  if(editar)permisos.addAll(Set.of("configuracion_contable.editar","configuracion_contable.automatizacion"));
  var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Test","test","",true,permisos);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
  var servicio=mock(ConfiguracionMotorService.class);var modulos=mock(EmpresaModuloService.class);
  when(modulos.habilitado("CONFIGURACION_CONTABLE")).thenReturn(true);
  // Ventas se declara lista por backend aunque el mapa local esté vacío: la UI no recalcula esta decisión.
  var estados=FuncionesContables.estados(Map.of(),Map.of(),false).stream().map(m->m.modulo()==Modulo.VENTAS?
   new EstadoModulo(Modulo.VENTAS,true,false,false,List.of()):m).toList();
  when(servicio.obtener()).thenReturn(new Estado(0,"DEVENGADO",false,true,true,Map.of(),estados));
  var vista=new ConfiguracionContableView(servicio,modulos);vista.beforeEnter(mock(BeforeEnterEvent.class));return vista;
 }
 private Checkbox checkbox(Component vista,String nombre) {
  return componentes(vista).filter(Checkbox.class::isInstance).map(Checkbox.class::cast)
   .filter(c->nombre.equals(c.getAriaLabel().orElse("")))
   .findFirst().orElseThrow();
 }
 private Stream<Component> componentes(Component c){return Stream.concat(Stream.of(c),c.getChildren().flatMap(this::componentes));}
}

