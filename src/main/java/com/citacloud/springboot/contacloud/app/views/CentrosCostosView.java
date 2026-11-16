package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.services.CentroCostoService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.util.Objects;
import java.util.UUID;

@Route(value="centros-costos",layout=MainLayout.class)
@PageTitle("Centros de costos | ContaCloud")
@PermitAll
public class CentrosCostosView extends VerticalLayout implements BeforeEnterObserver {
    private enum Estado {
        TODOS("Todos",null), ACTIVO("Activo",true), INACTIVO("Inactivo",false);
        final String etiqueta;
        final Boolean activo;
        Estado(String etiqueta,Boolean activo) { this.etiqueta=etiqueta;this.activo=activo; }
    }

    private final CentroCostoService servicio;
    private final ModuleAuthorization autorizacion;
    private final Select<Estado> estado=new Select<>();
    private final TextField buscar=new TextField("Buscar");
    private final Grid<Centro> grid=new Grid<>();
    private final Span vacio=new Span();
    private final AppPagination pagina=new AppPagination(p->cargar());
    private final AppActionButton nuevo;

    public CentrosCostosView(CentroCostoService servicio,ModuleAuthorization autorizacion) {
        this.servicio=servicio;
        this.autorizacion=autorizacion;
        addClassNames("cc-page","cc-cost-center-page");
        setPadding(false);
        setSpacing(false);
        nuevo=boton(ActionType.NEW,"Nuevo centro de costos",()->editar(null,null));
        nuevo.setVisible(false);
        estado.setLabel("Estado");
        estado.setItems(Estado.values());
        estado.setItemLabelGenerator(e->e.etiqueta);
        estado.setValue(Estado.TODOS);
        buscar.setPlaceholder("Código o nombre");
        buscar.setMaxLength(150);
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        estado.addValueChangeListener(e->{if(e.isFromClient()) reiniciar();});
        buscar.addValueChangeListener(e->{if(e.isFromClient()) reiniciar();});
        var filtros=new Div(estado,buscar);
        filtros.addClassName("cc-cost-center-fields");

        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addClassName("cc-cost-center-grid");
        grid.addColumn(c->c.referencia().codigo()).setHeader("Código").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(c->c.referencia().nombre()).setHeader("Centro de costos").setFlexGrow(3);
        grid.addColumn(c->c.padre()==null?"—":c.padre().etiqueta()).setHeader("Centro padre").setFlexGrow(2);
        grid.addComponentColumn(CentrosCostosView::estado).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(c->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver centro de costos",
            e->ver(c.referencia().id()))).setHeader("Acc.").setWidth("64px").setFlexGrow(0).setFrozenToEnd(true)
            .setPartNameGenerator(c->"cost-center-action").setTextAlign(ColumnTextAlign.CENTER);
        vacio.getElement().setAttribute("role","status");
        vacio.addClassNames("cc-card","cc-general-ledger-card");
        add(new AppPageHeader("CENTROS DE COSTOS",null,nuevo),filtros,vacio,grid,pagina);
    }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if(!puede("ver")) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        nuevo.setVisible(puede("crear"));
        cargar();
    }

    private void reiniciar() { pagina.reset(); cargar(); }

    private void cargar() {
        ejecutar("No fue posible consultar los centros de costos.",()->{
            var p=pagina.currentRequest();
            var r=servicio.listar(new Filtro(estado.getValue().activo,buscar.getValue()),p.page(),p.size());
            grid.setItems(r.centros());
            grid.setVisible(!r.centros().isEmpty());
            vacio.setVisible(r.total()==0);
            vacio.setText(estado.getValue()==Estado.TODOS&&buscar.isEmpty()
                ?"Todavía no hay centros de costos.":"No se encontraron centros de costos para estos filtros.");
            pagina.setVisible(r.total()>0);
            pagina.setTotal(r.total());
        });
    }

    private void ver(UUID id) {
        ejecutar("No fue posible abrir el centro de costos.",()->{
            var c=servicio.detalle(id);
            var r=c.referencia();
            var modal=dialogo("CENTRO DE COSTOS — "+r.codigo());
            modal.add(new AppDetailSection("INFORMACIÓN GENERAL").field("Código",r.codigo()).field("Nombre",r.nombre())
                .field("Centro padre",c.padre()==null?"Ninguno":c.padre().etiqueta()).badge("Estado",estado(c))
                .field("Descripción",Objects.toString(c.descripcion(),"—")));
            if(puede("editar")) modal.getFooter().add(boton(ActionType.EDIT,"Editar",()->editar(c,modal)));
            if(puede(r.activo()?"desactivar":"activar")) modal.getFooter().add(
                boton(r.activo()?ActionType.DEACTIVATE:ActionType.ACTIVATE,r.activo()?"Desactivar":"Activar",()->confirmarEstado(c,modal)));
            modal.getFooter().add(boton(ActionType.CLOSE,"Cerrar",modal::close));
            modal.open();
        });
    }

    private void editar(Centro previo,Dialog origen) {
        var modal=dialogo(previo==null?"NUEVO CENTRO DE COSTOS":"EDITAR CENTRO DE COSTOS — "+previo.referencia().codigo());
        var nombre=new TextField("Nombre");
        nombre.setMaxLength(160);
        nombre.setRequiredIndicatorVisible(true);
        var padre=new ComboBox<Referencia>("Centro padre");
        padre.setItemLabelGenerator(Referencia::etiqueta);
        padre.setPlaceholder("Ninguno");
        padre.setClearButtonVisible(true);
        padre.setItems(q->servicio.padres(q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());
        var descripcion=new TextArea("Descripción");
        descripcion.setWidthFull();
        descripcion.setMaxLength(2000);
        if(previo!=null) {
            nombre.setValue(previo.referencia().nombre());
            padre.setValue(previo.padre());
            descripcion.setValue(Objects.toString(previo.descripcion(),""));
        }
        var campos=new Div(nombre,padre);
        campos.addClassName("cc-cost-center-fields");
        modal.add(campos,descripcion);
        var clave=UUID.randomUUID();
        modal.getFooter().add(boton(ActionType.SAVE,"Guardar",()->ejecutar("No fue posible guardar el centro de costos.",()->{
            if(nombre.getValue().isBlank()) {
                nombre.setErrorMessage("Indica el nombre.");
                nombre.setInvalid(true);
                nombre.focus();
                return;
            }
            var entrada=new Entrada(nombre.getValue(),padre.isEmpty()?null:padre.getValue().id(),descripcion.getValue());
            UUID id=previo==null?servicio.crear(entrada,clave):previo.referencia().id();
            if(previo!=null) servicio.editar(id,previo.version(),entrada);
            modal.close();
            reiniciar();
            ver(id);
        })),boton(ActionType.CANCEL,"Cancelar",()->{
            modal.close();
            if(origen!=null) origen.open();
        }));
        if(origen!=null) origen.close();
        modal.open();
        nombre.focus();
    }

    private void confirmarEstado(Centro c,Dialog detalle) {
        var r=c.referencia();
        var verbo=r.activo()?"Desactivar":"Activar";
        var dialogo=new ConfirmDialog();
        dialogo.setHeader(verbo+" centro de costos");
        dialogo.setText(r.activo()?r.etiqueta()+" quedará inactivo para nuevas imputaciones.":r.etiqueta()+" volverá a estar disponible.");
        dialogo.setCancelable(true);
        dialogo.setCancelText("Cancelar");
        dialogo.setConfirmText(verbo);
        dialogo.setConfirmButtonTheme(r.activo()?"error primary":"success primary");
        dialogo.addConfirmListener(e->ejecutar("No fue posible cambiar el estado del centro de costos.",()->{
            if(r.activo()) servicio.desactivar(r.id(),c.version());
            else servicio.activar(r.id(),c.version());
            detalle.close();
            reiniciar();
            ver(r.id());
        }));
        dialogo.open();
    }

    private static Dialog dialogo(String titulo) {
        var d=new Dialog(titulo);
        d.setWidth("min(680px, 94vw)");
        d.setCloseOnOutsideClick(false);
        return d;
    }

    private static AppStatusBadge estado(Centro c) {
        return new AppStatusBadge(c.referencia().activo()?"Activo":"Inactivo",
            c.referencia().activo()?AppStatusBadge.Variant.SUCCESS:AppStatusBadge.Variant.NEUTRAL);
    }

    private static AppActionButton boton(ActionType accion,String titulo,Runnable operacion) {
        return new AppActionButton(accion,ButtonSize.MAIN,titulo,e->operacion.run());
    }

    private boolean puede(String accion) { return autorizacion.canAccess("CONTABILIDAD","centros_costos."+accion); }

    private static void ejecutar(String mensaje,Runnable operacion) {
        try {
            operacion.run();
        } catch(RuntimeException ex) {
            UiErrorHandler.show(ex,mensaje);
        }
    }
}
