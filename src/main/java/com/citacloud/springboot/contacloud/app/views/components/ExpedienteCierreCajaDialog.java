package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.services.ExpedienteCierreCajaService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.*;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Expediente histórico del turno; la pestaña Cierre conserva el detalle existente sin recalcularlo. */
public class ExpedienteCierreCajaDialog extends Dialog {
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CierreCajaDto cierre;
    private final ExpedienteCierreCajaService service;
    private final Consumer<UUID> verFactura,verRecibo,verMovimiento,verRecuento;
    private final AppPagination ventasPagination,cobrosPagination,movimientosPagination,recuentosPagination;
    private final AppGrid<FacturaVentaResumenDto> ventas=new AppGrid<>(FacturaVentaResumenDto.class);
    private final AppGrid<CobroClienteDto> cobros=new AppGrid<>(CobroClienteDto.class);
    private final AppGrid<MovimientoCajaDto> movimientos=new AppGrid<>(MovimientoCajaDto.class);
    private final AppGrid<RecuentoCajaDto> recuentos=new AppGrid<>(RecuentoCajaDto.class);
    private final TextField buscarVentas=new TextField("Buscar"),buscarCobros=new TextField("Buscar"),buscarMovimientos=new TextField("Buscar");
    private final Select<EstadoFacturaVenta> estadoVentas=new Select<>();
    private final Span totalVentas=new Span();
    private final VerticalLayout panelCierre=new VerticalLayout(),panelVentas=new VerticalLayout(),panelCobros=new VerticalLayout(),panelMovimientos=new VerticalLayout(),panelRecuentos=new VerticalLayout();
    private boolean ventasCargadas,cobrosCargados,movimientosCargados,recuentosCargados;

    static List<String> ordenPestanas(){return List.of("Cierre","Ventas","Cobros","Movimientos","Recuentos");}
    UUID cierreId(){return cierre.id();}
    UUID sesionId(){return cierre.sesionId();}
    boolean cierreEsPestanaInicial(){return panelCierre.isVisible()&&!panelVentas.isVisible()&&!panelCobros.isVisible()&&!panelMovimientos.isVisible()&&!panelRecuentos.isVisible();}

