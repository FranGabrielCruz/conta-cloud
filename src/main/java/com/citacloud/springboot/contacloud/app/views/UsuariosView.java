package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.util.*;

@Route(value="usuarios",layout=MainLayout.class) @PageTitle("Usuarios | ContaCloud") @PermitAll
public class UsuariosView extends VerticalLayout implements BeforeEnterObserver {
    private static final OpcionSeguridadDto TODAS = new OpcionSeguridadDto(null,"Todas las sucursales",true);
    private final UsuarioService service; private final AppGrid<UsuarioDto> grid=new AppGrid<>(UsuarioDto.class);
    private final TextField buscar=new TextField(); private final ComboBox<String> estado=new ComboBox<>();
    private final AppPagination paginacion; private boolean ajustandoSucursales;
    public UsuariosView(UsuarioService service){
        this.service=service; addClassName("cc-page"); setPadding(false);setSpacing(false);setWidthFull();
        var nuevo=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nuevo usuario",e->editar(null));
        nuevo.setVisible(puede("USUARIO_CREAR","usuarios.crear"));
        add(new AppPageHeader("Usuarios","Administra el acceso, rol y sucursales de los usuarios de la empresa.",nuevo));
        configurarFiltros(); configurarGrid(); paginacion=new AppPagination(r->cargar());
        var filtros=new HorizontalLayout(buscar,estado); filtros.addClassName("cc-filter-bar"); filtros.setWidthFull(); filtros.setAlignItems(Alignment.END);
        add(filtros,grid,paginacion); if(puede("USUARIO_VER","usuarios.ver"))cargar();
    }
    @Override public void beforeEnter(BeforeEnterEvent e){if(!puede("USUARIO_VER","usuarios.ver")){e.rerouteTo(DashboardView.class);}}
    private void configurarFiltros(){
        buscar.setLabel("Buscar");buscar.setPlaceholder("Usuario, nombre, correo o teléfono");buscar.setClearButtonVisible(true);buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.addValueChangeListener(e->{paginacion.reset();cargar();});
        estado.setLabel("Estado");estado.setItems("Todos","Activos","Inactivos");estado.setValue("Todos");estado.addValueChangeListener(e->{if(paginacion!=null){paginacion.reset();cargar();}});
    }
    private void configurarGrid(){
        grid.addColumn(UsuarioDto::usuario).setHeader("Usuario").setSortable(true);
        grid.addColumn(u->u.nombre()+" "+u.apellido()).setHeader("Nombre completo");
        grid.addColumn(UsuarioDto::correo).setHeader("Correo");grid.addColumn(UsuarioDto::telefono).setHeader("Teléfono");
        grid.addColumn(UsuarioDto::rolNombre).setHeader("Rol");grid.addColumn(UsuarioDto::sucursalesResumen).setHeader("Sucursales");
        grid.addComponentColumn(u->estado(u.activo())).setHeader("Estado");
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
    }
    private Component acciones(UsuarioDto u){var row=new HorizontalLayout();row.setPadding(false);row.setSpacing(false);
        row.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver usuario",e->ver(u)));
        if(puede("USUARIO_EDITAR","usuarios.editar"))row.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar usuario",e->editar(u)));
        if(u.activo()&&puede("USUARIO_DESACTIVAR","usuarios.desactivar"))row.add(new AppActionButton(ActionType.DEACTIVATE,ButtonSize.GRID_ACTION,"Desactivar usuario",e->confirmarDesactivar(u)));
        return row;}
    private void cargar(){if(paginacion==null)return;var r=paginacion.currentRequest();Boolean activo=switch(estado.getValue()==null?"Todos":estado.getValue()){case "Activos"->true;case "Inactivos"->false;default->null;};
        var page=service.buscar(buscar.getValue(),activo,r.page(),r.size());grid.setItems(page.getContent());paginacion.setTotal(page.getTotalElements());}
    private void editar(UsuarioDto actual){
        var dialog=new Dialog();dialog.setHeaderTitle(actual==null?"Nuevo usuario":"Editar usuario");dialog.setWidth("min(920px, 96vw)");
        var usuario=new TextField("Usuario");var nombre=new TextField("Nombre");var apellido=new TextField("Apellido");var correo=new EmailField("Correo");var telefono=new TextField("Teléfono");InputFieldSupport.aplicarFormatoTelefono(telefono);
        var rol=new ComboBox<OpcionSeguridadDto>("Rol");rol.setItems(service.rolesDisponibles());rol.setItemLabelGenerator(OpcionSeguridadDto::nombre);
        var sucursal=new MultiSelectComboBox<OpcionSeguridadDto>("Sucursales");var opciones=new ArrayList<OpcionSeguridadDto>();opciones.add(TODAS);opciones.addAll(service.sucursalesDisponibles());sucursal.setItems(opciones);sucursal.setItemLabelGenerator(OpcionSeguridadDto::nombre);
        sucursal.addValueChangeListener(e->{if(ajustandoSucursales)return;if(e.getValue().contains(TODAS)&&e.getValue().size()>1){ajustandoSucursales=true;sucursal.setValue(Set.of(TODAS));ajustandoSucursales=false;}});
        var clave=new PasswordField(actual==null?"Contraseña":"Nueva contraseña (opcional)");var confirmar=new PasswordField("Confirmar contraseña");var activo=new Checkbox("Activo",true);
        if(actual==null){InputFieldSupport.desactivarAutocompletado(usuario,"nuevo-usuario");InputFieldSupport.desactivarAutocompletado(sucursal,"nuevo-usuario-sucursales");InputFieldSupport.desactivarAutocompletado(telefono,"nuevo-usuario-telefono");InputFieldSupport.nuevaContrasena(clave,"nuevo-usuario-clave");InputFieldSupport.nuevaContrasena(confirmar,"nuevo-usuario-confirmar-clave");}
        if(actual!=null){usuario.setValue(valor(actual.usuario()));nombre.setValue(valor(actual.nombre()));apellido.setValue(valor(actual.apellido()));correo.setValue(valor(actual.correo()));telefono.setValue(InputFieldSupport.formatearTelefono(valor(actual.telefono())));activo.setValue(actual.activo());
            rol.getListDataView().getItems().filter(x->Objects.equals(x.id(),actual.rolId())).findFirst().ifPresent(rol::setValue);
            if(actual.todasSucursales())sucursal.setValue(Set.of(TODAS));else{sucursal.setValue(opciones.stream().filter(x->actual.sucursalIds().contains(x.id())).collect(java.util.stream.Collectors.toSet()));}}
        else{sucursal.clear();clave.clear();confirmar.clear();activo.setValue(true);}
        var form=new Div(usuario,nombre,apellido,correo,telefono,rol,sucursal,clave,confirmar,activo);form.addClassName("cc-dialog-form");dialog.add(form);
        var guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{try{Set<OpcionSeguridadDto> sel=sucursal.getValue();boolean todas=sel.contains(TODAS);Set<UUID> ids=sel.stream().map(OpcionSeguridadDto::id).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            UsuarioInputDto input=new UsuarioInputDto(usuario.getValue(),nombre.getValue(),apellido.getValue(),correo.getValue(),telefono.getValue(),rol.getValue()==null?null:rol.getValue().id(),todas,ids,activo.getValue());
            if(actual==null)service.crear(input,clave.getValue(),confirmar.getValue());else service.actualizar(actual.accesoId(),input,clave.getValue(),confirmar.getValue());dialog.close();cargar();notificar("Usuario guardado correctamente.");}catch(RuntimeException ex){notificar(ex.getMessage());}});
        var cerrar=new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->dialog.close());dialog.getFooter().add(guardar);
        if(actual!=null&&actual.activo()&&puede("USUARIO_DESACTIVAR","usuarios.desactivar"))dialog.getFooter().add(new AppActionButton(ActionType.DEACTIVATE,ButtonSize.MAIN,"Desactivar usuario",e->{dialog.close();confirmarDesactivar(actual);}));
        dialog.getFooter().add(cerrar);dialog.open();
    }
    private void ver(UsuarioDto u){var d=new Dialog();d.setHeaderTitle("Detalle del usuario");d.add(new AppDetailSection("Información").field("Usuario",u.usuario()).field("Nombre",u.nombre()+" "+u.apellido()).field("Correo",u.correo()).field("Teléfono",u.telefono()).field("Rol",u.rolNombre()).field("Sucursales",u.sucursalesResumen()).field("Estado",u.activo()?"Activo":"Inactivo"));d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();}
    private void confirmarDesactivar(UsuarioDto u){var c=new ConfirmDialog();c.setHeader("Desactivar usuario");c.setText("¿Desea desactivar el acceso de "+u.usuario()+" a esta empresa?");c.setCancelable(true);c.setCancelText("Cancelar");c.setConfirmText("Desactivar");c.setConfirmButtonTheme("error primary");c.addConfirmListener(e->{try{service.desactivar(u.accesoId());cargar();notificar("Usuario desactivado.");}catch(RuntimeException ex){notificar(ex.getMessage());}});c.open();}
    private static Span estado(boolean activo){var s=new Span(activo?"Activo":"Inactivo");s.getElement().getThemeList().add("badge "+(activo?"success":"contrast"));return s;}
    private static boolean puede(String a,String b){var p=TenantContext.principalActual().permisos();return p.contains(a)||p.contains(b);}
    private static String valor(String v){return v==null?"":v;} private static void notificar(String m){com.vaadin.flow.component.notification.Notification.show(m==null?"No fue posible completar la operación.":m);}
}
