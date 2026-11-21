package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.util.*;

/** La vista presenta los estados que calcula el backend, incluida la evaluación de cambios pendientes. */
@Route(value="configuracion-contable",layout=MainLayout.class)
@PageTitle("Configuración contable | ContaCloud")
@PermitAll
public class ConfiguracionContableView extends VerticalLayout implements BeforeEnterObserver,BeforeLeaveObserver {
 private final ConfiguracionMotorService servicio;
 private final EmpresaModuloService modulos;
 private final Div cuentas=new Div(),automatizacion=new Div(),aviso=new Div(),resumen=new Div();
 private final Checkbox maestro=new Checkbox("Contabilización automática");
 private final Tab cuentasTab=new Tab("Cuentas contables"),autoTab=new Tab("Automatización");
 private final Tabs tabs=new Tabs(cuentasTab,autoTab),modulosTabs=new Tabs();
 private final Map<Modulo,Tab> pestanasModulo=new EnumMap<>(Modulo.class);
 private final Map<String,UUID> valores=new HashMap<>();
 private final Map<String,List<ComboBox<Cuenta>>> selectores=new HashMap<>();
 private final Map<Modulo,Checkbox> switches=new EnumMap<>(Modulo.class);
 private final Map<Modulo,Div> estadosModulo=new EnumMap<>(Modulo.class);
 private final Map<Modulo,Div> indicadoresCuenta=new EnumMap<>(Modulo.class);
 private final Map<Modulo,AppActionButton> configurar=new EnumMap<>(Modulo.class);
 private Estado actual,evaluacion;
 private boolean cargando,dirty,cambiandoTab;
 private final boolean editar=permiso("configuracion_contable.editar","CONFIGURACION_CONTABLE_EDITAR");
 private final boolean editarAuto=permiso("configuracion_contable.automatizacion");

