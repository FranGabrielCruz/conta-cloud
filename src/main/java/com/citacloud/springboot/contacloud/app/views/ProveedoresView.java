package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.TipoProveedor;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.util.*;

@Route(value="proveedores",layout=MainLayout.class)
@PageTitle("Proveedores | ContaCloud")
@PermitAll
public class ProveedoresView extends VerticalLayout implements BeforeEnterObserver {
    private final SupplierService service;
    private final EmpresaModuloService modulos;
    private final TextField buscar=new TextField("Buscar");
    private final Select<String> estado=new Select<>();
    private final AppGrid<ProveedorDto> grid=new AppGrid<>(ProveedorDto.class);
    private final AppPagination paginacion;

    public ProveedoresView(SupplierService service,EmpresaModuloService modulos){this.service=service;this.modulos=modulos;
        addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        Component nuevo=puede("proveedores.crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nuevo proveedor",e->formulario(null)):null;
        add(nuevo==null?new AppPageHeader("PROVEEDORES","Administra los proveedores utilizados en las compras y cuentas por pagar.")
            :new AppPageHeader("PROVEEDORES","Administra los proveedores utilizados en las compras y cuentas por pagar.",nuevo));
        buscar.setPlaceholder("Buscar por nombre, razón social, RNC o correo...");buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);buscar.setValueChangeTimeout(400);
        estado.setLabel("Estado");estado.setItems("Todos","Activo","Inactivo");estado.setValue("Todos");
        buscar.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});estado.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        HorizontalLayout filtros=new HorizontalLayout(buscar,estado);filtros.addClassName("cc-filter-bar");filtros.setAlignItems(Alignment.END);
        configurarGrid();paginacion=new AppPagination(r->cargar());add(filtros,grid,paginacion);
    }
    @Override public void beforeEnter(BeforeEnterEvent event){if(!modulos.habilitado("COMPRAS")||!puede("proveedores.ver")){
        Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}cargar();}
    private void configurarGrid(){
        grid.addColumn(ProveedorDto::nombreVisible).setHeader("Nombre / Razón social").setFlexGrow(1);
        grid.addColumn(i->valor(i.identificacionFiscal())).setHeader("RNC / Identificación").setAutoWidth(true);
        grid.addColumn(i->valor(i.telefono())).setHeader("Teléfono").setAutoWidth(true);
        grid.addColumn(i->valor(i.correo())).setHeader("Correo").setFlexGrow(1);
        grid.addComponentColumn(i->badge(i.activo())).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText(puede("proveedores.crear")?"No hay proveedores registrados. Crea el primer proveedor para comenzar a utilizar el módulo de compras.":"No hay proveedores registrados.");
    }
    private Component acciones(ProveedorDto item){HorizontalLayout h=acciones();
        h.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver proveedor",e->detalle(item.id())));
        if(puede("proveedores.editar"))h.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar proveedor",e->formulario(item.id())));return h;}
    private void cargar(){try{var r=paginacion.currentRequest();var page=service.searchSuppliers(buscar.getValue(),estadoFiltro(),r.page(),r.size(),"nombre",true);
        grid.setItems(page.getContent());grid.setEmptyStateText(buscar.getValue().isBlank()&&estadoFiltro()==null?(puede("proveedores.crear")?"No hay proveedores registrados. Crea el primer proveedor para comenzar a utilizar el módulo de compras.":"No hay proveedores registrados."):"No se encontraron proveedores con los filtros seleccionados.");
        paginacion.setTotal(page.getTotalElements());}catch(RuntimeException ex){error(ex,"No fue posible cargar los proveedores.");}}
    private void reiniciar(){paginacion.reset();cargar();}
    private Boolean estadoFiltro(){return switch(estado.getValue()==null?"Todos":estado.getValue()){case"Activo"->true;case"Inactivo"->false;default->null;};}

    private void formulario(UUID id){try{ProveedorDto actual=id==null?null:service.getSupplier(id);ProveedorCatalogosDto catalogos=service.catalogos();
        Dialog d=dialog(actual==null?"NUEVO PROVEEDOR":"EDITAR PROVEEDOR");
        TextField nombre=new TextField("Nombre comercial");nombre.setRequiredIndicatorVisible(true);nombre.setMaxLength(150);
        TextField razon=new TextField("Razón social");razon.setMaxLength(180);TextField identificacion=new TextField("RNC / Identificación");identificacion.setMaxLength(50);
        Select<TipoProveedor> tipo=new Select<>();tipo.setLabel("Tipo de proveedor");tipo.setItems(TipoProveedor.NATIONAL,TipoProveedor.FOREIGN);tipo.setItemLabelGenerator(ProveedoresView::tipo);tipo.setRequiredIndicatorVisible(true);
        TextField telefono=new TextField("Teléfono");telefono.setMaxLength(30);EmailField correo=new EmailField("Correo electrónico");correo.setMaxLength(180);
        ComboBox<ProveedorCatalogosDto.CondicionOpcion> condicion=new ComboBox<>("Condición de pago");condicion.setItems(catalogos.condiciones());condicion.setItemLabelGenerator(ProveedorCatalogosDto.CondicionOpcion::nombre);condicion.setClearButtonVisible(true);
        ComboBox<ProveedorCatalogosDto.MonedaOpcion> moneda=new ComboBox<>("Moneda");moneda.setItems(catalogos.monedas());moneda.setItemLabelGenerator(x->x.codigo()+" · "+x.nombre());moneda.setClearButtonVisible(true);
        TextArea direccion=new TextArea("Dirección");direccion.setMaxLength(500);direccion.addClassName("cc-dialog-span-2");
        TextField contacto=new TextField("Contacto");contacto.setMaxLength(150);TextField telefonoContacto=new TextField("Teléfono del contacto");telefonoContacto.setMaxLength(30);
        TextArea notas=new TextArea("Notas");notas.setMaxLength(1000);notas.addClassName("cc-dialog-span-2");Checkbox activo=new Checkbox("Activo",true);activo.addClassName("cc-dialog-span-2");
        if(actual==null)tipo.setValue(TipoProveedor.NATIONAL);else{nombre.setValue(valorVacio(actual.nombreComercial()));razon.setValue(valorVacio(actual.razonSocial()));identificacion.setValue(valorVacio(actual.identificacionFiscal()));tipo.setValue(actual.tipo());telefono.setValue(valorVacio(actual.telefono()));correo.setValue(valorVacio(actual.correo()));
            condicion.setValue(catalogos.condiciones().stream().filter(x->Objects.equals(x.id(),actual.condicionPagoId())).findFirst().orElse(null));moneda.setValue(catalogos.monedas().stream().filter(x->Objects.equals(x.id(),actual.monedaId())).findFirst().orElse(null));
            direccion.setValue(valorVacio(actual.direccion()));contacto.setValue(valorVacio(actual.contacto()));telefonoContacto.setValue(valorVacio(actual.telefonoContacto()));notas.setValue(valorVacio(actual.notas()));activo.setValue(actual.activo());activo.setReadOnly(true);}
        d.add(seccion("INFORMACIÓN GENERAL",nombre,razon,identificacion,tipo,telefono,correo),seccion("INFORMACIÓN COMERCIAL",condicion,moneda),
            seccion("DIRECCIÓN",direccion),seccion("INFORMACIÓN ADICIONAL",contacto,telefonoContacto,notas,activo));
        EstadoFormulario cambios=new EstadoFormulario();List.of(nombre,razon,identificacion,telefono,contacto,telefonoContacto).forEach(c->c.addValueChangeListener(e->cambios.marcar(e.isFromClient())));
        correo.addValueChangeListener(e->cambios.marcar(e.isFromClient()));tipo.addValueChangeListener(e->cambios.marcar(e.isFromClient()));condicion.addValueChangeListener(e->cambios.marcar(e.isFromClient()));moneda.addValueChangeListener(e->cambios.marcar(e.isFromClient()));direccion.addValueChangeListener(e->cambios.marcar(e.isFromClient()));notas.addValueChangeListener(e->cambios.marcar(e.isFromClient()));activo.addValueChangeListener(e->cambios.marcar(e.isFromClient()));
        AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",null);guardar.addClickListener(e->{ProveedorInput input=new ProveedorInput(nombre.getValue(),razon.getValue(),identificacion.getValue(),tipo.getValue(),telefono.getValue(),correo.getValue(),condicion.getValue()==null?null:condicion.getValue().id(),moneda.getValue()==null?null:moneda.getValue().id(),direccion.getValue(),contacto.getValue(),telefonoContacto.getValue(),notas.getValue(),activo.getValue());guardar(d,guardar,cambios,actual,input);});
        d.getFooter().add(guardar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->cerrarFormulario(d,cambios)));
        d.setCloseOnEsc(false);d.setCloseOnOutsideClick(false);d.addDialogCloseActionListener(e->cerrarFormulario(d,cambios));d.open();
    }catch(RuntimeException ex){error(ex,"No fue posible abrir el proveedor.");}}
    private void guardar(Dialog d,AppActionButton boton,EstadoFormulario cambios,ProveedorDto actual,ProveedorInput input){boton.setEnabled(false);try{
        if(actual==null)service.createSupplier(input);else service.updateSupplier(actual.id(),input);cambios.dirty=false;d.close();cargar();Notification.show(actual==null?"Proveedor creado correctamente.":"Proveedor actualizado correctamente.");
    }catch(RuntimeException ex){boton.setEnabled(true);error(ex,"No fue posible guardar el proveedor.");}}
    private void detalle(UUID id){try{ProveedorDto p=service.getSupplier(id);Dialog d=dialog("PROVEEDOR");
        Tab resumen=new Tab("Resumen"),documentos=new Tab("Documentos"),cuentas=new Tab("Cuentas por pagar"),pagos=new Tab("Pagos");documentos.setEnabled(false);cuentas.setEnabled(false);pagos.setEnabled(false);d.add(new Tabs(resumen,documentos,cuentas,pagos));
        Div contenido=new Div(new AppDetailSection("INFORMACIÓN GENERAL").field("Nombre comercial",p.nombreComercial()).field("Razón social",p.razonSocial()).field("RNC / Identificación",valor(p.identificacionFiscal())).field("Tipo de proveedor",tipo(p.tipo())).field("Teléfono",valor(p.telefono())).field("Correo electrónico",valor(p.correo())).field("Estado",p.activo()?"Activo":"Inactivo"),
            new AppDetailSection("INFORMACIÓN COMERCIAL").field("Condición de pago",p.condicionPagoNombre()).field("Moneda predeterminada",p.monedaCodigo()),
            new AppDetailSection("CONTACTO").field("Contacto",p.contacto()).field("Teléfono",p.telefonoContacto()),
            new AppDetailSection("DIRECCIÓN").field("Dirección",p.direccion()),new AppDetailSection("NOTAS").field("Notas",p.notas()));contenido.addClassName("cc-supplier-detail");d.add(contenido);
        if(puede("proveedores.editar"))d.getFooter().add(new AppActionButton(ActionType.EDIT,ButtonSize.MAIN,"Editar proveedor",e->{d.close();formulario(p.id());}));
        if(p.activo()&&puede("proveedores.desactivar"))d.getFooter().add(new AppActionButton(ActionType.DEACTIVATE,ButtonSize.MAIN,"Desactivar proveedor",e->confirmarEstado(p,d)));
        if(!p.activo()&&puede("proveedores.reactivar"))d.getFooter().add(new AppActionButton(ActionType.ACTIVATE,ButtonSize.MAIN,"Reactivar proveedor",e->confirmarEstado(p,d)));
        d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
    }catch(RuntimeException ex){error(ex,"No fue posible consultar el proveedor.");}}
    private void confirmarEstado(ProveedorDto p,Dialog parent){boolean desactivar=p.activo();ConfirmDialog c=new ConfirmDialog();c.setHeader(desactivar?"Desactivar proveedor":"Reactivar proveedor");c.setText(desactivar?"¿Deseas desactivar este proveedor? Permanecerá disponible en documentos históricos, pero no podrá seleccionarse normalmente en nuevas operaciones.":"El proveedor volverá a estar disponible para nuevas operaciones. ¿Deseas continuar?");c.setCancelable(true);c.setCancelText("Cancelar");c.setConfirmText(desactivar?"Desactivar":"Reactivar");if(desactivar)c.setConfirmButtonTheme("error primary");c.addConfirmListener(e->{try{if(desactivar)service.deactivateSupplier(p.id());else service.reactivateSupplier(p.id());parent.close();cargar();Notification.show(desactivar?"Proveedor desactivado correctamente.":"Proveedor reactivado correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible cambiar el estado del proveedor.");}});c.open();}
    private static Div seccion(String titulo,Component...campos){Div d=new Div();d.addClassName("cc-form-section");H3 h=new H3(titulo);h.addClassName("cc-section-heading");Div f=new Div(campos);f.addClassName("cc-dialog-form");d.add(h,f);return d;}
    private static Dialog dialog(String titulo){Dialog d=new Dialog();d.setHeaderTitle(titulo);d.setWidth("min(900px, 96vw)");return d;}
    private static HorizontalLayout acciones(){HorizontalLayout h=new HorizontalLayout();h.addClassName("cc-grid-actions");h.setPadding(false);h.setSpacing(false);return h;}
    private static Span badge(boolean activo){Span s=new Span(activo?"Activo":"Inactivo");s.getElement().getThemeList().add("badge "+(activo?"success":"contrast"));return s;}
    private static String tipo(TipoProveedor t){return t==TipoProveedor.NATIONAL?"Nacional":"Extranjero";}
    private static String valor(String v){return v==null||v.isBlank()?"—":v;}private static String valorVacio(String v){return v==null?"":v;}
    private static boolean puede(String permiso){return TenantContext.principalActual().permisos().contains(permiso);}
    private static void cerrarFormulario(Dialog d,EstadoFormulario cambios){if(!cambios.dirty){d.close();return;}ConfirmDialog c=new ConfirmDialog();c.setHeader("Cambios sin guardar");c.setText("Hay cambios sin guardar. ¿Deseas descartarlos?");c.setCancelable(true);c.setCancelText("Continuar editando");c.setConfirmText("Descartar cambios");c.setConfirmButtonTheme("error primary");c.addConfirmListener(e->{cambios.dirty=false;d.close();});c.open();}
    private static void error(RuntimeException ex,String fallback){String m=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():fallback;Notification.show(m==null||m.isBlank()?fallback:m);}
    private static final class EstadoFormulario{boolean dirty;void marcar(boolean cliente){if(cliente)dirty=true;}}
}
