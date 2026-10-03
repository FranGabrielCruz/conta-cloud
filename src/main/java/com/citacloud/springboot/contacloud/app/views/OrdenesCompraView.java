package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.EstadoOrdenCompra;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="ordenes-compra",layout=MainLayout.class)
@PageTitle("Órdenes de compra | ContaCloud")
@PermitAll
public class OrdenesCompraView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final PurchaseOrderService service; private final PurchaseOrderCalculationService calculations;
    private final EmpresaModuloService modules; private final TextField search=new TextField("Buscar");
    private final DatePicker from=new DatePicker("Desde"),to=new DatePicker("Hasta");
    private final Select<EstadoOrdenCompra> status=new Select<>(); private final AppGrid<OrdenCompraDto> grid=new AppGrid<>(OrdenCompraDto.class);
    private final AppPagination pagination;

    public OrdenesCompraView(PurchaseOrderService service,PurchaseOrderCalculationService calculations,EmpresaModuloService modules){
        this.service=service;this.calculations=calculations;this.modules=modules;addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        Component create=can("ordenes_compra.crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nueva orden de compra",e->form(null)):null;
        add(create==null?new AppPageHeader("ÓRDENES DE COMPRA","Administra las solicitudes de compra realizadas a proveedores."):
            new AppPageHeader("ÓRDENES DE COMPRA","Administra las solicitudes de compra realizadas a proveedores.",create));
        search.setPlaceholder("Número, proveedor o referencia...");search.setClearButtonVisible(true);search.setValueChangeMode(ValueChangeMode.LAZY);search.setValueChangeTimeout(400);
        status.setLabel("Estado");status.setItems(EstadoOrdenCompra.values());status.setItemLabelGenerator(OrdenesCompraView::state);status.setEmptySelectionAllowed(true);status.setEmptySelectionCaption("Todos");
        search.addValueChangeListener(e->{if(e.isFromClient())reset();});from.addValueChangeListener(e->{if(e.isFromClient())reset();});to.addValueChangeListener(e->{if(e.isFromClient())reset();});status.addValueChangeListener(e->{if(e.isFromClient())reset();});
        HorizontalLayout filters=new HorizontalLayout(search,from,to,status);filters.addClassName("cc-filter-bar");filters.setAlignItems(Alignment.END);
        configureGrid();pagination=new AppPagination(r->load());add(filters,grid,pagination);
    }
    @Override public void beforeEnter(BeforeEnterEvent event){if(!modules.habilitado("COMPRAS")||!can("ordenes_compra.ver")){Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}load();}
    private void configureGrid(){grid.addColumn(OrdenCompraDto::numero).setHeader("Número").setAutoWidth(true);
        grid.addColumn(o->date(o.fecha())).setHeader("Fecha").setAutoWidth(true);grid.addColumn(OrdenCompraDto::proveedorNombre).setHeader("Proveedor").setFlexGrow(1);
        grid.addColumn(OrdenCompraDto::monedaCodigo).setHeader("Moneda").setAutoWidth(true);grid.addColumn(o->money(o.total())).setHeader("Total").setAutoWidth(true);
        grid.addComponentColumn(o->badge(o.estado())).setHeader("Estado").setAutoWidth(true);grid.addComponentColumn(o->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver orden",e->detail(o.id()))).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No hay órdenes de compra registradas.");}
    private void load(){try{var r=pagination.currentRequest();var page=service.search(search.getValue(),from.getValue(),to.getValue(),status.getValue(),r.page(),r.size());grid.setItems(page.getContent());pagination.setTotal(page.getTotalElements());}catch(RuntimeException ex){error(ex,"No fue posible cargar las órdenes de compra.");}}
    private void reset(){pagination.reset();load();}

    private void form(UUID id){try{OrdenCompraDto current=id==null?null:service.get(id);if(current!=null&&current.estado()!=EstadoOrdenCompra.DRAFT){Notification.show("Solo se pueden editar órdenes en borrador.");return;}
        OrdenCompraCatalogosDto catalogs=service.catalogs();Dialog dialog=dialog(current==null?"NUEVA ORDEN DE COMPRA":"EDITAR ORDEN DE COMPRA");
        ComboBox<OrdenCompraCatalogosDto.ProveedorOpcion> supplier=new ComboBox<>("Proveedor");supplier.setItems(query->service.searchSupplierOptions(query.getFilter().orElse(""),query.getOffset(),query.getLimit()).stream());supplier.setItemLabelGenerator(x->x.nombre()+(blank(x.identificacion())?"":" · "+x.identificacion()));supplier.setRequiredIndicatorVisible(true);
        DatePicker orderDate=new DatePicker("Fecha");orderDate.setRequiredIndicatorVisible(true);DatePicker delivery=new DatePicker("Entrega esperada");
        ComboBox<OrdenCompraCatalogosDto.SucursalOpcion> branch=new ComboBox<>("Sucursal");branch.setItems(catalogs.sucursales());branch.setItemLabelGenerator(OrdenCompraCatalogosDto.SucursalOpcion::nombre);branch.setRequiredIndicatorVisible(true);
        ComboBox<OrdenCompraCatalogosDto.MonedaOpcion> currency=new ComboBox<>("Moneda");currency.setItems(catalogs.monedas());currency.setItemLabelGenerator(x->x.codigo()+" · "+x.nombre());currency.setRequiredIndicatorVisible(true);
        ComboBox<OrdenCompraCatalogosDto.CondicionOpcion> term=new ComboBox<>("Condición de pago");term.setItems(catalogs.condiciones());term.setItemLabelGenerator(OrdenCompraCatalogosDto.CondicionOpcion::nombre);term.setClearButtonVisible(true);
        TextField reference=new TextField("Referencia");reference.setMaxLength(100);TextArea notes=new TextArea("Notas");notes.setMaxLength(1000);notes.addClassName("cc-dialog-span-2");
        List<LineDraft> lines=new ArrayList<>();Grid<LineDraft> lineGrid=new Grid<>();lineGrid.setAllRowsVisible(true);lineGrid.addColumn(LineDraft::description).setHeader("Descripción").setFlexGrow(1);lineGrid.addColumn(l->l.quantity().stripTrailingZeros().toPlainString()).setHeader("Cantidad").setAutoWidth(true);lineGrid.addColumn(l->money(l.unitPrice())).setHeader("Precio").setAutoWidth(true);lineGrid.addColumn(l->money(l.discount())).setHeader("Descuento").setAutoWidth(true);lineGrid.addColumn(l->l.tax()==null?"—":l.tax().nombre()).setHeader("Impuesto").setAutoWidth(true);lineGrid.addColumn(l->money(l.calculated().total())).setHeader("Total").setAutoWidth(true);
        lineGrid.addComponentColumn(l->{HorizontalLayout a=actions();a.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar línea",e->lineForm(dialog,catalogs,l,lines,lineGrid)),new AppActionButton(ActionType.DELETE,ButtonSize.GRID_ACTION,"Eliminar línea",e->{lines.remove(l);lineGrid.setItems(lines);}));return a;}).setHeader("Acciones").setAutoWidth(true);
        AppActionButton addLine=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Agregar línea",e->lineForm(dialog,catalogs,null,lines,lineGrid));
        Div lineSection=section("DETALLE",addLine,lineGrid);lineSection.addClassName("cc-dialog-span-2");
        if(current==null){orderDate.setValue(LocalDate.now());if(catalogs.sucursales().size()==1)branch.setValue(catalogs.sucursales().getFirst());selectId(currency,catalogs.monedas(),catalogs.monedaBaseId(),OrdenCompraCatalogosDto.MonedaOpcion::id);}
        else{supplier.setValue(service.supplierOption(current.proveedorId()));orderDate.setValue(current.fecha());delivery.setValue(current.fechaEntrega());selectId(branch,catalogs.sucursales(),current.sucursalId(),OrdenCompraCatalogosDto.SucursalOpcion::id);selectId(currency,catalogs.monedas(),current.monedaId(),OrdenCompraCatalogosDto.MonedaOpcion::id);selectId(term,catalogs.condiciones(),current.condicionPagoId(),OrdenCompraCatalogosDto.CondicionOpcion::id);reference.setValue(value(current.referencia()));notes.setValue(value(current.notas()));for(var l:current.lineas()){var tax=catalogs.impuestos().stream().filter(t->Objects.equals(t.id(),l.impuestoId())).findFirst().orElse(l.impuestoId()==null?null:new OrdenCompraCatalogosDto.ImpuestoOpcion(l.impuestoId(),l.impuestoNombre(),l.tasaImpuesto()));lines.add(new LineDraft(l.descripcion(),l.cantidad(),l.precioUnitario(),l.descuento(),tax,calculations.calculate(new LineaOrdenCompraInput(l.descripcion(),l.cantidad(),l.precioUnitario(),l.descuento(),l.impuestoId()),l.impuestoNombre(),l.tasaImpuesto())));}lineGrid.setItems(lines);}
        supplier.addValueChangeListener(e->{if(!e.isFromClient()||e.getValue()==null)return;var p=e.getValue();selectId(term,catalogs.condiciones(),p.condicionPagoId(),OrdenCompraCatalogosDto.CondicionOpcion::id);UUID preferred=p.monedaId()==null?catalogs.monedaBaseId():p.monedaId();selectId(currency,catalogs.monedas(),preferred,OrdenCompraCatalogosDto.MonedaOpcion::id);});
        dialog.add(section("INFORMACIÓN GENERAL",supplier,orderDate,branch,currency,term,delivery,reference,notes),lineSection);
        AppActionButton save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",null);save.addClickListener(e->{save.setEnabled(false);try{List<LineaOrdenCompraInput> inputs=lines.stream().map(l->new LineaOrdenCompraInput(l.description(),l.quantity(),l.unitPrice(),l.discount(),l.tax()==null?null:l.tax().id())).toList();OrdenCompraInput input=new OrdenCompraInput(id(supplier),id(branch),orderDate.getValue(),delivery.getValue(),id(currency),id(term),reference.getValue(),notes.getValue(),inputs,current==null?null:current.version());if(current==null)service.create(input);else service.update(current.id(),input);dialog.close();load();Notification.show(current==null?"Orden creada correctamente.":"Orden actualizada correctamente.");}catch(RuntimeException ex){save.setEnabled(true);error(ex,"No fue posible guardar la orden de compra.");}});
        dialog.getFooter().add(save,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->dialog.close()));dialog.open();
    }catch(RuntimeException ex){error(ex,"No fue posible abrir la orden de compra.");}}

    private void lineForm(Dialog parent,OrdenCompraCatalogosDto catalogs,LineDraft current,List<LineDraft> lines,Grid<LineDraft> grid){Dialog d=dialog(current==null?"AGREGAR LÍNEA":"EDITAR LÍNEA");TextArea description=new TextArea("Descripción");description.setRequiredIndicatorVisible(true);description.setMaxLength(500);BigDecimalField quantity=new BigDecimalField("Cantidad");quantity.setRequiredIndicatorVisible(true);BigDecimalField price=new BigDecimalField("Precio unitario");price.setRequiredIndicatorVisible(true);BigDecimalField discount=new BigDecimalField("Descuento");ComboBox<OrdenCompraCatalogosDto.ImpuestoOpcion> tax=new ComboBox<>("Impuesto");tax.setItems(catalogs.impuestos());tax.setItemLabelGenerator(x->x.nombre()+" ("+x.tasa().stripTrailingZeros().toPlainString()+"%)");tax.setClearButtonVisible(true);if(current==null){quantity.setValue(BigDecimal.ONE);price.setValue(BigDecimal.ZERO);discount.setValue(BigDecimal.ZERO);}else{description.setValue(current.description());quantity.setValue(current.quantity());price.setValue(current.unitPrice());discount.setValue(current.discount());tax.setValue(current.tax());}d.add(section("INFORMACIÓN DE LA LÍNEA",description,quantity,price,discount,tax));AppActionButton save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar línea",e->{try{var selected=tax.getValue();var calculated=calculations.calculate(new LineaOrdenCompraInput(description.getValue(),quantity.getValue(),price.getValue(),discount.getValue(),selected==null?null:selected.id()),selected==null?null:selected.nombre(),selected==null?BigDecimal.ZERO:selected.tasa());LineDraft draft=new LineDraft(calculated.description(),calculated.quantity(),calculated.unitPrice(),calculated.discount(),selected,calculated);if(current==null)lines.add(draft);else lines.set(lines.indexOf(current),draft);grid.setItems(lines);d.close();}catch(RuntimeException ex){error(ex,"No fue posible guardar la línea.");}});d.getFooter().add(save,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}

    private void detail(UUID id){try{OrdenCompraDto o=service.get(id);Dialog d=dialog("ORDEN DE COMPRA "+o.numero());d.add(new AppDetailSection("INFORMACIÓN GENERAL").field("Proveedor",o.proveedorNombre()).field("RNC / Identificación",display(o.proveedorIdentificacion())).field("Fecha",date(o.fecha())).field("Entrega esperada",date(o.fechaEntrega())).field("Sucursal",o.sucursalNombre()).field("Moneda",o.monedaCodigo()).field("Condición de pago",display(o.condicionPagoNombre())).field("Referencia",display(o.referencia())).field("Estado",state(o.estado())),detailLines(o),new AppDetailSection("TOTALES").field("Subtotal",money(o.subtotal())).field("Descuento",money(o.descuento())).field("Impuestos",money(o.impuesto())).field("Total",money(o.total())));
        if(o.estado()==EstadoOrdenCompra.DRAFT&&can("ordenes_compra.editar"))d.getFooter().add(new AppActionButton(ActionType.EDIT,ButtonSize.MAIN,"Editar orden",e->{d.close();form(o.id());}));
        if(o.estado()==EstadoOrdenCompra.DRAFT&&can("ordenes_compra.emitir"))d.getFooter().add(new AppActionButton(ActionType.ISSUE,ButtonSize.MAIN,"Emitir orden",e->confirmIssue(o,d)));
        if((o.estado()==EstadoOrdenCompra.DRAFT||o.estado()==EstadoOrdenCompra.ISSUED)&&can("ordenes_compra.anular"))d.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular orden",e->voidForm(o,d)));
        d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();}catch(RuntimeException ex){error(ex,"No fue posible consultar la orden de compra.");}}
    private Component detailLines(OrdenCompraDto o){AppGrid<LineaOrdenCompraDto> g=new AppGrid<>(LineaOrdenCompraDto.class);g.addColumn(LineaOrdenCompraDto::numero).setHeader("#").setAutoWidth(true);g.addColumn(LineaOrdenCompraDto::descripcion).setHeader("Descripción").setFlexGrow(1);g.addColumn(l->l.cantidad().stripTrailingZeros().toPlainString()).setHeader("Cantidad");g.addColumn(l->money(l.precioUnitario())).setHeader("Precio");g.addColumn(l->money(l.descuento())).setHeader("Descuento");g.addColumn(l->display(l.impuestoNombre())).setHeader("Impuesto");g.addColumn(l->money(l.total())).setHeader("Total");g.setItems(o.lineas());return section("DETALLE",g);}
    private void confirmIssue(OrdenCompraDto o,Dialog parent){ConfirmDialog c=new ConfirmDialog("Emitir orden de compra","Después de emitirla no podrá editarse. ¿Deseas continuar?","Emitir",e->{try{service.issue(o.id(),o.version());parent.close();load();Notification.show("Orden emitida correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible emitir la orden.");}},"Cancelar",e->{});c.open();}
    private void voidForm(OrdenCompraDto o,Dialog parent){Dialog d=dialog("ANULAR ORDEN DE COMPRA");TextArea reason=new TextArea("Motivo de anulación");reason.setRequiredIndicatorVisible(true);reason.setMaxLength(500);d.add(reason);AppActionButton confirm=new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular orden",null);confirm.addClickListener(e->{confirm.setEnabled(false);try{service.voidOrder(o.id(),o.version(),reason.getValue());d.close();parent.close();load();Notification.show("Orden anulada correctamente.");}catch(RuntimeException ex){confirm.setEnabled(true);error(ex,"No fue posible anular la orden.");}});d.getFooter().add(confirm,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}

    private static Div section(String title,Component...components){Div d=new Div();d.addClassName("cc-form-section");H3 h=new H3(title);h.addClassName("cc-section-heading");Div body=new Div(components);body.addClassName("cc-dialog-form");d.add(h,body);return d;}
    private static Dialog dialog(String title){Dialog d=new Dialog();d.setHeaderTitle(title);d.setWidth("min(1000px, 96vw)");return d;}
    private static HorizontalLayout actions(){HorizontalLayout h=new HorizontalLayout();h.addClassName("cc-grid-actions");h.setSpacing(false);h.setPadding(false);return h;}
    private static Span badge(EstadoOrdenCompra s){Span x=new Span(state(s));x.getElement().getThemeList().add("badge "+switch(s){case DRAFT->"contrast";case ISSUED,PARTIALLY_RECEIVED->"primary";case RECEIVED->"success";case VOIDED->"error";});return x;}
    static String state(EstadoOrdenCompra s){if(s==null)return "Todos";return switch(s){case DRAFT->"Borrador";case ISSUED->"Emitida";case VOIDED->"Anulada";case PARTIALLY_RECEIVED->"Recibida parcialmente";case RECEIVED->"Recibida";};}
    private static String money(BigDecimal v){return new DecimalFormat("#,##0.00").format(v==null?BigDecimal.ZERO:v);}private static String date(LocalDate v){return v==null?"—":DATE.format(v);}private static String display(String v){return blank(v)?"—":v;}private static String value(String v){return v==null?"":v;}private static boolean blank(String v){return v==null||v.isBlank();}
    private static boolean can(String permission){return TenantContext.principalActual().permisos().contains(permission);}
    private static <T> void selectId(ComboBox<T> field,List<T> values,UUID id,java.util.function.Function<T,UUID> getter){field.setValue(id==null?null:values.stream().filter(v->Objects.equals(getter.apply(v),id)).findFirst().orElse(null));}
    private static UUID id(ComboBox<? extends Object> field){Object v=field.getValue();if(v==null)return null;if(v instanceof OrdenCompraCatalogosDto.ProveedorOpcion x)return x.id();if(v instanceof OrdenCompraCatalogosDto.SucursalOpcion x)return x.id();if(v instanceof OrdenCompraCatalogosDto.MonedaOpcion x)return x.id();if(v instanceof OrdenCompraCatalogosDto.CondicionOpcion x)return x.id();throw new IllegalArgumentException();}
    private static void error(RuntimeException ex,String fallback){String m=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():fallback;Notification.show(blank(m)?fallback:m);}
    private record LineDraft(String description,BigDecimal quantity,BigDecimal unitPrice,BigDecimal discount,OrdenCompraCatalogosDto.ImpuestoOpcion tax,PurchaseOrderCalculationService.CalculatedLine calculated){}
}
