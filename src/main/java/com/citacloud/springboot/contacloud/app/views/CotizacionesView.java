package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.UI;
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
import com.vaadin.flow.server.*;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="cotizaciones",layout=MainLayout.class)
@PageTitle("Cotizaciones | ContaCloud")
@PermitAll
public class CotizacionesView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CotizacionService service; private final CotizacionCalculoService calculos; private final EmpresaModuloService modules;
    private final TextField search=new TextField("Buscar"); private final DatePicker from=new DatePicker("Desde"),to=new DatePicker("Hasta");
    private final Select<EstadoCotizacion> status=new Select<>(); private final ComboBox<CotizacionCatalogosDto.ClienteOpcion> customer=new ComboBox<>("Cliente");
    private final ComboBox<CotizacionCatalogosDto.MonedaOpcion> currency=new ComboBox<>("Moneda");
    private final AppGrid<CotizacionDto> grid=new AppGrid<>(CotizacionDto.class); private final AppPagination pagination;

    public CotizacionesView(CotizacionService service,CotizacionCalculoService calculos,EmpresaModuloService modules){
        this.service=service;this.calculos=calculos;this.modules=modules;addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        Component create=can("cotizaciones.crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nueva cotización",e->form(null)):null;
        add(create==null?new AppPageHeader("COTIZACIONES","Administra las ofertas comerciales realizadas a clientes."):
            new AppPageHeader("COTIZACIONES","Administra las ofertas comerciales realizadas a clientes.",create));
        search.setPlaceholder("Número, cliente, referencia o producto...");search.setClearButtonVisible(true);search.setValueChangeMode(ValueChangeMode.LAZY);search.setValueChangeTimeout(400);
        status.setLabel("Estado");status.setItems(EstadoCotizacion.values());status.setItemLabelGenerator(CotizacionesView::state);status.setEmptySelectionAllowed(true);status.setEmptySelectionCaption("Todos");
        customer.setItemLabelGenerator(CotizacionesView::customerLabel);customer.setClearButtonVisible(true);customer.setItems(query->service.buscarClientes(query.getFilter().orElse(""),query.getOffset(),query.getLimit()).stream());
        currency.setItemLabelGenerator(x->x.codigo()+" · "+x.nombre());currency.setClearButtonVisible(true);
        search.addValueChangeListener(e->{if(e.isFromClient())reset();});from.addValueChangeListener(e->{if(e.isFromClient())reset();});to.addValueChangeListener(e->{if(e.isFromClient())reset();});
        status.addValueChangeListener(e->{if(e.isFromClient())reset();});customer.addValueChangeListener(e->{if(e.isFromClient())reset();});currency.addValueChangeListener(e->{if(e.isFromClient())reset();});
        HorizontalLayout filters=new HorizontalLayout(search,status,from,to,customer,currency);filters.addClassName("cc-filter-bar");filters.setAlignItems(Alignment.END);
        configureGrid();pagination=new AppPagination(r->load());add(filters,grid,pagination);
    }

    @Override public void beforeEnter(BeforeEnterEvent event){if(!modules.habilitado("VENTAS")||!can("cotizaciones.ver")){Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}
        try{currency.setItems(service.catalogos().monedas());load();}catch(RuntimeException ex){error(ex,"No fue posible cargar las cotizaciones.");}}

    private void configureGrid(){grid.addColumn(CotizacionDto::numero).setHeader("Número").setAutoWidth(true);
        grid.addColumn(c->date(c.fecha())).setHeader("Fecha").setAutoWidth(true);grid.addColumn(CotizacionDto::clienteNombre).setHeader("Cliente").setFlexGrow(1);
        grid.addColumn(c->date(c.validaHasta())).setHeader("Válida hasta").setAutoWidth(true);grid.addColumn(CotizacionDto::monedaCodigo).setHeader("Moneda").setAutoWidth(true);
        grid.addColumn(c->money(c.total())).setHeader("Total").setAutoWidth(true);grid.addComponentColumn(c->badge(c.estado())).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(c->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver cotización",e->detail(c.id()))).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No hay cotizaciones registradas.");}
    private void load(){try{var r=pagination.currentRequest();var page=service.buscar(search.getValue(),from.getValue(),to.getValue(),status.getValue(),
        customer.getValue()==null?null:customer.getValue().id(),currency.getValue()==null?null:currency.getValue().id(),r.page(),r.size());grid.setItems(page.getContent());pagination.setTotal(page.getTotalElements());
        }catch(RuntimeException ex){error(ex,"No fue posible cargar las cotizaciones.");}}
    private void reset(){pagination.reset();load();}

    private void form(UUID id){try{CotizacionDto current=id==null?null:service.obtener(id);if(current!=null&&current.estado()!=EstadoCotizacion.BORRADOR){Notification.show("Solo se pueden editar cotizaciones en borrador.");return;}
        CotizacionCatalogosDto catalogs=service.catalogos();Dialog dialog=dialog(current==null?"NUEVA COTIZACIÓN":"EDITAR COTIZACIÓN");
        TextField number=new TextField("Número");number.setReadOnly(true);number.setValue(current==null?"Se asignará al guardar":current.numero());
        DatePicker quoteDate=new DatePicker("Fecha");quoteDate.setRequiredIndicatorVisible(true);DatePicker validUntil=new DatePicker("Válida hasta");validUntil.setRequiredIndicatorVisible(true);
        ComboBox<CotizacionCatalogosDto.ClienteOpcion> client=new ComboBox<>("Cliente");client.setItems(q->service.buscarClientes(q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());client.setItemLabelGenerator(CotizacionesView::customerLabel);client.setRequiredIndicatorVisible(true);
        ComboBox<CotizacionCatalogosDto.SucursalOpcion> branch=new ComboBox<>("Sucursal");branch.setItems(catalogs.sucursales());branch.setItemLabelGenerator(CotizacionCatalogosDto.SucursalOpcion::nombre);branch.setRequiredIndicatorVisible(true);
        ComboBox<CotizacionCatalogosDto.MonedaOpcion> money=new ComboBox<>("Moneda");money.setItems(catalogs.monedas());money.setItemLabelGenerator(x->x.codigo()+" · "+x.nombre());money.setRequiredIndicatorVisible(true);
        TextField reference=new TextField("Referencia");reference.setMaxLength(100);TextArea conditions=new TextArea("Observación / condiciones");conditions.setMaxLength(2000);conditions.addClassName("cc-dialog-span-2");
        List<LineDraft> lines=new ArrayList<>();Grid<LineDraft> lineGrid=new Grid<>();lineGrid.setWidthFull();lineGrid.setAllRowsVisible(true);AppPagination[] linePagination=new AppPagination[1];
        Span subtotal=new Span(),discountTotal=new Span(),taxTotal=new Span(),grandTotal=new Span();Runnable totals=()->{var t=totals(lines);subtotal.setText(money(t.subtotal()));discountTotal.setText(money(t.descuento()));taxTotal.setText(money(t.impuesto()));grandTotal.setText(money(t.total()));};
        Runnable refreshLines=()->{paginate(lineGrid,lines,linePagination[0]);totals.run();};
        lineGrid.addColumn(l->l.product().codigo()+" · "+l.product().nombre()).setHeader("Producto / Servicio").setFlexGrow(1);lineGrid.addColumn(LineDraft::description).setHeader("Descripción").setFlexGrow(1);
        lineGrid.addColumn(l->unit(l.product().unidad(),l.product().abreviatura())).setHeader("Unidad").setAutoWidth(true);lineGrid.addColumn(l->number(l.quantity())).setHeader("Cantidad").setAutoWidth(true);
        lineGrid.addColumn(l->money(l.unitPrice())).setHeader("Precio").setAutoWidth(true);lineGrid.addColumn(l->money(l.calculated().descuento())).setHeader("Descuento").setAutoWidth(true);
        lineGrid.addColumn(l->l.tax()==null?"—":l.tax().nombre()).setHeader("Impuesto").setAutoWidth(true);lineGrid.addColumn(l->money(l.calculated().total())).setHeader("Total").setAutoWidth(true);
        lineGrid.addComponentColumn(l->{HorizontalLayout a=actions();a.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar línea",e->lineForm(catalogs,l,lines,refreshLines)),
            new AppActionButton(ActionType.DELETE,ButtonSize.GRID_ACTION,"Eliminar línea",e->{lines.remove(l);refreshLines.run();}));return a;}).setHeader("Acciones").setAutoWidth(true);
        linePagination[0]=new AppPagination(r->refreshLines.run(),5,5,10,25,50);AppActionButton addLine=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Agregar línea",e->lineForm(catalogs,null,lines,refreshLines));
        Div lineSection=tableSection("DETALLE",addLine,lineGrid,linePagination[0]);lineSection.addClassName("cc-dialog-span-2");
        if(current==null){LocalDate today=LocalDate.now();quoteDate.setValue(today);validUntil.setValue(today.plusDays(catalogs.diasVigenciaPredeterminados()));if(catalogs.sucursales().size()==1)branch.setValue(catalogs.sucursales().getFirst());selectId(money,catalogs.monedas(),catalogs.monedaBaseId(),CotizacionCatalogosDto.MonedaOpcion::id);}
        else{client.setValue(service.clienteOpcion(current.clienteId()));quoteDate.setValue(current.fecha());validUntil.setValue(current.validaHasta());selectId(branch,catalogs.sucursales(),current.sucursalId(),CotizacionCatalogosDto.SucursalOpcion::id);selectId(money,catalogs.monedas(),current.monedaId(),CotizacionCatalogosDto.MonedaOpcion::id);reference.setValue(value(current.referencia()));conditions.setValue(value(current.condiciones()));
            for(var l:current.lineas()){var product=service.productoOpcion(l.productoId());var tax=catalogs.impuestos().stream().filter(t->Objects.equals(t.id(),l.impuestoId())).findFirst().orElse(l.impuestoId()==null?null:new CotizacionCatalogosDto.ImpuestoOpcion(l.impuestoId(),l.impuestoNombre(),l.tasaImpuesto()));
                var calc=calculos.calcular(new LineaCotizacionInput(product.id(),l.descripcion(),l.cantidad(),l.precioUnitario(),l.tipoDescuento(),l.valorDescuento(),l.impuestoId()),l.impuestoNombre(),l.tasaImpuesto());lines.add(new LineDraft(product,l.descripcion(),l.cantidad(),l.precioUnitario(),l.tipoDescuento(),l.valorDescuento(),tax,calc));}}
        client.addValueChangeListener(e->{if(!e.isFromClient()||e.getValue()==null)return;UUID preferred=e.getValue().monedaId()==null?catalogs.monedaBaseId():e.getValue().monedaId();selectId(money,catalogs.monedas(),preferred,CotizacionCatalogosDto.MonedaOpcion::id);});refreshLines.run();
        Div totalsSection=section("TOTALES",labelValue("Subtotal",subtotal),labelValue("Descuento",discountTotal),labelValue("Impuestos",taxTotal),labelValue("TOTAL",grandTotal));totalsSection.addClassName("cc-dialog-span-2");
        dialog.add(section("INFORMACIÓN GENERAL",number,quoteDate,validUntil,client,branch,money,reference),lineSection,section("OBSERVACIÓN / CONDICIONES",conditions),totalsSection);
        java.util.function.Supplier<CotizacionInput> input=()->new CotizacionInput(client.getValue()==null?null:client.getValue().id(),branch.getValue()==null?null:branch.getValue().id(),quoteDate.getValue(),validUntil.getValue(),money.getValue()==null?null:money.getValue().id(),reference.getValue(),conditions.getValue(),
            lines.stream().map(l->new LineaCotizacionInput(l.product().id(),l.description(),l.quantity(),l.unitPrice(),l.discountType(),l.discountValue(),l.tax()==null?null:l.tax().id())).toList(),current==null?null:current.version());
        AppActionButton save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",null);save.addClickListener(e->{save.setEnabled(false);try{if(current==null)service.crear(input.get());else service.actualizar(current.id(),input.get());dialog.close();load();Notification.show(current==null?"Cotización creada correctamente.":"Cotización actualizada correctamente.");}catch(RuntimeException ex){save.setEnabled(true);error(ex,"No fue posible guardar la cotización.");}});
        dialog.getFooter().add(save,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->dialog.close()));dialog.open();
    }catch(RuntimeException ex){error(ex,"No fue posible abrir la cotización.");}}

    private void lineForm(CotizacionCatalogosDto catalogs,LineDraft current,List<LineDraft> lines,Runnable refresh){Dialog d=dialog(current==null?"AGREGAR LÍNEA":"EDITAR LÍNEA");
        ComboBox<CotizacionCatalogosDto.ProductoOpcion> product=new ComboBox<>("Producto / Servicio");product.setItems(q->service.buscarProductos(q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());product.setItemLabelGenerator(x->x.codigo()+" · "+x.nombre());product.setRequiredIndicatorVisible(true);
        TextArea description=new TextArea("Descripción");description.setRequiredIndicatorVisible(true);description.setMaxLength(500);BigDecimalField quantity=new BigDecimalField("Cantidad");quantity.setRequiredIndicatorVisible(true);
        BigDecimalField price=new BigDecimalField("Precio unitario");price.setRequiredIndicatorVisible(true);price.setReadOnly(!can("cotizaciones.precios"));Select<TipoDescuentoCotizacion> discountType=new Select<>();discountType.setLabel("Tipo de descuento");discountType.setItems(TipoDescuentoCotizacion.values());discountType.setItemLabelGenerator(t->t==TipoDescuentoCotizacion.MONTO?"Monto":"Porcentaje");discountType.setReadOnly(!can("cotizaciones.descuentos"));
        BigDecimalField discount=new BigDecimalField("Descuento");discount.setReadOnly(!can("cotizaciones.descuentos"));ComboBox<CotizacionCatalogosDto.ImpuestoOpcion> tax=new ComboBox<>("Impuesto");tax.setItems(catalogs.impuestos());tax.setItemLabelGenerator(x->x.nombre()+" ("+number(x.tasa())+"%)");tax.setClearButtonVisible(true);
        if(current==null){quantity.setValue(BigDecimal.ONE);price.setValue(BigDecimal.ZERO);discountType.setValue(TipoDescuentoCotizacion.MONTO);discount.setValue(BigDecimal.ZERO);}else{product.setValue(current.product());description.setValue(current.description());quantity.setValue(current.quantity());price.setValue(current.unitPrice());discountType.setValue(current.discountType());discount.setValue(current.discountValue());tax.setValue(current.tax());}
        product.addValueChangeListener(e->{if(!e.isFromClient()||e.getValue()==null)return;var selected=e.getValue();description.setValue(blank(selected.descripcion())?selected.nombre():selected.descripcion());price.setValue(selected.precioVenta()==null?BigDecimal.ZERO:selected.precioVenta());selectId(tax,catalogs.impuestos(),selected.impuestoVentaId(),CotizacionCatalogosDto.ImpuestoOpcion::id);});
        d.add(section("INFORMACIÓN DE LA LÍNEA",product,description,quantity,price,discountType,discount,tax));AppActionButton save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar línea",e->{try{if(product.getValue()==null)throw new ReglaNegocioException("Selecciona un producto o servicio.");
            if(lines.stream().anyMatch(l->l!=current&&Objects.equals(l.product().id(),product.getValue().id())))throw new ReglaNegocioException("El producto ya existe en la cotización.");var selected=tax.getValue();var input=new LineaCotizacionInput(product.getValue().id(),description.getValue(),quantity.getValue(),price.getValue(),discountType.getValue(),discount.getValue(),selected==null?null:selected.id());
            var calc=calculos.calcular(input,selected==null?null:selected.nombre(),selected==null?BigDecimal.ZERO:selected.tasa());var draft=new LineDraft(product.getValue(),calc.descripcion(),calc.cantidad(),calc.precioUnitario(),calc.tipoDescuento(),calc.valorDescuento(),selected,calc);if(current==null)lines.add(draft);else lines.set(lines.indexOf(current),draft);refresh.run();d.close();
            }catch(RuntimeException ex){error(ex,"No fue posible guardar la línea.");}});d.getFooter().add(save,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}

    private void detail(UUID id){try{CotizacionDto c=service.obtener(id);Dialog d=dialog("COTIZACIÓN "+c.numero()+" · "+state(c.estado()));
        HorizontalLayout summary=new HorizontalLayout(new AppDetailSection("INFORMACIÓN").field("Fecha",date(c.fecha())).field("Válida hasta",date(c.validaHasta())).field("Sucursal",display(c.sucursalNombre())).field("Moneda",display(c.monedaCodigo())).field("Referencia",display(c.referencia())),
            new AppDetailSection("CLIENTE").field("Nombre",c.clienteNombre()).field("RNC / Identificación",display(c.clienteIdentificacion())).field("Dirección",display(c.clienteDireccion())).field("Teléfono",display(c.clienteTelefono())).field("Correo",display(c.clienteCorreo())));summary.setWidthFull();summary.getChildren().forEach(x->x.getElement().getStyle().set("flex","1"));
        d.add(summary,detailLines(c),new AppDetailSection("TOTALES").field("Subtotal",money(c.subtotal())).field("Descuento",money(c.descuento())).field("Impuestos",money(c.impuesto())).field("Total",money(c.total())));
        if(!blank(c.condiciones()))d.add(new AppDetailSection("OBSERVACIÓN / CONDICIONES").field("Detalle",c.condiciones()));if(!c.eventos().isEmpty())d.add(history(c));
        if(c.estado()==EstadoCotizacion.BORRADOR&&can("cotizaciones.editar"))d.getFooter().add(new AppActionButton(ActionType.EDIT,ButtonSize.MAIN,"Editar cotización",e->{d.close();form(c.id());}));
        if(c.estado()==EstadoCotizacion.BORRADOR&&can("cotizaciones.enviar"))d.getFooter().add(new AppActionButton(ActionType.ISSUE,ButtonSize.MAIN,"Marcar como enviada",e->confirmSend(c,d)));
        if(c.estado()==EstadoCotizacion.ENVIADA&&can("cotizaciones.aceptar"))d.getFooter().add(new AppActionButton(ActionType.ACCEPT,ButtonSize.MAIN,"Aceptar cotización",e->acceptForm(c,d)));
        if(c.estado()==EstadoCotizacion.ENVIADA&&can("cotizaciones.rechazar"))d.getFooter().add(new AppActionButton(ActionType.REJECT,ButtonSize.MAIN,"Rechazar cotización",e->reasonForm(c,d,false)));
        if(c.estado()==EstadoCotizacion.ACEPTADA&&can("cotizaciones.convertir"))d.getFooter().add(new AppActionButton(ActionType.CREATE_INVOICE,ButtonSize.MAIN,"Crear factura",e->createInvoice(c,d)));
        if(can("cotizaciones.duplicar"))d.getFooter().add(new AppActionButton(ActionType.DUPLICATE,ButtonSize.MAIN,"Duplicar cotización",e->duplicate(c,d)));
        if(can("cotizaciones.imprimir"))d.getFooter().add(new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Imprimir",e->openPdf(c)));
        if(c.estado()!=EstadoCotizacion.ANULADA&&c.estado()!=EstadoCotizacion.CONVERTIDA&&can("cotizaciones.anular"))d.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular cotización",e->reasonForm(c,d,true)));
        d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
    }catch(RuntimeException ex){error(ex,"No fue posible consultar la cotización.");}}

    private Component detailLines(CotizacionDto c){AppGrid<LineaCotizacionDto> g=new AppGrid<>(LineaCotizacionDto.class);g.setWidthFull();g.addColumn(LineaCotizacionDto::numero).setHeader("#").setAutoWidth(true);g.addColumn(LineaCotizacionDto::descripcion).setHeader("Descripción").setFlexGrow(1);g.addColumn(l->unit(l.unidadMedida(),l.unidadMedidaAbreviatura())).setHeader("Unidad").setAutoWidth(true);g.addColumn(l->number(l.cantidad())).setHeader("Cantidad");g.addColumn(l->money(l.precioUnitario())).setHeader("Precio");g.addColumn(l->money(l.descuento())).setHeader("Descuento");g.addColumn(l->display(l.impuestoNombre())).setHeader("Impuesto");g.addColumn(l->money(l.total())).setHeader("Total");AppPagination[] p=new AppPagination[1];Runnable refresh=()->paginate(g,c.lineas(),p[0]);p[0]=new AppPagination(r->refresh.run(),5,5,10,25,50);refresh.run();return tableSection("DETALLE",null,g,p[0]);}
    private Component history(CotizacionDto c){AppGrid<EventoCotizacionDto> g=new AppGrid<>(EventoCotizacionDto.class);g.setAllRowsVisible(true);g.addColumn(e->e.ocurridoEn()==null?"—":DATE_TIME.format(e.ocurridoEn())).setHeader("Fecha / hora").setAutoWidth(true);g.addColumn(e->event(e.tipo())).setHeader("Evento").setAutoWidth(true);g.addColumn(e->display(e.detalle())).setHeader("Detalle").setFlexGrow(1);g.setItems(c.eventos());return tableSection("HISTORIAL",null,g);}
    private void confirmSend(CotizacionDto c,Dialog parent){new ConfirmDialog("Enviar cotización","Después de enviarla no podrá editarse. ¿Deseas continuar?","Enviar",e->{try{service.enviar(c.id(),c.version());parent.close();load();Notification.show("Cotización marcada como enviada.");}catch(RuntimeException ex){error(ex,"No fue posible enviar la cotización.");}},"Cancelar",e->{}).open();}
    private void acceptForm(CotizacionDto c,Dialog parent){Dialog d=dialog("ACEPTAR COTIZACIÓN");TextArea note=new TextArea("Nota (opcional)");note.setMaxLength(500);d.add(note);AppActionButton confirm=new AppActionButton(ActionType.ACCEPT,ButtonSize.MAIN,"Aceptar",e->{try{service.aceptar(c.id(),c.version(),note.getValue());d.close();parent.close();load();Notification.show("Cotización aceptada correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible aceptar la cotización.");}});d.getFooter().add(confirm,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}
    private void reasonForm(CotizacionDto c,Dialog parent,boolean voiding){Dialog d=dialog(voiding?"ANULAR COTIZACIÓN":"RECHAZAR COTIZACIÓN");TextArea reason=new TextArea(voiding?"Motivo de anulación":"Motivo de rechazo");reason.setRequiredIndicatorVisible(true);reason.setMaxLength(500);d.add(reason);AppActionButton confirm=new AppActionButton(voiding?ActionType.VOID:ActionType.REJECT,ButtonSize.MAIN,voiding?"Anular":"Rechazar",e->{try{if(voiding)service.anular(c.id(),c.version(),reason.getValue());else service.rechazar(c.id(),c.version(),reason.getValue());d.close();parent.close();load();Notification.show(voiding?"Cotización anulada correctamente.":"Cotización rechazada correctamente.");}catch(RuntimeException ex){error(ex,voiding?"No fue posible anular la cotización.":"No fue posible rechazar la cotización.");}});d.getFooter().add(confirm,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}
    private void duplicate(CotizacionDto c,Dialog parent){new ConfirmDialog("Duplicar cotización","Se creará una nueva cotización en borrador con fecha y vigencia actuales.","Duplicar",e->{try{CotizacionDto copy=service.duplicar(c.id());parent.close();load();Notification.show("Cotización "+copy.numero()+" creada correctamente.");detail(copy.id());}catch(RuntimeException ex){error(ex,"No fue posible duplicar la cotización.");}},"Cancelar",e->{}).open();}
    private void createInvoice(CotizacionDto c,Dialog parent){try{UUID id=service.prepararFactura(c.id(),c.version());parent.close();UI.getCurrent().getPage().setLocation("facturas?cotizacionId="+id);}catch(RuntimeException ex){error(ex,"No fue posible preparar la factura.");}}
    private void openPdf(CotizacionDto c){try{byte[] bytes=service.generarPdf(c.id());StreamResource resource=new StreamResource("cotizacion-"+c.numero()+".pdf",()->new ByteArrayInputStream(bytes));resource.setContentType("application/pdf");resource.setCacheTime(0);var registration=VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);UI.getCurrent().getPage().open(registration.getResourceUri().toString(),"_blank");}catch(RuntimeException ex){error(ex,"No fue posible abrir el PDF de la cotización.");}}

    private static CotizacionCalculoService.Totales totals(List<LineDraft> lines){BigDecimal s=BigDecimal.ZERO,d=BigDecimal.ZERO,t=BigDecimal.ZERO,total=BigDecimal.ZERO;for(var l:lines){s=s.add(l.calculated().subtotalBruto());d=d.add(l.calculated().descuento());t=t.add(l.calculated().impuesto());total=total.add(l.calculated().total());}return new CotizacionCalculoService.Totales(s,d,t,total);}
    private static Div section(String title,Component...components){Div d=new Div();d.addClassName("cc-form-section");H3 h=new H3(title);h.addClassName("cc-section-heading");Div body=new Div(components);body.addClassName("cc-dialog-form");d.add(h,body);return d;}
    private static Div tableSection(String title,Component action,Component...content){Div d=new Div();d.addClassNames("cc-form-section","cc-table-section");H3 h=new H3(title);h.addClassName("cc-section-heading");Div header=new Div();header.addClassName("cc-table-section-header");header.add(h);if(action!=null)header.add(action);Div body=new Div(content);body.addClassName("cc-table-section-body");d.add(header,body);return d;}
    private static Div labelValue(String label,Span value){Div d=new Div(new Span(label),value);d.addClassName("cc-quote-total");return d;}
    private static <T> void paginate(Grid<T> grid,List<T> items,AppPagination pagination){if(pagination==null)return;var r=pagination.currentRequest();int from=Math.min(r.page()*r.size(),items.size());int to=Math.min(from+r.size(),items.size());grid.setItems(items.subList(from,to));pagination.setTotal(items.size());}
    private static Dialog dialog(String title){Dialog d=new Dialog();d.setHeaderTitle(title);d.setWidth("min(1200px, 96vw)");return d;}
    private static HorizontalLayout actions(){HorizontalLayout h=new HorizontalLayout();h.addClassName("cc-grid-actions");h.setSpacing(false);h.setPadding(false);return h;}
    private static Span badge(EstadoCotizacion s){Span x=new Span(state(s));x.getElement().getThemeList().add("badge "+switch(s){case BORRADOR->"contrast";case ENVIADA->"primary";case ACEPTADA,CONVERTIDA->"success";case RECHAZADA,VENCIDA,ANULADA->"error";});return x;}
    static String state(EstadoCotizacion s){if(s==null)return "Todos";return switch(s){case BORRADOR->"Borrador";case ENVIADA->"Enviada";case ACEPTADA->"Aceptada";case RECHAZADA->"Rechazada";case VENCIDA->"Vencida";case ANULADA->"Anulada";case CONVERTIDA->"Convertida";};}
    private static String event(String s){return s==null?"—":s.charAt(0)+s.substring(1).toLowerCase(Locale.ROOT);}
    private static String customerLabel(CotizacionCatalogosDto.ClienteOpcion c){return c.codigo()+" · "+c.nombre()+(blank(c.identificacion())?"":" · "+c.identificacion());}
    private static String money(BigDecimal v){return new DecimalFormat("#,##0.00").format(v==null?BigDecimal.ZERO:v);}private static String number(BigDecimal v){return v==null?"0":v.stripTrailingZeros().toPlainString();}
    private static String date(LocalDate v){return v==null?"—":DATE.format(v);}private static String display(String v){return blank(v)?"—":v;}private static String value(String v){return v==null?"":v;}private static boolean blank(String v){return v==null||v.isBlank();}private static String unit(String n,String a){return blank(n)?"—":n+(blank(a)?"":" ("+a+")");}
    private static boolean can(String permission){return TenantContext.principalActual().permisos().contains(permission);}
    private static <T> void selectId(ComboBox<T> field,List<T> values,UUID id,java.util.function.Function<T,UUID> getter){field.setValue(id==null?null:values.stream().filter(v->Objects.equals(getter.apply(v),id)).findFirst().orElse(null));}
    private static void error(RuntimeException ex,String fallback){String m=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():fallback;Notification.show(blank(m)?fallback:m);}
    private record LineDraft(CotizacionCatalogosDto.ProductoOpcion product,String description,BigDecimal quantity,BigDecimal unitPrice,
        TipoDescuentoCotizacion discountType,BigDecimal discountValue,CotizacionCatalogosDto.ImpuestoOpcion tax,
        CotizacionCalculoService.LineaCalculada calculated) {}
}
