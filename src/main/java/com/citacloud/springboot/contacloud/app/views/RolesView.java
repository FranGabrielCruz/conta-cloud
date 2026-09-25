package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.RolService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.util.*;
import java.util.stream.Collectors;

@Route(value="roles",layout=MainLayout.class) @PageTitle("Roles y permisos | ContaCloud") @PermitAll
public class RolesView extends VerticalLayout implements BeforeEnterObserver {
    private static final List<String> ORDEN_SECCIONES = List.of("Configuración", "Usuarios", "Roles y permisos",
        "Tasas de cambio", "Impuestos", "Comprobantes fiscales", "Secuencias",
        "Condiciones de pago", "Períodos fiscales", "Configuración contable", "Auditoría", "General");
    private final RolService service; private final AppGrid<RolDto> grid=new AppGrid<>(RolDto.class);
    private final TextField buscar=new TextField();private final ComboBox<String> estado=new ComboBox<>();private final AppPagination paginacion;
    public RolesView(RolService service){this.service=service;addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        var nuevo=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nuevo rol",e->editar(null));nuevo.setVisible(puede("ROL_CREAR","roles.crear"));
        add(new AppPageHeader("Roles y permisos","Define las responsabilidades y permisos de acceso para esta empresa.",nuevo));configurarFiltros();configurarGrid();paginacion=new AppPagination(r->cargar());
        var filtros=new HorizontalLayout(buscar,estado);filtros.addClassName("cc-filter-bar");filtros.setWidthFull();filtros.setAlignItems(Alignment.END);add(filtros,grid,paginacion);if(puede("ROL_VER","roles.ver"))cargar();}
    @Override public void beforeEnter(BeforeEnterEvent e){if(!puede("ROL_VER","roles.ver"))e.rerouteTo(DashboardView.class);}
    private void configurarFiltros(){buscar.setLabel("Buscar");buscar.setPlaceholder("Nombre o descripción");buscar.setClearButtonVisible(true);buscar.setValueChangeMode(ValueChangeMode.LAZY);buscar.addValueChangeListener(e->{paginacion.reset();cargar();});estado.setLabel("Estado");estado.setItems("Todos","Activos","Inactivos");estado.setValue("Todos");estado.addValueChangeListener(e->{if(paginacion!=null){paginacion.reset();cargar();}});}
    private void configurarGrid(){grid.addColumn(RolDto::nombre).setHeader("Rol").setSortable(true);grid.addColumn(RolDto::descripcion).setHeader("Descripción");grid.addColumn(RolDto::usuarios).setHeader("Usuarios");grid.addComponentColumn(r->estado(r.activo())).setHeader("Estado");grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);}
    private Component acciones(RolDto r){var row=new HorizontalLayout();row.setPadding(false);row.setSpacing(false);row.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver rol",e->ver(service.obtener(r.id()))));if(puede("ROL_EDITAR","roles.editar"))row.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar rol",e->editar(service.obtener(r.id()))));if(r.activo()&&!r.protegido()&&puede("ROL_DESACTIVAR","roles.desactivar"))row.add(new AppActionButton(ActionType.DEACTIVATE,ButtonSize.GRID_ACTION,"Desactivar rol",e->confirmar(r)));return row;}
    private void cargar(){if(paginacion==null)return;var req=paginacion.currentRequest();Boolean activo=switch(estado.getValue()==null?"Todos":estado.getValue()){case"Activos"->true;case"Inactivos"->false;default->null;};var p=service.buscar(buscar.getValue(),activo,req.page(),req.size());grid.setItems(p.getContent());paginacion.setTotal(p.getTotalElements());}
    private void editar(RolDto actual){var d=new Dialog();d.setHeaderTitle(actual==null?"Nuevo rol":"Editar rol");d.setWidth("min(900px, 96vw)");var nombre=new TextField("Nombre");var descripcion=new TextArea("Descripción");var activo=new Checkbox("Activo",true);descripcion.setMaxLength(255);descripcion.setWidthFull();
        if(actual!=null){nombre.setValue(valor(actual.nombre()));descripcion.setValue(valor(actual.descripcion()));activo.setValue(actual.activo());if(actual.protegido()){nombre.setReadOnly(true);activo.setReadOnly(true);}}
        Map<UUID,Checkbox> checks=new LinkedHashMap<>();var matriz=new Div();matriz.addClassName("cc-permission-matrix");
        Map<String,List<OpcionPermiso>> secciones=opcionesPermisos().stream().collect(Collectors.groupingBy(o->seccionPermiso(o.permiso()),LinkedHashMap::new,Collectors.toList()));
        ORDEN_SECCIONES.forEach(seccion->{List<OpcionPermiso> opciones=secciones.remove(seccion);if(opciones!=null)matriz.add(crearGrupoPermisos(seccion,opciones,actual,checks));});
        secciones.forEach((seccion,opciones)->matriz.add(crearGrupoPermisos(seccion,opciones,actual,checks)));
        var form=new Div(nombre,descripcion,activo);form.addClassName("cc-dialog-form");d.add(form,new H2("Permisos"),matriz);
        var guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{try{Set<UUID> ids=checks.entrySet().stream().filter(x->x.getValue().getValue()).map(Map.Entry::getKey).collect(Collectors.toSet());var input=new RolInputDto(nombre.getValue(),descripcion.getValue(),activo.getValue(),ids);if(actual==null)service.crear(input);else service.actualizar(actual.id(),input);d.close();cargar();notificar("Rol guardado correctamente.");}catch(RuntimeException ex){notificar(ex.getMessage());}});d.getFooter().add(guardar);
        if(actual!=null&&actual.activo()&&!actual.protegido()&&puede("ROL_DESACTIVAR","roles.desactivar"))d.getFooter().add(new AppActionButton(ActionType.DEACTIVATE,ButtonSize.MAIN,"Desactivar rol",e->{d.close();confirmar(actual);}));d.getFooter().add(new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}
    private void ver(RolDto rol){var d=new Dialog();d.setHeaderTitle("Detalle del rol");List<PermisoDto> permisos=service.catalogoPermisos().stream().filter(p->rol.permisoIds().contains(p.id())).toList();var detalle=new AppDetailSection("Información").field("Nombre",rol.nombre()).field("Descripción",rol.descripcion()).field("Usuarios asignados",Long.toString(rol.usuarios())).field("Estado",rol.activo()?"Activo":"Inactivo");var lista=new Div();lista.addClassName("cc-permission-summary");permisos.stream().collect(Collectors.groupingBy(RolesView::seccionPermiso,LinkedHashMap::new,Collectors.toList())).forEach((m,ps)->lista.add(new H3(m),new Paragraph(ps.stream().map(PermisoDto::nombre).distinct().collect(Collectors.joining(", ")))));d.add(detalle,lista);d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();}
    private void confirmar(RolDto rol){var c=new ConfirmDialog();c.setHeader("Desactivar rol");c.setText("¿Desea desactivar el rol "+rol.nombre()+"?");c.setCancelable(true);c.setCancelText("Cancelar");c.setConfirmText("Desactivar");c.setConfirmButtonTheme("error primary");c.addConfirmListener(e->{try{service.desactivar(rol.id());cargar();notificar("Rol desactivado.");}catch(RuntimeException ex){notificar(ex.getMessage());}});c.open();}
    private List<OpcionPermiso> opcionesPermisos(){
        Map<String,List<PermisoDto>> equivalentes=service.catalogoPermisos().stream().collect(Collectors.groupingBy(p->seccionPermiso(p)+"|"+accionPermiso(p),LinkedHashMap::new,Collectors.toList()));
        return equivalentes.values().stream().map(lista->{PermisoDto principal=lista.stream().filter(p->p.codigo().contains(".")).findFirst().orElse(lista.getFirst());return new OpcionPermiso(principal,lista.stream().map(PermisoDto::id).collect(Collectors.toSet()));})
            .sorted(Comparator.comparingInt((OpcionPermiso o)->indiceSeccion(seccionPermiso(o.permiso()))).thenComparing(o->o.permiso().nombre())).toList();
    }
    private static Div crearGrupoPermisos(String titulo,List<OpcionPermiso> opciones,RolDto actual,Map<UUID,Checkbox> checks){var grupo=new Div();grupo.addClassName("cc-permission-group");grupo.add(new H3(titulo));if("Configuración".equals(titulo)){Map<String,List<OpcionPermiso>> subsecciones=opciones.stream().collect(Collectors.groupingBy(o->subseccionConfiguracion(o.permiso()),LinkedHashMap::new,Collectors.toList()));List.of("Empresa","Sucursales","Monedas").forEach(nombre->{List<OpcionPermiso> items=subsecciones.remove(nombre);if(items!=null){grupo.add(new H4(nombre));agregarOpciones(grupo,items,actual,checks);}});subsecciones.forEach((nombre,items)->{grupo.add(new H4(nombre));agregarOpciones(grupo,items,actual,checks);});}else agregarOpciones(grupo,opciones,actual,checks);return grupo;}
    private static void agregarOpciones(Div grupo,List<OpcionPermiso> opciones,RolDto actual,Map<UUID,Checkbox> checks){for(OpcionPermiso opcion:opciones){PermisoDto p=opcion.permiso();var check=new Checkbox(p.nombre());check.setHelperText(p.descripcion());check.setValue(actual!=null&&opcion.idsEquivalentes().stream().anyMatch(actual.permisoIds()::contains));checks.put(p.id(),check);grupo.add(check);}}
    private static String seccionPermiso(PermisoDto p){String recurso=recursoPermiso(p);return switch(recurso){case"empresa","sucursal","sucursales","moneda","monedas"->"Configuración";case"usuario","usuarios"->"Usuarios";case"rol","roles"->"Roles y permisos";case"tasa_cambio","tasas_cambio"->"Tasas de cambio";case"impuesto","impuestos"->"Impuestos";case"comprobante","comprobantes"->"Comprobantes fiscales";case"secuencia","secuencias"->"Secuencias";case"condicion_pago","condiciones_pago"->"Condiciones de pago";case"periodo","periodos"->"Períodos fiscales";case"configuracion_contable"->"Configuración contable";case"auditoria"->"Auditoría";default->"General";};}
    private static String subseccionConfiguracion(PermisoDto p){return switch(recursoPermiso(p)){case"empresa"->"Empresa";case"sucursal","sucursales"->"Sucursales";case"moneda","monedas"->"Monedas";default->"General";};}
    private static String recursoPermiso(PermisoDto p){String recurso=valor(p.recurso()).toLowerCase(Locale.ROOT);String codigo=p.codigo().toUpperCase(Locale.ROOT);if(recurso.isBlank()){if(codigo.startsWith("CONFIGURACION_CONTABLE"))recurso="configuracion_contable";else if(codigo.startsWith("CONDICION_PAGO"))recurso="condicion_pago";else if(codigo.startsWith("TASA_CAMBIO"))recurso="tasa_cambio";else recurso=codigo.contains("_")?codigo.substring(0,codigo.indexOf('_')).toLowerCase(Locale.ROOT):codigo.toLowerCase(Locale.ROOT);}return recurso;}
    private static String accionPermiso(PermisoDto p){if(p.accion()!=null&&!p.accion().isBlank())return p.accion().toLowerCase(Locale.ROOT);String codigo=p.codigo();int punto=codigo.lastIndexOf('.'),guion=codigo.lastIndexOf('_');return codigo.substring(Math.max(punto,guion)+1).toLowerCase(Locale.ROOT);}
    private static int indiceSeccion(String seccion){int indice=ORDEN_SECCIONES.indexOf(seccion);return indice<0?ORDEN_SECCIONES.size():indice;}
    private record OpcionPermiso(PermisoDto permiso,Set<UUID> idsEquivalentes){}
    private static Span estado(boolean activo){var s=new Span(activo?"Activo":"Inactivo");s.getElement().getThemeList().add("badge "+(activo?"success":"contrast"));return s;}private static boolean puede(String a,String b){var p=TenantContext.principalActual().permisos();return p.contains(a)||p.contains(b);}private static String valor(String v){return v==null?"":v;}private static void notificar(String m){com.vaadin.flow.component.notification.Notification.show(m==null?"No fue posible completar la operación.":m);}
}
