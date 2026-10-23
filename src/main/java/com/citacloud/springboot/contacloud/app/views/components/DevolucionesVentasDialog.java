package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.services.DevolucionVentaService;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.math.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Flujo independiente de devolución; no comparte ni modifica el carrito activo del POS. */
public class DevolucionesVentasDialog extends Dialog {
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final DevolucionVentaService service; private final Consumer<UUID> abrirPdf; private final Runnable alCerrar;
    private final VerticalLayout cuerpo=new VerticalLayout(); private final TextField buscar=new TextField("Buscar factura o cliente");
    private final DatePicker desde=new DatePicker("Desde"),hasta=new DatePicker("Hasta");
    private final ComboBox<EstadoDevolucionFacturaFiltro> estado=new ComboBox<>("Estado");
    private final Grid<FacturaDevolucionResumenDto> facturas=new Grid<>(); private final AppPagination paginacion;
    private DevolucionVentaDetalleDto detalle; private final Map<UUID,BigDecimalField> cantidades=new LinkedHashMap<>();
    private UUID claveOperacion=UUID.randomUUID(); private Button confirmar;

    public DevolucionesVentasDialog(DevolucionVentaService service,Consumer<UUID> abrirPdf,Runnable alCerrar){
        this.service=service;this.abrirPdf=abrirPdf;this.alCerrar=alCerrar;setHeaderTitle("DEVOLUCIONES DE VENTAS");
        addClassName("cc-sales-return-dialog");setWidth("min(1180px, 96vw)");setMaxHeight("94vh");setModal(true);setDraggable(false);
        cuerpo.setPadding(false);cuerpo.setSpacing(true);cuerpo.setWidthFull();paginacion=new AppPagination(req->cargarFacturas(),10,10);
        configurarBusqueda();mostrarBusqueda();add(cuerpo);addDialogCloseActionListener(e->cerrar());
    }

    private void configurarBusqueda(){
        buscar.setPlaceholder("Número, nombre o RNC/Cédula");buscar.setClearButtonVisible(true);buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.setValueChangeTimeout(350);buscar.addValueChangeListener(e->{paginacion.reset();cargarFacturas();});
        desde.setClearButtonVisible(true);hasta.setClearButtonVisible(true);desde.addValueChangeListener(e->{paginacion.reset();cargarFacturas();});hasta.addValueChangeListener(e->{paginacion.reset();cargarFacturas();});
        estado.setItems(EstadoDevolucionFacturaFiltro.values());estado.setItemLabelGenerator(this::estadoLabel);estado.setValue(EstadoDevolucionFacturaFiltro.TODAS);
        estado.addValueChangeListener(e->{paginacion.reset();cargarFacturas();});
        facturas.setWidthFull();facturas.setAllRowsVisible(true);facturas.addClassName("cc-sales-return-grid");facturas.addColumn(FacturaDevolucionResumenDto::numero).setHeader("Factura").setAutoWidth(true);
        facturas.addColumn(f->DATE_TIME.format(f.fechaHora())).setHeader("Fecha/Hora").setAutoWidth(true);
        facturas.addColumn(FacturaDevolucionResumenDto::cliente).setHeader("Cliente").setFlexGrow(1);
        facturas.addColumn(f->f.moneda()+" "+money(f.total())).setHeader("Total").setAutoWidth(true);
        facturas.addColumn(FacturaDevolucionResumenDto::estado).setHeader("Estado").setAutoWidth(true);
        facturas.addColumn(new ComponentRenderer<>(f->{Button b=new Button(VaadinIcon.REPLY.create());b.addClassName("cc-grid-icon-button");b.setTooltipText("Crear devolución");b.getElement().setAttribute("aria-label","Crear devolución");b.setVisible(TenantContext.principalActual().permisos().contains("devoluciones_venta.crear"));b.addClickListener(e->seleccionar(f.id()));return b;})).setHeader("Acc.").setWidth("64px").setFlexGrow(0);
    }

