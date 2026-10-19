package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.EstadoFacturaVenta;
import com.citacloud.springboot.contacloud.app.services.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public class UltimasFacturasDialog extends Dialog {
    private static final DateTimeFormatter FECHA_HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat IMPORTE=new DecimalFormat("#,##0.00");
    private final UltimasFacturasService service;
    private final Consumer<UUID> reimprimir;
    private final Runnable devolverFoco;
    private final TextField buscar=new TextField();
    private final DatePicker desde=new DatePicker("Desde"),hasta=new DatePicker("Hasta");
    private final Select<EstadoFacturaVenta> estado=new Select<>();
    private final Grid<FacturaVentaResumenDto> grid=new Grid<>();
    private final ProgressBar cargando=new ProgressBar();
    private final AppPagination paginacion;
    private List<FacturaVentaResumenDto> resultados=List.of();
    private boolean cargandoDatos;

    public UltimasFacturasDialog(UltimasFacturasService service,Consumer<UUID> reimprimir,Runnable devolverFoco){
        this.service=Objects.requireNonNull(service);this.reimprimir=Objects.requireNonNull(reimprimir);
        this.devolverFoco=Objects.requireNonNull(devolverFoco);
        setHeaderTitle("ÚLTIMAS FACTURAS");setWidth("min(1180px, 96vw)");setHeight("min(760px, 94vh)");
        addClassName("cc-latest-invoices-dialog");setCloseOnEsc(true);setCloseOnOutsideClick(false);
        Button cerrarCabecera=new Button(VaadinIcon.CLOSE.create(),e->close());
        cerrarCabecera.addClassName("cc-pos-icon-button");cerrarCabecera.setTooltipText("Cerrar");
        cerrarCabecera.getElement().setAttribute("aria-label","Cerrar últimas facturas");getHeader().add(cerrarCabecera);
        configurarFiltros();configurarGrid();
        paginacion=new AppPagination(r->cargar(),10,10);
        cargando.setIndeterminate(true);cargando.setVisible(false);cargando.addClassName("cc-latest-invoices-loading");
        Div filtros=new Div(buscar,desde,hasta,estado);filtros.addClassName("cc-latest-invoices-filters");
        Div cuerpo=new Div(filtros,cargando,grid,paginacion);cuerpo.addClassName("cc-latest-invoices-body");add(cuerpo);
        getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->close()));
        registrarTeclado();
        addOpenedChangeListener(e->{if(e.isOpened()){cargar();buscar.focus();}});
        addClosedListener(e->devolverFoco.run());
    }

    private void configurarFiltros(){
        buscar.setPlaceholder("Buscar por cliente, RNC/Cédula o ID de factura...");buscar.setClearButtonVisible(true);
        buscar.setPrefixComponent(VaadinIcon.SEARCH.create());buscar.setValueChangeMode(ValueChangeMode.LAZY);buscar.setValueChangeTimeout(300);
        buscar.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        for(DatePicker fecha:List.of(desde,hasta)){fecha.setLocale(new Locale("es","DO"));fecha.setPlaceholder("DD/MM/YYYY");fecha.setClearButtonVisible(true);
            fecha.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});}
        estado.setLabel("Estado");estado.setItems(EstadoFacturaVenta.values());estado.setItemLabelGenerator(UltimasFacturasDialog::estado);
        estado.setEmptySelectionAllowed(true);estado.setPlaceholder("Todos");estado.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
    }

    private void configurarGrid(){
        grid.addColumn(FacturaVentaResumenDto::numero).setHeader("Factura").setWidth("150px").setFlexGrow(0);
        grid.addColumn(f->fechaHora(f.fechaHora())).setHeader("Fecha/Hora").setWidth("175px").setFlexGrow(0);
        grid.addComponentColumn(this::cliente).setHeader("Cliente").setFlexGrow(1);
        grid.addComponentColumn(f->importe(f.moneda(),f.total())).setHeader("Total").setWidth("155px").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(f->badge(f.estado())).setHeader("Estado").setWidth("125px").setFlexGrow(0);
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setWidth("105px").setFlexGrow(0);
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);grid.setAllRowsVisible(false);grid.setHeightFull();
        grid.setEmptyStateText("No se encontraron facturas.");grid.addItemDoubleClickListener(e->abrirDetalle(e.getItem()));
        grid.addClassName("cc-latest-invoices-grid");
    }

    private Component cliente(FacturaVentaResumenDto factura){Span nombre=new Span(valor(factura.cliente()));nombre.addClassName("cc-latest-invoice-customer");
        nombre.getElement().setAttribute("title",valor(factura.cliente()));return nombre;}
    private Component importe(String moneda,BigDecimal total){Span valor=new Span(valor(moneda)+" "+IMPORTE.format(total));valor.addClassName("cc-latest-invoice-amount");return valor;}
    private Component acciones(FacturaVentaResumenDto factura){HorizontalLayout acciones=new HorizontalLayout();acciones.addClassName("cc-grid-actions");acciones.setPadding(false);acciones.setSpacing(false);
        acciones.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver factura",e->abrirDetalle(factura)));
        if(puede("facturas.pdf"))acciones.add(new AppActionButton(ActionType.PRINT,ButtonSize.GRID_ACTION,"Reimprimir factura",e->reimprimir.accept(factura.id())));
        return acciones;}

    private void cargar(){
        if(cargandoDatos)return;
        if(desde.getValue()!=null&&hasta.getValue()!=null&&desde.getValue().isAfter(hasta.getValue())){
            Notification.show("La fecha inicial no puede ser posterior a la fecha final.");return;}
        cargandoDatos=true;cargando.setVisible(true);grid.setEnabled(false);
        try{var pagina=paginacion.currentRequest();var resultado=service.buscar(buscar.getValue(),desde.getValue(),hasta.getValue(),estado.getValue(),pagina.page());
            resultados=resultado.getContent();grid.setItems(resultados);paginacion.setTotal(resultado.getTotalElements());
            if(!resultados.isEmpty())grid.deselectAll();
        }catch(RuntimeException ex){Notification.show(mensaje(ex,"No fue posible consultar las facturas. Intente nuevamente."));}
        finally{grid.setEnabled(true);cargando.setVisible(false);cargandoDatos=false;}
    }
    private void reiniciar(){paginacion.reset();cargar();}

    private void registrarTeclado(){
        Shortcuts.addShortcutListener(this,()->moverSeleccion(1),Key.ARROW_DOWN);
        Shortcuts.addShortcutListener(this,()->moverSeleccion(-1),Key.ARROW_UP);
        Shortcuts.addShortcutListener(this,()->grid.getSelectedItems().stream().findFirst().ifPresent(this::abrirDetalle),Key.ENTER);
    }
    private void moverSeleccion(int paso){if(resultados.isEmpty())return;FacturaVentaResumenDto actual=grid.getSelectedItems().stream().findFirst().orElse(null);
        int indice=actual==null?(paso>0?0:resultados.size()-1):Math.max(0,Math.min(resultados.size()-1,resultados.indexOf(actual)+paso));
        FacturaVentaResumenDto siguiente=resultados.get(indice);grid.select(siguiente);grid.scrollToIndex(indice);}

    private void abrirDetalle(FacturaVentaResumenDto seleccion){
        try{FacturaVentaDetalleDto factura=service.detalle(seleccion.id());Dialog detalle=new Dialog();detalle.setHeaderTitle("FACTURA "+factura.numero());
            detalle.setWidth("min(980px, 96vw)");detalle.setHeight("min(760px, 94vh)");detalle.addClassName("cc-latest-invoice-detail");
            AppDetailSection cabecera=new AppDetailSection("INFORMACIÓN GENERAL").field("Fecha/Hora",fechaHora(factura.fechaHora()))
                    .field("Cliente",factura.cliente()).field("RNC/Cédula",valor(factura.identificacion())).field("Estado",estado(factura.estado()))
                    .field("Moneda",factura.moneda()).field("Condición de pago",factura.condicionPago()).field("Vendedor",factura.vendedor());
            cabecera.addClassName("cc-latest-invoice-general");
            Grid<FacturaVentaDetalleDto.Linea> lineas=new Grid<>();lineas.addColumn(FacturaVentaDetalleDto.Linea::producto).setHeader("Producto").setFlexGrow(1);
            lineas.addColumn(FacturaVentaDetalleDto.Linea::unidad).setHeader("Unidad").setWidth("105px").setFlexGrow(0);
            lineas.addColumn(l->IMPORTE.format(l.cantidad())).setHeader("Cantidad").setWidth("110px").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
            lineas.addColumn(l->IMPORTE.format(l.precio())).setHeader("Precio").setWidth("120px").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
            lineas.addColumn(l->IMPORTE.format(l.impuesto())).setHeader("Impuesto").setWidth("120px").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
            lineas.addColumn(l->IMPORTE.format(l.total())).setHeader("Total").setWidth("130px").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
            lineas.setItems(factura.lineas());lineas.setAllRowsVisible(true);lineas.addClassName("cc-latest-invoice-lines");
            Div totales=totales(factura);Div pagos=pagos(factura);Div contenido=new Div(cabecera,new H3("PRODUCTOS"),lineas,totales,pagos);
            contenido.addClassName("cc-latest-invoice-detail-body");detalle.add(contenido);
            if(puede("facturas.pdf"))detalle.getFooter().add(new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Reimprimir",e->reimprimir.accept(factura.id())));
            detalle.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->detalle.close()));
            detalle.addClosedListener(e->buscar.focus());detalle.open();
        }catch(RuntimeException ex){Notification.show(mensaje(ex,"No fue posible consultar la factura."));}
    }

    private Div totales(FacturaVentaDetalleDto f){Div bloque=new Div();bloque.addClassName("cc-latest-invoice-totals");bloque.add(fila("Subtotal",f.moneda(),f.subtotal()));
        if(f.descuento()!=null&&f.descuento().signum()>0)bloque.add(fila("Descuento","-"+f.moneda(),f.descuento()));
        if(f.recargo()!=null&&f.recargo().signum()>0)bloque.add(fila("Recargo",f.moneda(),f.recargo()));
        bloque.add(fila("Impuestos",f.moneda(),f.impuestos()),fila("TOTAL",f.moneda(),f.total()));return bloque;}
    private Div pagos(FacturaVentaDetalleDto f){Div bloque=new Div(new H3("PAGO"));bloque.addClassName("cc-latest-invoice-payments");
        if(f.pagos().isEmpty()){bloque.add(new Span("No hay información de pago disponible."));return bloque;}
        for(var pago:f.pagos()){StringBuilder texto=new StringBuilder(pago.metodo()).append(" · ").append(f.moneda()).append(' ').append(IMPORTE.format(pago.monto()));
            if(pago.recibido()!=null)texto.append(" · Recibido ").append(f.moneda()).append(' ').append(IMPORTE.format(pago.recibido()));
            if(pago.cambio()!=null&&pago.cambio().signum()>0)texto.append(" · Cambio ").append(f.moneda()).append(' ').append(IMPORTE.format(pago.cambio()));
            if(pago.referencia()!=null&&!pago.referencia().isBlank())texto.append(" · Ref. ").append(pago.referencia());bloque.add(new Span(texto.toString()));}
        return bloque;}
    private Div fila(String etiqueta,String moneda,BigDecimal importe){Div fila=new Div(new Span(etiqueta),new Span(moneda+" "+IMPORTE.format(importe)));fila.addClassName("cc-latest-invoice-total-row");
        if("TOTAL".equals(etiqueta))fila.addClassName("cc-latest-invoice-grand-total");return fila;}
    private static Span badge(EstadoFacturaVenta estado){Span badge=new Span(estado(estado));badge.getElement().getThemeList().add("badge "+(estado==EstadoFacturaVenta.VOIDED?"error":"success"));return badge;}
    static String estado(EstadoFacturaVenta estado){return estado==EstadoFacturaVenta.VOIDED?"Anulada":"Pagada";}
    private static String fechaHora(OffsetDateTime fecha){return fecha==null?"—":FECHA_HORA.format(fecha.atZoneSameInstant(ZoneId.systemDefault()));}
    private static String valor(String texto){return texto==null||texto.isBlank()?"—":texto;}
    private static boolean puede(String permiso){return com.citacloud.springboot.contacloud.app.security.TenantContext.principalActual().permisos().contains(permiso);}
    private static String mensaje(RuntimeException ex,String base){return ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():base;}
}