    public ExpedienteCierreCajaDialog(CierreCajaDto cierre,ExpedienteCierreCajaService service,
            boolean puedeVentas,boolean puedeCobros,boolean puedeMovimientos,boolean puedeRecuentos,
            boolean puedeImprimir,Consumer<UUID> imprimir,Consumer<UUID> verFactura,Consumer<UUID> verRecibo,
            Consumer<UUID> verMovimiento,Consumer<UUID> verRecuento){
        this.cierre=Objects.requireNonNull(cierre);this.service=Objects.requireNonNull(service);
        this.verFactura=Objects.requireNonNull(verFactura);this.verRecibo=Objects.requireNonNull(verRecibo);
        this.verMovimiento=Objects.requireNonNull(verMovimiento);this.verRecuento=Objects.requireNonNull(verRecuento);
        setHeaderTitle("CIERRE DE CAJA "+cierre.numero());setWidth("min(800px, 96vw)");
        ventasPagination=new AppPagination(r->cargarVentas());cobrosPagination=new AppPagination(r->cargarCobros());
        movimientosPagination=new AppPagination(r->cargarMovimientos());recuentosPagination=new AppPagination(r->cargarRecuentos());
        prepararPaneles();configurarVentas();configurarCobros();configurarMovimientos();configurarRecuentos();
        Tab tabCierre=new Tab("Cierre"),tabVentas=new Tab("Ventas"),tabCobros=new Tab("Cobros"),
            tabMovimientos=new Tab("Movimientos"),tabRecuentos=new Tab("Recuentos");
        Tabs tabs=new Tabs(tabCierre,tabVentas,tabCobros,tabMovimientos,tabRecuentos);tabs.setSelectedTab(tabCierre);
        AppActionButton print=new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Imprimir cierre",e->imprimir.accept(cierre.id()));
        print.setVisible(puedeImprimir);
        restringir(panelVentas,puedeVentas,"No tienes permiso para consultar las facturas del turno.");
        restringir(panelCobros,puedeCobros,"No tienes permiso para consultar los cobros del turno.");
        restringir(panelMovimientos,puedeMovimientos,"No tienes permiso para consultar los movimientos del turno.");
        restringir(panelRecuentos,puedeRecuentos,"No tienes permiso para consultar los recuentos del turno.");
        tabs.addSelectedChangeListener(e->{ocultarPaneles();Tab selected=e.getSelectedTab();print.setVisible(puedeImprimir&&selected==tabCierre);
            if(selected==tabCierre)panelCierre.setVisible(true);
            else if(selected==tabVentas){panelVentas.setVisible(true);if(puedeVentas&&!ventasCargadas)cargarVentas();}
            else if(selected==tabCobros){panelCobros.setVisible(true);if(puedeCobros&&!cobrosCargados)cargarCobros();}
            else if(selected==tabMovimientos){panelMovimientos.setVisible(true);if(puedeMovimientos&&!movimientosCargados)cargarMovimientos();}
            else{panelRecuentos.setVisible(true);if(puedeRecuentos&&!recuentosCargados)cargarRecuentos();}});
        add(tabs,panelCierre,panelVentas,panelCobros,panelMovimientos,panelRecuentos);
        getFooter().add(print,new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->close()));
    }

    private void prepararPaneles(){for(VerticalLayout panel:List.of(panelCierre,panelVentas,panelCobros,panelMovimientos,panelRecuentos)){
        panel.setPadding(false);panel.setSpacing(false);panel.setWidthFull();}
        panelCierre.add(contenidoCierre());panelVentas.setVisible(false);panelCobros.setVisible(false);panelMovimientos.setVisible(false);panelRecuentos.setVisible(false);}

    /** Copia estructural exacta del contenido que mostraba Ver cierre antes de incorporar las pestañas. */
    private Component contenidoCierre(){
        AppDetailSection resumen=new AppDetailSection("Información").field("Turno",cierre.turno()).field("Caja",cierre.caja())
            .field("Sucursal",cierre.sucursal()).field("Apertura",cierre.apertura().format(DATE_TIME))
            .field("Cierre",cierre.cierre().format(DATE_TIME)).field("Cajero",cierre.cerradoPor()).field("Moneda",cierre.monedaCodigo())
            .field("Fondo inicial",dinero(cierre.fondoInicial())).field("Entradas en efectivo",dinero(cierre.totalEntradasEfectivo()))
            .field("Salidas en efectivo",dinero(cierre.totalSalidasEfectivo())).field("Efectivo esperado",dinero(cierre.esperado()))
            .field("Efectivo contado",dinero(cierre.contado())).field("Diferencia",dinero(cierre.diferencia()))
            .field("Resultado",resultado(cierre.resultado())).field("Observación",cierre.observacion()==null?"—":cierre.observacion());
        AppGrid<CierreCajaDto.Detalle> grid=new AppGrid<>(CierreCajaDto.Detalle.class);
        grid.addColumn(x->cierre.monedaSimbolo()+" "+PosNumberFormatter.money(x.denominacion())).setHeader("Denominación");
        grid.addColumn(CierreCajaDto.Detalle::cantidad).setHeader("Cantidad");
        grid.addColumn(x->PosNumberFormatter.money(x.subtotal())).setHeader("Subtotal");grid.setItems(cierre.detalles());grid.setAllRowsVisible(true);
        VerticalLayout contenido=new VerticalLayout(resumen,new H4("CONTEO FÍSICO"),grid);contenido.setPadding(false);return contenido;
    }

    private void configurarVentas(){buscarVentas.setPlaceholder("Factura, cliente o identificación...");configurarBuscar(buscarVentas,()->{ventasPagination.reset();cargarVentas();});
        estadoVentas.setLabel("Estado");estadoVentas.setItems(EstadoFacturaVenta.values());estadoVentas.setEmptySelectionAllowed(true);estadoVentas.setEmptySelectionCaption("Todos");
        estadoVentas.setItemLabelGenerator(x->x==EstadoFacturaVenta.VOIDED?"Anulada":"Pagada");estadoVentas.addValueChangeListener(e->{if(e.isFromClient()){ventasPagination.reset();cargarVentas();}});
        ventas.addColumn(FacturaVentaResumenDto::numero).setHeader("Factura").setAutoWidth(true);
        ventas.addColumn(x->fecha(x.fechaHora())).setHeader("Fecha/Hora").setAutoWidth(true);ventas.addColumn(FacturaVentaResumenDto::cliente).setHeader("Cliente").setFlexGrow(1);
        ventas.addColumn(x->x.moneda()+" "+PosNumberFormatter.money(x.total())).setHeader("Total").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        ventas.addColumn(x->x.estado()==EstadoFacturaVenta.VOIDED?"Anulada":"Pagada").setHeader("Estado").setAutoWidth(true);
        ventas.addComponentColumn(x->accion("Ver factura",e->verFactura.accept(x.id()))).setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        ventas.setEmptyStateText("No se registraron ventas durante este turno.");totalVentas.addClassName("cc-cash-close-turn-total");
        panelVentas.removeAll();panelVentas.add(new H4("VENTAS DEL TURNO"),new HorizontalLayout(buscarVentas,estadoVentas),ventas,ventasPagination,totalVentas);}

    private void configurarCobros(){buscarCobros.setPlaceholder("Recibo, cliente, identificación o factura...");configurarBuscar(buscarCobros,()->{cobrosPagination.reset();cargarCobros();});
        cobros.addColumn(CobroClienteDto::numero).setHeader("Recibo").setAutoWidth(true);cobros.addColumn(x->fecha(x.confirmadoEn())).setHeader("Fecha/Hora").setAutoWidth(true);
        cobros.addColumn(CobroClienteDto::cliente).setHeader("Cliente").setFlexGrow(1);cobros.addColumn(x->x.moneda()+" "+PosNumberFormatter.money(x.recibido())).setHeader("Total").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        cobros.addColumn(x->estado(x.estado())).setHeader("Estado").setAutoWidth(true);cobros.addComponentColumn(x->accion("Ver recibo",e->verRecibo.accept(x.id()))).setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        cobros.setEmptyStateText("No se registraron cobros durante este turno.");panelCobros.removeAll();panelCobros.add(new H4("COBROS DEL TURNO"),buscarCobros,cobros,cobrosPagination);}

    private void configurarMovimientos(){buscarMovimientos.setPlaceholder("Concepto, origen o referencia...");configurarBuscar(buscarMovimientos,()->{movimientosPagination.reset();cargarMovimientos();});
        movimientos.addColumn(x->fecha(x.fechaHora())).setHeader("Fecha/Hora").setAutoWidth(true);movimientos.addColumn(MovimientoCajaDto::concepto).setHeader("Concepto").setFlexGrow(1);
        movimientos.addColumn(x->origen(x.origen())).setHeader("Origen").setAutoWidth(true);movimientos.addColumn(MovimientoCajaDto::referencia).setHeader("Referencia").setAutoWidth(true);
        movimientos.addColumn(x->x.tipo()==TipoMovimientoFinanciero.INCOME?PosNumberFormatter.money(x.importe()):"—").setHeader("Entrada").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        movimientos.addColumn(x->x.tipo()==TipoMovimientoFinanciero.EXPENSE?PosNumberFormatter.money(x.importe()):"—").setHeader("Salida").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        movimientos.addColumn(x->PosNumberFormatter.money(x.saldo())).setHeader("Saldo").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        movimientos.addComponentColumn(x->accion("Ver movimiento",e->verMovimiento.accept(x.id()))).setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        movimientos.setEmptyStateText("No se encontraron movimientos para este turno.");panelMovimientos.removeAll();panelMovimientos.add(new H4("MOVIMIENTOS DEL TURNO"),buscarMovimientos,movimientos,movimientosPagination);}

    private void configurarRecuentos(){recuentos.addColumn(RecuentoCajaDto::numero).setHeader("Número").setAutoWidth(true);recuentos.addColumn(x->fecha(x.contadoEn())).setHeader("Fecha/Hora").setAutoWidth(true);
        recuentos.addColumn(x->x.monedaCodigo()+" "+PosNumberFormatter.money(x.esperado())).setHeader("Esperado").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        recuentos.addColumn(x->x.monedaCodigo()+" "+PosNumberFormatter.money(x.contado())).setHeader("Contado").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        recuentos.addColumn(x->resultado(x.resultado())).setHeader("Resultado").setAutoWidth(true);recuentos.addComponentColumn(x->accion("Ver recuento",e->verRecuento.accept(x.id()))).setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        recuentos.setEmptyStateText("No se realizaron recuentos durante este turno.");panelRecuentos.removeAll();panelRecuentos.add(new H4("RECUENTOS DEL TURNO"),recuentos,recuentosPagination);}

    private void cargarVentas(){try{var r=ventasPagination.currentRequest();var page=service.ventas(cierre.id(),buscarVentas.getValue(),estadoVentas.getValue(),r.page(),r.size());
        ventas.setItems(page.getContent());ventasPagination.setTotal(page.getTotalElements());totalVentas.setText("TOTAL VENDIDO  "+cierre.monedaCodigo()+" "+PosNumberFormatter.money(service.totalVentas(cierre.id())));ventasCargadas=true;}
        catch(RuntimeException ex){ventas.setItems(List.of());ventasPagination.setTotal(0);UiErrorHandler.show(ex,"No fue posible consultar las ventas del turno.");}}
    private void cargarCobros(){try{var r=cobrosPagination.currentRequest();var page=service.cobros(cierre.id(),buscarCobros.getValue(),r.page(),r.size());cobros.setItems(page.getContent());cobrosPagination.setTotal(page.getTotalElements());cobrosCargados=true;}
        catch(RuntimeException ex){cobros.setItems(List.of());cobrosPagination.setTotal(0);UiErrorHandler.show(ex,"No fue posible consultar los cobros del turno.");}}
    private void cargarMovimientos(){try{var r=movimientosPagination.currentRequest();var page=service.movimientos(cierre.id(),buscarMovimientos.getValue(),r.page(),r.size());movimientos.setItems(page.getContent());movimientosPagination.setTotal(page.getTotalElements());movimientosCargados=true;}
        catch(RuntimeException ex){movimientos.setItems(List.of());movimientosPagination.setTotal(0);UiErrorHandler.show(ex,"No fue posible consultar los movimientos del turno.");}}
    private void cargarRecuentos(){try{var r=recuentosPagination.currentRequest();var page=service.recuentos(cierre.id(),r.page(),r.size());recuentos.setItems(page.getContent());recuentosPagination.setTotal(page.getTotalElements());recuentosCargados=true;}
        catch(RuntimeException ex){recuentos.setItems(List.of());recuentosPagination.setTotal(0);UiErrorHandler.show(ex,"No fue posible consultar los recuentos del turno.");}}

    private static AppActionButton accion(String tooltip,com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<com.vaadin.flow.component.button.Button>> listener){return new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,tooltip,listener);}
    private static void configurarBuscar(TextField field,Runnable action){field.setClearButtonVisible(true);field.setValueChangeMode(ValueChangeMode.LAZY);field.setValueChangeTimeout(400);field.addValueChangeListener(e->{if(e.isFromClient())action.run();});}
    private static void restringir(VerticalLayout panel,boolean permitido,String mensaje){if(!permitido){panel.removeAll();panel.add(new Span(mensaje));}}
    private void ocultarPaneles(){panelCierre.setVisible(false);panelVentas.setVisible(false);panelCobros.setVisible(false);panelMovimientos.setVisible(false);panelRecuentos.setVisible(false);}
    private static String fecha(java.time.OffsetDateTime fecha){return fecha==null?"—":fecha.format(DATE_TIME);}
    private static String dinero(BigDecimal valor){return valor==null?"—":String.format(Locale.US,"%,.2f",valor);}
    private static String resultado(ResultadoRecuentoCaja r){return switch(r){case BALANCED->"Cuadre";case SHORTAGE->"Faltante";case OVERAGE->"Sobrante";};}
    private static String estado(EstadoReciboCliente e){return switch(e){case AVAILABLE->"Disponible";case PARTIALLY_APPLIED->"Parcial";case APPLIED->"Aplicado";case VOIDED->"Anulado";};}
    private static String origen(TipoOrigenMovimiento o){return switch(o){case SALES_INVOICE->"Venta";case CUSTOMER_RECEIPT,SALES_PAYMENT->"Recibo";case CASH_VOUCHER->"Vale de caja";case OPENING_BALANCE->"Apertura";case REVERSAL->"Reversión";case MANUAL->"Manual";default->o.name().replace('_',' ');};}
}