    private void mostrarBusqueda(){
        detalle=null;cantidades.clear();cuerpo.removeAll();HorizontalLayout filtros=new HorizontalLayout(buscar,desde,hasta,estado);filtros.addClassName("cc-sales-return-filters");filtros.setWidthFull();filtros.setAlignItems(FlexComponent.Alignment.END);
        Button cerrar=new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cerrar",e->cerrar());HorizontalLayout pie=new HorizontalLayout(cerrar);pie.setWidthFull();pie.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        cuerpo.add(new Paragraph("Busque una factura confirmada y seleccione los productos que desea devolver."),filtros,facturas,paginacion,pie);cargarFacturas();
    }

    private void cargarFacturas(){
        try{var pagina=service.buscar(buscar.getValue(),desde.getValue(),hasta.getValue(),estado.getValue(),paginacion.currentRequest().page());facturas.setItems(pagina.getContent());paginacion.setTotal(pagina.getTotalElements());}
        catch(Exception ex){facturas.setItems(List.of());paginacion.setTotal(0);UiErrorHandler.show(ex,"No fue posible cargar las facturas disponibles para devolución.");}
    }

    private void seleccionar(UUID id){try{detalle=service.preparar(id);claveOperacion=UUID.randomUUID();mostrarDetalle();}catch(Exception ex){UiErrorHandler.show(ex,"No fue posible preparar la devolución.");}}

