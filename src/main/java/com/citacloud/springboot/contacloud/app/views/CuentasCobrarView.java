package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.CarteraCobrarDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.CuentasCobrarPdfService;
import com.citacloud.springboot.contacloud.app.services.CuentasCobrarService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

@Route(value="cuentas-por-cobrar",layout=MainLayout.class)
@PageTitle("Cuentas por cobrar | ContaCloud")
@PermitAll
public class CuentasCobrarView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CuentasCobrarService servicio;
    private final CuentasCobrarPdfService pdf;
    private final ModuleAuthorization autorizacion;
    private final Div indicadores=new Div();
    private final ComboBox<Cliente> cliente=new ComboBox<>("Cliente");
    private final Select<Estado> estado=new Select<>();
    private final DatePicker desde=fecha("Vence desde");
    private final DatePicker hasta=fecha("Vence hasta");
    private final TextField buscar=new TextField("Buscar");
    private final AppGrid<Cuenta> documentos=new AppGrid<>(Cuenta.class);
    private final AppGrid<ClienteCartera> clientes=new AppGrid<>(ClienteCartera.class);
    private final AppPagination paginacion=new AppPagination(p->cargar());
    private final Tab tabDocumentos=new Tab("Documentos");
    private final Tab tabClientes=new Tab("Por cliente");
    private final Tabs modo=new Tabs(tabDocumentos,tabClientes);
    private final AppActionButton cartera;
    private final AppActionButton antiguedad;

    public CuentasCobrarView(CuentasCobrarService servicio,CuentasCobrarPdfService pdf,ModuleAuthorization autorizacion){
        this.servicio=servicio;
        this.pdf=pdf;
        this.autorizacion=autorizacion;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        addClassName("cc-page");
        cartera=new AppActionButton(ActionType.PDF,ButtonSize.MAIN,"Reporte de cuentas por cobrar",e->abrirCartera());
        antiguedad=new AppActionButton(ActionType.ANALYTICS,ButtonSize.MAIN,"Reporte de antigüedad de saldos",e->solicitarCorte());
        add(new AppPageHeader("CUENTAS POR COBRAR","Consulta los saldos y vencimientos de tus clientes.",cartera,antiguedad));
        indicadores.addClassName("cc-cartera-indicadores");
        cliente.setPlaceholder("Todos los clientes");
        cliente.setClearButtonVisible(true);
        cliente.setItemLabelGenerator(Cliente::nombre);
        cliente.setItems(q->servicio.buscarClientes(q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());
        estado.setLabel("Estado");
        estado.setItems(Estado.values());
        estado.setItemLabelGenerator(Estado::etiqueta);
        estado.setValue(Estado.ACTIVA);
        buscar.setPlaceholder("Documento, cliente o identificación");
        buscar.setMaxLength(150);
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        var filtros=new Div(cliente,estado,desde,hasta,buscar);
        filtros.addClassNames("cc-filter-bar","cc-cartera-filtros");
        configurarTablas();
        add(indicadores,filtros,modo,documentos,clientes,paginacion);
        cliente.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        estado.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        desde.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        hasta.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        buscar.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        modo.addSelectedChangeListener(e->{if(e.isFromClient())reiniciar();});
    }

    @Override public void beforeEnter(BeforeEnterEvent e){
        if(!autorizacion.canAccess("VENTAS","cuentas_cobrar.ver")){
            Notification.show("No tienes permiso para consultar cuentas por cobrar.");
            e.rerouteTo(DashboardView.class);
            return;
        }
        boolean reportar=TenantContext.principalActual().permisos().contains("cuentas_cobrar.reportar");
        cartera.setVisible(reportar);
        antiguedad.setVisible(reportar);
        cargar();
    }

    private void configurarTablas(){
        documentos.addColumn(Cuenta::documento).setHeader("Documento").setAutoWidth(true).setFlexGrow(0);
        documentos.addColumn(c->formato(c.fecha())).setHeader("Fecha").setAutoWidth(true).setFlexGrow(0);
        documentos.addColumn(c->formato(c.vencimiento())).setHeader("Vence").setAutoWidth(true).setFlexGrow(0);
        documentos.addColumn(c->c.cliente().nombre()).setHeader("Cliente").setFlexGrow(2);
        columnaImporte(documentos,"Total",c->importe(c.moneda(),c.total()));
        columnaImporte(documentos,"Saldo",c->importe(c.moneda(),c.saldo()));
        documentos.addComponentColumn(c->etiqueta(c.estado())).setHeader("Estado").setAutoWidth(true).setFlexGrow(0);
        documentos.addComponentColumn(c->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,
            "Ver cuenta por cobrar",e->ver(c.id()))).setHeader("Acc.").setWidth("64px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        documentos.setEmptyStateText("No se encontraron cuentas por cobrar.");

        clientes.addColumn(c->c.cliente().nombre()).setHeader("Cliente").setFlexGrow(2);
        columnaImporte(clientes,"Por vencer",c->importe(c.moneda(),c.corriente()));
        columnaImporte(clientes,"Vencido",c->importe(c.moneda(),c.vencido()));
        columnaImporte(clientes,"Total",c->importe(c.moneda(),c.saldo()));
        clientes.addComponentColumn(c->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver cartera del cliente",
            e->{cliente.setValue(c.cliente());modo.setSelectedTab(tabDocumentos);reiniciar();}))
            .setHeader("Acc.").setWidth("64px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        clientes.setEmptyStateText("No hay clientes con saldo para estos filtros.");
        clientes.setVisible(false);
    }

    private void reiniciar(){paginacion.reset();cargar();}

    private Filtro filtro(){
        return new Filtro(cliente.getValue()==null?null:cliente.getValue().id(),estado.getValue(),
            desde.getValue(),hasta.getValue(),buscar.getValue());
    }

    private void cargar(){
        try{
            var p=paginacion.currentRequest();
            boolean porCliente=modo.getSelectedTab()==tabClientes;
            var pagina=servicio.consultar(filtro(),porCliente,p.page(),p.size());
            documentos.setVisible(!porCliente);
            clientes.setVisible(porCliente);
            if(porCliente){
                clientes.setItems(pagina.clientes().getContent());
                clientes.recalculateColumnWidths();
                paginacion.setTotal(pagina.clientes().getTotalElements());
            }else{
                documentos.setItems(pagina.documentos().getContent());
                documentos.recalculateColumnWidths();
                paginacion.setTotal(pagina.documentos().getTotalElements());
            }
            mostrarIndicadores(pagina.indicadores());
        }catch(RuntimeException ex){
            UiErrorHandler.show(ex,"No fue posible consultar las cuentas por cobrar.");
        }
    }

    private void mostrarIndicadores(Indicadores resumen){
        indicadores.removeAll();
        indicadores.add(tarjeta("TOTAL CARTERA",resumen,Moneda::saldo),
            tarjeta("POR VENCER",resumen,Moneda::corriente),tarjeta("VENCIDO",resumen,Moneda::vencido),
            tarjeta("CLIENTES",String.valueOf(resumen.clientes())));
    }

    private static Div tarjeta(String titulo,Indicadores resumen,Function<Moneda,BigDecimal> monto){
        var tarjeta=tarjeta(titulo);
        if(resumen.monedas().isEmpty())tarjeta.add(valor("—"));
        else resumen.monedas().forEach(m->tarjeta.add(valor(importe(m.codigo(),monto.apply(m)))));
        return tarjeta;
    }
    private static Div tarjeta(String titulo,String... valores){
        var tarjeta=new Div(new Span(titulo));
        tarjeta.addClassName("cc-card");
        for(String v:valores)tarjeta.add(valor(v));
        return tarjeta;
    }
    private static Span valor(String texto){
        var valor=new Span(texto);
        valor.addClassName("cc-cartera-importe");
        return valor;
    }

    private void ver(UUID id){
        try{
            var d=servicio.detalle(id);
            var c=d.cuenta();
            var dialogo=new Dialog();
            dialogo.setHeaderTitle("CUENTA POR COBRAR · "+c.documento());
            dialogo.setWidth("min(1100px,96vw)");
            dialogo.setMaxHeight("92vh");
            var info=new AppDetailSection("INFORMACIÓN DEL DOCUMENTO").field("Documento",c.documento())
                .field("Fecha",formato(c.fecha())).field("Vence",formato(c.vencimiento()))
                .field("Estado",c.estado().etiqueta()).field("Cliente",c.cliente().nombre())
                .field("Moneda",c.moneda()).field("Condición de pago",c.condicion()).field("Sucursal",c.sucursal());
            if(c.identificacion()!=null&&!c.identificacion().isBlank())info.field("Identificación",c.identificacion());
            if(c.referencia()!=null)info.field("Factura de referencia",c.referencia());
            info.addClassName("cc-detail-four");
            var resumen=new AppDetailSection("RESUMEN FINANCIERO")
                .field("Total documento",importe(c.moneda(),c.total()))
                .field("Total aplicado",importe(c.moneda(),c.aplicado()))
                .field("Cobros aplicados",importe(c.moneda(),d.cobros()))
                .field("Saldo pendiente",importe(c.moneda(),c.saldo()));
            if(d.creditos().signum()!=0)resumen.field("Créditos aplicados",importe(c.moneda(),d.creditos()));
            if(d.otrosAplicados().signum()!=0)resumen.field("Otras aplicaciones",importe(c.moneda(),d.otrosAplicados()));
            if(c.deudaOriginal().compareTo(c.total())!=0)resumen.field("Deuda registrada",importe(c.moneda(),c.deudaOriginal()));
            resumen.addClassName("cc-detail-four");
            var movimientos=new AppGrid<Aplicacion>(Aplicacion.class);
            movimientos.addColumn(Aplicacion::documento).setHeader("Documento").setAutoWidth(true);
            movimientos.addColumn(Aplicacion::tipo).setHeader("Tipo").setAutoWidth(true);
            movimientos.addColumn(a->FECHA_HORA.format(a.fecha())).setHeader("Fecha").setAutoWidth(true);
            columnaImporte(movimientos,"Importe",a->importe(c.moneda(),a.importe()));
            movimientos.addColumn(a->a.revertida()?"Revertida":"Aplicada").setHeader("Estado").setAutoWidth(true);
            movimientos.setItems(d.aplicaciones());
            movimientos.setEmptyStateText("Este documento no tiene aplicaciones registradas.");
            dialogo.add(info,resumen,new H4("APLICACIONES / COBROS"),movimientos);
            dialogo.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->dialogo.close()));
            dialogo.open();
        }catch(RuntimeException ex){
            UiErrorHandler.show(ex,"No fue posible abrir la cuenta por cobrar.");
        }
    }

    private void abrirCartera(){
        try{PdfEnPestana.abrir(pdf.cartera(filtro()));}
        catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible generar el reporte de cartera.");}
    }

    private void solicitarCorte(){
        try{
            var dialogo=new Dialog();
            dialogo.setHeaderTitle("ANTIGÜEDAD DE SALDOS");
            var corte=fecha("Fecha de corte");
            var hoy=servicio.hoy();
            corte.setValue(hoy);
            corte.setMax(hoy);
            corte.setRequiredIndicatorVisible(true);
            dialogo.add(new Paragraph("El reporte usa los filtros de la cartera y agrupa los saldos por cliente y moneda."),corte);
            var filtros=filtro();
            dialogo.getFooter().add(new AppActionButton(ActionType.PDF,ButtonSize.MAIN,"Generar reporte",e->{
                try{PdfEnPestana.abrir(pdf.antiguedad(filtros,corte.getValue()));dialogo.close();}
                catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible generar el reporte de antigüedad.");}
            }),new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->dialogo.close()));
            dialogo.open();
        }catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible preparar el reporte de antigüedad.");}
    }

    private static <T> void columnaImporte(AppGrid<T> grid,String titulo,Function<T,String> valor){
        grid.addColumn(valor::apply).setHeader(titulo).setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
    }
    private static Span etiqueta(Estado estado){
        var estadoVisual=new Span(estado.etiqueta());
        estadoVisual.getElement().getThemeList().add("badge "+switch(estado){
            case VENCIDO->"error";case PAGADO->"success";case ANULADO->"contrast";default->"";
        });
        return estadoVisual;
    }
    private static DatePicker fecha(String label){
        var d=new DatePicker(label);
        d.setLocale(Locale.forLanguageTag("es-DO"));
        d.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));
        d.setClearButtonVisible(true);
        return d;
    }
    private static String formato(LocalDate fecha){return FECHA.format(fecha);}
    private static String importe(String moneda,BigDecimal valor){return moneda+" "+PosNumberFormatter.money(valor);}
}