 public ConfiguracionContableView(ConfiguracionMotorService servicio,EmpresaModuloService modulos) {
  this.servicio=servicio;this.modulos=modulos;
  setPadding(false);setSpacing(false);setWidthFull();addClassNames("cc-page","cc-motor-config");
  add(new AppPageHeader("CONFIGURACIÓN CONTABLE","Define las cuentas y reglas utilizadas por el motor contable de la empresa."));
  tabs.addClassName("cc-infrastructure-tabs");
  cuentas.addClassName("cc-motor-panel");automatizacion.addClassName("cc-motor-panel");
  crearCuentas();crearAutomatizacion();automatizacion.setVisible(false);
  var contenido=new Div(cuentas,automatizacion);contenido.addClassName("cc-motor-content");
  tabs.addSelectedChangeListener(e->{
   if(cambiandoTab)return;
   if(dirty&&e.isFromClient()) {
    var destino=e.getSelectedTab();cambiandoTab=true;tabs.setSelectedTab(e.getPreviousTab());cambiandoTab=false;
    confirmarDescartar(()->{cargar(actual);tabs.setSelectedTab(destino);});
   } else {cuentas.setVisible(e.getSelectedTab()==cuentasTab);automatizacion.setVisible(e.getSelectedTab()==autoTab);}
  });
  add(tabs,contenido);
 }
 private Div encabezado(String titulo,String descripcion) {
  var header=new Div(new H3(titulo),new Paragraph(descripcion));header.addClassName("cc-motor-tab-header");
  if("AUTOMATIZACIÓN".equals(titulo)?editarAuto:editar)header.add(new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->guardar()));
  return header;
 }
 private void crearCuentas() {
  cuentas.add(encabezado("CUENTAS CONTABLES","Define las cuentas utilizadas por el motor contable."));
  modulosTabs.setWidthFull();Map<Tab,Div> panels=new LinkedHashMap<>();
  for(var m:Modulo.values()) {
   if(m==Modulo.FINANZAS)continue;
   var tab=new Tab(m.etiqueta());modulosTabs.add(tab);pestanasModulo.put(m,tab);
   var panel=new Div();panel.add(new H3(m.etiqueta()));var form=new Div();form.addClassName("cc-motor-fields");
   for(var f:FuncionesContables.TODAS)if(f.modulos().contains(m)) {
    var combo=new ComboBox<Cuenta>(f.nombre());combo.setWidthFull();combo.setClearButtonVisible(true);
    combo.setItemLabelGenerator(Cuenta::etiqueta);combo.setRequiredIndicatorVisible(f.obligatoria().contains(m));combo.setReadOnly(!editar);
    combo.setItems(q->servicio.buscar(f.clave(),q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());
    combo.setHelperText(f.obligatoria().contains(m)?"Obligatoria":"Opcional");
    selectores.computeIfAbsent(f.clave(),k->new ArrayList<>()).add(combo);
    combo.addValueChangeListener(e->{
     if(cargando)return;
     valores.put(f.clave(),e.getValue()==null?null:e.getValue().id());cargando=true;
     selectores.get(f.clave()).stream().filter(c->c!=combo).forEach(c->c.setValue(e.getValue()));
     cargando=false;dirty=true;refrescarEstados();
    });form.add(combo);
   }
   if(m==Modulo.CIERRE)panel.add(new Paragraph("El resultado del ejercicio se requiere para el cierre anual. No condiciona el cierre mensual."));
   var indicador=new Div();indicadoresCuenta.put(m,indicador);panel.add(form,indicador);
   panel.setVisible(m==Modulo.VENTAS);panels.put(tab,panel);
  }
  modulosTabs.addSelectedChangeListener(e->panels.forEach((tab,panel)->panel.setVisible(tab==e.getSelectedTab())));
  cuentas.add(modulosTabs);panels.values().forEach(cuentas::add);
  cuentas.add(new Paragraph("Sin resultados: no existen cuentas de movimiento disponibles para esta función. Crea o activa cuentas compatibles en el Catálogo de cuentas."));
 }
 private void crearAutomatizacion() {
  automatizacion.add(encabezado("AUTOMATIZACIÓN","Controla qué módulos generan asientos automáticamente."),resumen,maestro);
  maestro.setReadOnly(!editarAuto);
  maestro.addValueChangeListener(e->{if(!cargando){dirty=true;refrescarEstados();}});
  automatizacion.add(new Paragraph("Activa la generación automática de asientos para los módulos que hayas habilitado."),aviso);
  aviso.getElement().setAttribute("role","status");
  var filas=new Div();filas.addClassName("cc-motor-module-list");
  var titulos=new Div(new Span("Módulo"),new Span("Estado"),new Span("Automático"));titulos.addClassName("cc-motor-module-head");filas.add(titulos);
  for(var m:Modulo.automatizables()) {
   var status=new Div();estadosModulo.put(m,status);
   var checkbox=new Checkbox();checkbox.setAriaLabel("Automatizar "+m.etiqueta());checkbox.setReadOnly(!editarAuto);switches.put(m,checkbox);
   checkbox.addValueChangeListener(e->{if(!cargando){dirty=true;refrescarEstados();}});
   var acciones=new Div(checkbox);acciones.addClassName("cc-motor-module-actions");
   if(m!=Modulo.FINANZAS) {
    var boton=new AppActionButton(ActionType.CONFIGURE,ButtonSize.GRID_ACTION,"Configurar cuentas",e->irACuentas(m));
    configurar.put(m,boton);acciones.add(boton);
   } else {
    if(permiso("cajas.ver"))acciones.add(new AppActionButton(ActionType.CONFIGURE,ButtonSize.GRID_ACTION,"Configurar cajas",e->UI.getCurrent().navigate("cajas")));
    if(permiso("cuentas_bancarias.ver"))acciones.add(new AppActionButton(ActionType.CONFIGURE,ButtonSize.GRID_ACTION,"Configurar cuentas bancarias",e->UI.getCurrent().navigate("cuentas-bancarias")));
   }
   var fila=new Div(new Span(m.etiqueta()),status,acciones);fila.addClassName("cc-motor-module-row");filas.add(fila);
  }
  automatizacion.add(filas,new Paragraph("Los cambios se aplican a nuevas operaciones. Apagar el interruptor general conserva las preferencias por módulo."));
 }
 private void irACuentas(Modulo m) {
  Runnable navegar=()->{tabs.setSelectedTab(cuentasTab);if(m!=null)modulosTabs.setSelectedTab(pestanasModulo.get(m));};
  if(dirty)confirmarDescartar(()->{cargar(actual);navegar.run();});else navegar.run();
 }
 @Override public void beforeEnter(BeforeEnterEvent e) {
  if(e.getLocation()!=null&&e.getLocation().getQueryParameters().getParameters().getOrDefault("tab",List.of()).contains("periodos")) {
   e.rerouteTo(PeriodosFiscalesView.class);return;
  }
  if(!modulos.habilitado("CONFIGURACION_CONTABLE")||!permiso("configuracion_contable.ver","CONFIGURACION_CONTABLE_VER")) {
   e.rerouteTo(DashboardView.class);return;
  }
  try{cargar(servicio.obtener());}catch(RuntimeException ex){error(ex);}
 }
 private Entrada entrada() {
  Map<String,UUID> cambios=new HashMap<>();
  valores.forEach((clave,id)->{
   var previa=actual.cuentas().get(clave);
   if(!Objects.equals(previa==null?null:previa.id(),id))cambios.put(clave,id);
  });
  return new Entrada(actual.version(),"DEVENGADO",maestro.getValue(),cambios,flags());
 }
 private void cargar(Estado estado) {
  actual=estado;evaluacion=estado;cargando=true;valores.clear();maestro.setValue(estado.automatico());
  selectores.forEach((clave,cs)->{var c=estado.cuentas().get(clave);valores.put(clave,c==null?null:c.id());cs.forEach(s->s.setValue(c));});
  estado.modulos().forEach(m->{if(switches.containsKey(m.modulo()))switches.get(m.modulo()).setValue(m.automatico());});
  cargando=false;dirty=false;presentarEstados();
 }
 private void refrescarEstados() {
  if(actual==null)return;
  try{evaluacion=servicio.evaluar(entrada());presentarEstados();}catch(RuntimeException ex){error(ex);}
 }
 private void presentarEstados() {
  if(evaluacion==null)return;
  var estados=evaluacion.cuentasModulos();
  selectores.forEach((k,cs)->cs.forEach(c->{
   boolean invalida=c.getValue()!=null&&estados.stream().anyMatch(m->m.pendientes().contains(FuncionesContables.funcion(k).nombre()));
   c.setInvalid(invalida);c.setErrorMessage("La cuenta configurada no está disponible o no es compatible.");
   c.setEnabled(editar&&evaluacion.catalogo());
  }));
  long listos=evaluacion.modulosListos();
  resumen.setText(listos+" de "+Modulo.automatizables().size()+" módulos con configuración disponible · "+evaluacion.modulosActivos()+" activos");
  maestro.setEnabled(editarAuto&&(evaluacion.puedeActivarMaestro()||maestro.getValue()));
  aviso.removeAll();
  aviso.setClassName("cc-motor-notice-warning",listos==0);
  if(!editarAuto)aviso.add(new Paragraph("Puedes consultar la configuración, pero necesitas permiso para modificar la automatización."));
  else if(listos==0) {
   aviso.add(new H3("⚠ CONTABILIZACIÓN AUTOMÁTICA NO DISPONIBLE"),
    new Paragraph("Debes completar la configuración contable de al menos un módulo antes de activar la contabilización automática."),
    new AppActionButton(ActionType.CONFIGURE,ButtonSize.GRID_ACTION,"Ir a Cuentas contables",e->irACuentas(null)));
  } else if(!evaluacion.puedeActivarMaestro()&&!maestro.getValue())
   aviso.add(new Paragraph("Selecciona al menos un módulo con configuración completa para habilitar el interruptor general."));
  else aviso.add(new Paragraph(maestro.getValue()?(evaluacion.modulosActivos()>0?"✓ Contabilización automática activa.":
   "Interruptor general encendido, sin módulos activos. Revisa la configuración o selecciona un módulo disponible."):
   "Interruptor general apagado. Se conservan las preferencias individuales."));
  for(var m:evaluacion.cuentasModulos()) {
   var indicador=indicadoresCuenta.get(m.modulo());
   if(indicador!=null){indicador.removeAll();indicador.add(new Paragraph(m.completo()?"✓ Configuración completa":
    m.pendientes().isEmpty()?"✓ Cuentas configuradas. La integración operativa de automatización aún no está disponible.":
    "⚠ Falta configurar: "+String.join(", ",m.pendientes())));}
   var status=estadosModulo.get(m.modulo());if(status==null)continue;
   status.removeAll();var badge=new Span(m.efectivo()?"✓ Activa":!m.completo()?"⚠ Configuración incompleta":!m.integrationAvailable()?"⚠ No disponible":"✓ Disponible");
   badge.addClassName(m.completo()&&m.integrationAvailable()?"cc-motor-status-ready":"cc-motor-status-warning");status.add(badge);
   if(!m.completo()||!m.integrationAvailable()) {
    if(!m.pendientes().isEmpty())status.add(new Paragraph("Falta configurar: "+String.join(", ",m.pendientes())));
    if(m.motivo()!=null&&!m.motivo().isBlank())status.add(new Paragraph(m.motivo()));
   } else if(m.modulo()==Modulo.FINANZAS)status.add(new Paragraph("Disponible para transferencias en moneda base. Se validará la cuenta de cada recurso financiero utilizado."));
   if(m.automatico()&&!m.efectivo())status.add(new Paragraph(
    m.completo()&&m.integrationAvailable()?"Seleccionado · interruptor general apagado":"Preferencia conservada · contabilización bloqueada"));
   switches.get(m.modulo()).setEnabled(editarAuto&&m.userCanManageAutomation()&&m.completo()&&m.integrationAvailable());
   var configurarCuentas=configurar.get(m.modulo());if(configurarCuentas!=null)configurarCuentas.setVisible(!m.completo()&&!m.pendientes().isEmpty());
  }
 }
 private Map<Modulo,Boolean> flags(){Map<Modulo,Boolean> r=new EnumMap<>(Modulo.class);switches.forEach((m,c)->r.put(m,c.getValue()));return r;}
 private void guardar(){if(actual==null)return;try{cargar(servicio.guardar(entrada()));Notification.show(tabs.getSelectedTab()==autoTab?"Configuración de automatización guardada correctamente.":"Configuración contable guardada.");}catch(RuntimeException ex){error(ex);}}
 private void confirmarDescartar(Runnable accion) {
  var d=new ConfirmDialog();d.setHeader("Cambios sin guardar");d.setText("Hay cambios sin guardar. ¿Deseas salir sin guardarlos?");
  d.setCancelable(true);d.setCancelText("Continuar editando");d.setConfirmText("Descartar cambios");d.setConfirmButtonTheme("error primary");
  d.addConfirmListener(e->accion.run());d.open();
 }
 @Override public void beforeLeave(BeforeLeaveEvent e){if(dirty){var a=e.postpone();confirmarDescartar(()->{dirty=false;a.proceed();});}}
 private static boolean permiso(String... ps){return Arrays.stream(ps).anyMatch(TenantContext.principalActual().permisos()::contains);}
 private static void error(RuntimeException ex){com.citacloud.springboot.contacloud.app.errors.UiErrorHandler.show(ex,"No fue posible actualizar la configuración contable.");}
}