    private void mostrarDetalle(){
        cuerpo.removeAll();cantidades.clear();
        Div cabecera=new Div();cabecera.addClassName("cc-sales-return-invoice");cabecera.add(new Span("Factura "+detalle.facturaNumero()),new Span(DATE_TIME.format(detalle.fechaFactura())),new Span(detalle.cliente()),new Span(detalle.estado()),new Span(detalle.moneda()+" "+money(detalle.totalFactura())));
        Grid<DevolucionVentaDetalleDto.Linea> grid=new Grid<>();grid.addClassName("cc-sales-return-detail-grid");grid.setAllRowsVisible(true);
        grid.addColumn(l->l.codigo()+" · "+l.producto()).setHeader("Producto").setFlexGrow(1);grid.addColumn(DevolucionVentaDetalleDto.Linea::unidad).setHeader("Unidad").setAutoWidth(true);
        grid.addColumn(l->qty(l.facturado())).setHeader("Facturado").setAutoWidth(true);grid.addColumn(l->qty(l.devuelto())).setHeader("Devuelto").setAutoWidth(true);grid.addColumn(l->qty(l.disponible())).setHeader("Disponible").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(l->{BigDecimalField c=new BigDecimalField();c.setWidth("120px");c.setValue(BigDecimal.ZERO);c.setLabel("Devolver");c.setHelperText("Máx. "+qty(l.disponible()));c.addValueChangeListener(e->actualizarResumen());cantidades.put(l.lineaFacturaId(),c);return c;})).setHeader("Devolver").setWidth("150px").setFlexGrow(0);
        grid.setItems(detalle.lineas());
        ComboBox<DevolucionVentaDetalleDto.Motivo> motivo=new ComboBox<>("Motivo");motivo.setItems(detalle.motivos());motivo.setItemLabelGenerator(DevolucionVentaDetalleDto.Motivo::nombre);motivo.setRequiredIndicatorVisible(true);
        TextArea observacion=new TextArea("Observación");observacion.setMaxLength(1000);observacion.setWidthFull();
        Checkbox inventario=new Checkbox("Los productos físicos retornan al inventario");ComboBox<DevolucionVentaDetalleDto.Almacen> almacen=new ComboBox<>("Almacén destino");almacen.setItems(detalle.almacenes());almacen.setItemLabelGenerator(DevolucionVentaDetalleDto.Almacen::nombre);almacen.setVisible(false);
        inventario.addValueChangeListener(e->{almacen.setVisible(e.getValue());if(!e.getValue())almacen.clear();});
        Span resumen=new Span();resumen.setId("cc-sales-return-summary");resumen.addClassName("cc-sales-return-summary");
        confirmar=new AppActionButton(ActionType.ACCEPT,ButtonSize.MAIN,"Confirmar devolución",e->confirmar(motivo.getValue(),observacion.getValue(),inventario.getValue(),almacen.getValue()));
        confirmar.setVisible(TenantContext.principalActual().permisos().contains("devoluciones_venta.confirmar"));
        Button volver=new AppActionButton(ActionType.BACK,ButtonSize.MAIN,"Volver a facturas",e->mostrarBusqueda());Button cancelar=new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cerrar",e->cerrar());
        HorizontalLayout formulario=new HorizontalLayout(motivo,almacen);formulario.setWidthFull();formulario.setAlignItems(FlexComponent.Alignment.END);formulario.setFlexGrow(1,motivo,almacen);
        HorizontalLayout acciones=new HorizontalLayout(confirmar,volver,cancelar);acciones.setWidthFull();acciones.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        cuerpo.add(cabecera,grid,formulario,inventario,observacion,resumen,acciones);actualizarResumen();
    }

    private void actualizarResumen(){
        if(detalle==null)return;BigDecimal total=BigDecimal.ZERO;for(var l:detalle.lineas()){BigDecimal c=Optional.ofNullable(cantidades.get(l.lineaFacturaId())).map(BigDecimalField::getValue).orElse(BigDecimal.ZERO);if(c!=null&&c.signum()>0&&l.facturado().signum()>0)total=total.add(l.total().multiply(c.divide(l.facturado(),12,RoundingMode.HALF_UP)));}
        getElement().executeJs("const e=this.querySelector('#cc-sales-return-summary'); if(e)e.textContent=$0", "Total estimado: "+detalle.moneda()+" "+money(total));
    }

    private void confirmar(DevolucionVentaDetalleDto.Motivo motivo,String observacion,boolean retorna,DevolucionVentaDetalleDto.Almacen almacen){
        List<ConfirmarDevolucionVentaInput.Linea> lineas=cantidades.entrySet().stream().filter(e->e.getValue().getValue()!=null&&e.getValue().getValue().signum()>0).map(e->new ConfirmarDevolucionVentaInput.Linea(e.getKey(),e.getValue().getValue())).toList();
        ConfirmarDevolucionVentaInput input=new ConfirmarDevolucionVentaInput(claveOperacion,detalle.facturaId(),motivo==null?null:motivo.id(),observacion,retorna,almacen==null?null:almacen.id(),lineas);
        Dialog confirmacion=new Dialog();confirmacion.setHeaderTitle("CONFIRMAR DEVOLUCIÓN");confirmacion.add(new Paragraph("La operación generará la devolución, la nota de crédito y los movimientos correspondientes. No podrá duplicarse."));
        AppActionButton[] aceptarRef=new AppActionButton[1];
        AppActionButton aceptar=new AppActionButton(ActionType.ACCEPT,ButtonSize.MAIN,"Confirmar devolución",e->{aceptarRef[0].setEnabled(false);confirmar.setEnabled(false);try{var resultado=service.confirmar(input);confirmacion.close();close();abrirPdf.accept(resultado.id());}catch(Exception ex){aceptarRef[0].setEnabled(true);confirmar.setEnabled(true);UiErrorHandler.show(ex,"No fue posible confirmar la devolución.");}});aceptarRef[0]=aceptar;
        AppActionButton cancelar=new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->confirmacion.close());HorizontalLayout pie=new HorizontalLayout(aceptar,cancelar);pie.setWidthFull();pie.setJustifyContentMode(FlexComponent.JustifyContentMode.END);confirmacion.add(pie);confirmacion.open();
    }

    private void cerrar(){close();if(alCerrar!=null)alCerrar.run();}
    private String estadoLabel(EstadoDevolucionFacturaFiltro e){return switch(e){case TODAS->"Todas";case PAGADA->"Pagadas";case PARCIALMENTE_DEVUELTA->"Parcialmente devueltas";case DEVUELTA->"Devueltas";};}
    private static String money(BigDecimal v){return String.format(Locale.US,"%,.2f",v==null?BigDecimal.ZERO:v);}
    private static String qty(BigDecimal v){return v==null?"0":v.stripTrailingZeros().toPlainString();}
}
