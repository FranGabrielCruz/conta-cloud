package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="operaciones-caja",layout=MainLayout.class)
@PageTitle("Operaciones de caja | ContaCloud")
@PermitAll
public class OperacionesCajaView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final CashRegisterSessionService service;
    private final RecuentoCajaConsultaService consultaRecuentos;
    private final RecuentoCajaPdfService pdfRecuentos;
    private final ValeCajaService valesCaja;
    private final ValeCajaPdfService pdfVales;
    private final CierreCajaService cierresCaja;
    private final CierreCajaPdfService pdfCierres;
    private final ExpedienteCierreCajaService expedienteCierres;
    private final UltimasFacturasService facturasVenta;
    private final CobroClienteService cobrosClientes;
    private final EmpresaModuloService modulos;
    private final TextField buscar=new TextField("Buscar caja");
    private final ComboBox<CajaCatalogosDto.SucursalOpcion> sucursal=new ComboBox<>("Sucursal");
    private final Select<String> estado=new Select<>();
    private final AppGrid<OperacionCajaDto> cajas=new AppGrid<>(OperacionCajaDto.class);
    private final AppPagination paginacionCajas;
    private final DatePicker desde=new DatePicker("Desde"),hasta=new DatePicker("Hasta");
    private final Select<EstadoRevisionCaja> revision=new Select<>();
    private final AppGrid<SesionCajaDto> historial=new AppGrid<>(SesionCajaDto.class);
    private final AppPagination paginacionHistorial;
    private final VerticalLayout panelOperaciones=new VerticalLayout(),panelHistorial=new VerticalLayout();
    private final TextField buscarRecuento=new TextField("Buscar");
    private final ComboBox<RecuentoCajaCatalogosDto.SucursalOpcion> sucursalRecuento=new ComboBox<>("Sucursal");
    private final ComboBox<RecuentoCajaCatalogosDto.CajaOpcion> cajaRecuento=new ComboBox<>("Caja");
    private final ComboBox<RecuentoCajaCatalogosDto.UsuarioOpcion> usuarioRecuento=new ComboBox<>("Usuario");
    private final DatePicker desdeRecuento=new DatePicker("Desde"),hastaRecuento=new DatePicker("Hasta");
    private final Select<ResultadoRecuentoCaja> resultadoRecuento=new Select<>();
    private final AppGrid<RecuentoCajaDto> recuentos=new AppGrid<>(RecuentoCajaDto.class);
    private final AppPagination paginacionRecuentos;
    private final VerticalLayout panelRecuentos=new VerticalLayout();
    private RecuentoCajaCatalogosDto catalogosRecuento;
    private RecuentoCajaCatalogosDto catalogosVale;
    private final TextField buscarVale=new TextField("Buscar");
    private final ComboBox<RecuentoCajaCatalogosDto.SucursalOpcion> sucursalVale=new ComboBox<>("Sucursal");
    private final ComboBox<RecuentoCajaCatalogosDto.CajaOpcion> cajaVale=new ComboBox<>("Caja");
    private final ComboBox<RecuentoCajaCatalogosDto.UsuarioOpcion> usuarioVale=new ComboBox<>("Usuario");
    private final Select<EstadoValeCaja> estadoVale=new Select<>();
    private final DatePicker desdeVale=new DatePicker("Desde"),hastaVale=new DatePicker("Hasta");
    private final AppGrid<ValeCajaDto> vales=new AppGrid<>(ValeCajaDto.class);
    private final AppPagination paginacionVales;
    private final VerticalLayout panelVales=new VerticalLayout();

    public OperacionesCajaView(CashRegisterSessionService service,EmpresaModuloService modulos,
            RecuentoCajaConsultaService consultaRecuentos,RecuentoCajaPdfService pdfRecuentos,
            ValeCajaService valesCaja,ValeCajaPdfService pdfVales,CierreCajaService cierresCaja,CierreCajaPdfService pdfCierres,
            ExpedienteCierreCajaService expedienteCierres,UltimasFacturasService facturasVenta,CobroClienteService cobrosClientes){
        this.service=service;this.modulos=modulos;this.consultaRecuentos=consultaRecuentos;this.pdfRecuentos=pdfRecuentos;this.valesCaja=valesCaja;this.pdfVales=pdfVales;this.cierresCaja=cierresCaja;this.pdfCierres=pdfCierres;this.expedienteCierres=expedienteCierres;this.facturasVenta=facturasVenta;this.cobrosClientes=cobrosClientes;addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        add(new AppPageHeader("OPERACIONES DE CAJA","Abre turnos, consulta el efectivo esperado y realiza cierres de caja."));
        configurarOperaciones();configurarHistorial();configurarRecuentos();configurarVales();
        paginacionCajas=new AppPagination(r->cargarCajas());paginacionHistorial=new AppPagination(r->cargarHistorial());paginacionRecuentos=new AppPagination(r->cargarRecuentos(),10,10,20,50);paginacionVales=new AppPagination(r->cargarVales(),10,10,20,50);
        panelOperaciones.setPadding(false);panelOperaciones.setSpacing(false);panelOperaciones.add(filtrosOperaciones(),cajas,paginacionCajas);
        panelHistorial.setPadding(false);panelHistorial.setSpacing(false);panelHistorial.add(filtrosHistorial(),historial,paginacionHistorial);panelHistorial.setVisible(false);
        panelRecuentos.setPadding(false);panelRecuentos.setSpacing(false);panelRecuentos.add(filtrosRecuentos(),recuentos,paginacionRecuentos);panelRecuentos.setVisible(false);
        panelVales.setPadding(false);panelVales.setSpacing(false);panelVales.add(filtrosVales(),vales,paginacionVales);panelVales.setVisible(false);
        Tab actual=new Tab("Estado actual"),historico=new Tab("Historial de cierres"),tabRecuentos=new Tab("Recuentos"),tabVales=new Tab("Vales de caja");tabVales.setVisible(puede("vales_caja.ver"));Tabs tabs=new Tabs(actual,historico,tabRecuentos,tabVales);
        tabs.addSelectedChangeListener(e->{boolean esActual=e.getSelectedTab()==actual,esHistorial=e.getSelectedTab()==historico,esRecuento=e.getSelectedTab()==tabRecuentos;panelOperaciones.setVisible(esActual);panelHistorial.setVisible(esHistorial);panelRecuentos.setVisible(esRecuento);panelVales.setVisible(e.getSelectedTab()==tabVales);if(esActual)cargarCajas();else if(esHistorial)cargarHistorial();else if(esRecuento)cargarRecuentos();else cargarVales();});
        add(tabs,panelOperaciones,panelHistorial,panelRecuentos,panelVales);
    }

    @Override public void beforeEnter(BeforeEnterEvent event){
        if(!modulos.habilitado("CAJA_BANCOS")||!puede("operaciones_caja.ver")){
            Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}
        cargarCajas();
    }

    private void configurarOperaciones(){
        buscar.setPlaceholder("Nombre de caja...");buscar.setClearButtonVisible(true);buscar.setValueChangeMode(ValueChangeMode.LAZY);buscar.setValueChangeTimeout(400);
        sucursal.setPlaceholder("Todas");sucursal.setClearButtonVisible(true);sucursal.setItemLabelGenerator(CajaCatalogosDto.SucursalOpcion::nombre);
        estado.setLabel("Estado");estado.setItems("Todas","Abiertas","Cerradas");estado.setValue("Todas");
        buscar.addValueChangeListener(e->{if(e.isFromClient()){paginacionCajas.reset();cargarCajas();}});
        sucursal.addValueChangeListener(e->{if(e.isFromClient()){paginacionCajas.reset();cargarCajas();}});
        estado.addValueChangeListener(e->{if(e.isFromClient()){paginacionCajas.reset();cargarCajas();}});
        cajas.addColumn(OperacionCajaDto::cajaNombre).setHeader("Caja").setFlexGrow(1);
        cajas.addColumn(OperacionCajaDto::sucursalNombre).setHeader("Sucursal").setFlexGrow(1);
        cajas.addColumn(OperacionCajaDto::monedaCodigo).setHeader("Moneda").setAutoWidth(true);
        cajas.addComponentColumn(i->badge(i.estadoOperativo()==EstadoSesionCaja.OPEN?"Abierta":"Cerrada",i.estadoOperativo()==EstadoSesionCaja.OPEN?"success":"contrast")).setHeader("Estado").setAutoWidth(true);
        cajas.addColumn(i->i.turnoCodigo()==null?"—":i.turnoCodigo()).setHeader("Turno").setAutoWidth(true);
        cajas.addComponentColumn(this::accionesCaja).setHeader("Acciones").setAutoWidth(true);cajas.setEmptyStateText("No hay cajas disponibles.");
    }

    private void configurarHistorial(){
        desde.setLocale(new Locale("es","DO"));hasta.setLocale(new Locale("es","DO"));
        revision.setLabel("Revisión");revision.setItems(EstadoRevisionCaja.values());revision.setEmptySelectionAllowed(true);revision.setEmptySelectionCaption("Todas");
        desde.addValueChangeListener(e->{if(e.isFromClient()){paginacionHistorial.reset();cargarHistorial();}});
        hasta.addValueChangeListener(e->{if(e.isFromClient()){paginacionHistorial.reset();cargarHistorial();}});
        revision.addValueChangeListener(e->{if(e.isFromClient()){paginacionHistorial.reset();cargarHistorial();}});
        historial.addColumn(SesionCajaDto::codigoVisible).setHeader("Turno").setFlexGrow(1);
        historial.addColumn(i->DATE.format(i.fechaOperativa())).setHeader("Fecha").setAutoWidth(true);
        historial.addColumn(SesionCajaDto::cajaNombre).setHeader("Caja").setFlexGrow(1);
        historial.addColumn(SesionCajaDto::cajeroNombre).setHeader("Cajero").setFlexGrow(1);
        historial.addColumn(i->dinero(i.efectivoEsperado())).setHeader("Esperado").setAutoWidth(true);
        historial.addColumn(i->dinero(i.efectivoContado())).setHeader("Contado").setAutoWidth(true);
        historial.addColumn(i->dinero(i.diferencia())).setHeader("Diferencia").setAutoWidth(true);
        historial.addComponentColumn(i->badgeRevision(i.estadoRevision())).setHeader("Revisión").setAutoWidth(true);
        historial.addComponentColumn(this::accionesHistorial).setHeader("Acciones").setAutoWidth(true);historial.setEmptyStateText("No hay cierres registrados.");
    }

    private void configurarRecuentos(){
        buscarRecuento.setPlaceholder("Número, caja o usuario...");buscarRecuento.setClearButtonVisible(true);
        buscarRecuento.setValueChangeMode(ValueChangeMode.LAZY);buscarRecuento.setValueChangeTimeout(400);
        sucursalRecuento.setPlaceholder("Todas");sucursalRecuento.setClearButtonVisible(true);sucursalRecuento.setItemLabelGenerator(RecuentoCajaCatalogosDto.SucursalOpcion::nombre);
        cajaRecuento.setPlaceholder("Todas");cajaRecuento.setClearButtonVisible(true);cajaRecuento.setItemLabelGenerator(RecuentoCajaCatalogosDto.CajaOpcion::nombre);
        usuarioRecuento.setPlaceholder("Todos");usuarioRecuento.setClearButtonVisible(true);usuarioRecuento.setItemLabelGenerator(RecuentoCajaCatalogosDto.UsuarioOpcion::nombre);
        desdeRecuento.setLocale(new Locale("es","DO"));hastaRecuento.setLocale(new Locale("es","DO"));
        desdeRecuento.setPlaceholder("DD/MM/YYYY");hastaRecuento.setPlaceholder("DD/MM/YYYY");desdeRecuento.setClearButtonVisible(true);hastaRecuento.setClearButtonVisible(true);
        resultadoRecuento.setLabel("Resultado");resultadoRecuento.setItems(ResultadoRecuentoCaja.values());resultadoRecuento.setEmptySelectionAllowed(true);resultadoRecuento.setEmptySelectionCaption("Todos");resultadoRecuento.setItemLabelGenerator(OperacionesCajaView::resultadoCorto);
        buscarRecuento.addValueChangeListener(e->{if(e.isFromClient())reiniciarRecuentos();});
        sucursalRecuento.addValueChangeListener(e->{if(e.isFromClient()){actualizarCajasRecuento();reiniciarRecuentos();}});
        cajaRecuento.addValueChangeListener(e->{if(e.isFromClient())reiniciarRecuentos();});
        usuarioRecuento.addValueChangeListener(e->{if(e.isFromClient())reiniciarRecuentos();});
        desdeRecuento.addValueChangeListener(e->{if(e.isFromClient())reiniciarRecuentos();});
        hastaRecuento.addValueChangeListener(e->{if(e.isFromClient())reiniciarRecuentos();});
        resultadoRecuento.addValueChangeListener(e->{if(e.isFromClient())reiniciarRecuentos();});
        recuentos.addColumn(RecuentoCajaDto::numero).setHeader("Número").setAutoWidth(true);
        recuentos.addColumn(x->x.contadoEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).setHeader("Fecha/Hora").setAutoWidth(true);
        recuentos.addColumn(RecuentoCajaDto::caja).setHeader("Caja").setFlexGrow(1);
        recuentos.addColumn(RecuentoCajaDto::sucursal).setHeader("Sucursal").setFlexGrow(1);
        recuentos.addColumn(RecuentoCajaDto::usuario).setHeader("Usuario").setFlexGrow(1);
        recuentos.addColumn(x->importeRecuento(x,x.esperado())).setHeader("Esperado").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        recuentos.addColumn(x->importeRecuento(x,x.contado())).setHeader("Contado").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        recuentos.addColumn(x->diferenciaRecuento(x,x.diferencia())).setHeader("Diferencia").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        recuentos.addComponentColumn(x->badgeResultado(x.resultado())).setHeader("Resultado").setAutoWidth(true);
        recuentos.addComponentColumn(x->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver recuento",e->verRecuento(x.id()))).setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        recuentos.setEmptyStateText("No se encontraron recuentos de caja.");
    }

    private void configurarVales(){
        buscarVale.setPlaceholder("Número, beneficiario, concepto o referencia...");buscarVale.setClearButtonVisible(true);buscarVale.setValueChangeMode(ValueChangeMode.LAZY);buscarVale.setValueChangeTimeout(400);
        sucursalVale.setPlaceholder("Todas");sucursalVale.setClearButtonVisible(true);sucursalVale.setItemLabelGenerator(RecuentoCajaCatalogosDto.SucursalOpcion::nombre);
        cajaVale.setPlaceholder("Todas");cajaVale.setClearButtonVisible(true);cajaVale.setItemLabelGenerator(RecuentoCajaCatalogosDto.CajaOpcion::nombre);
        usuarioVale.setPlaceholder("Todos");usuarioVale.setClearButtonVisible(true);usuarioVale.setItemLabelGenerator(RecuentoCajaCatalogosDto.UsuarioOpcion::nombre);
        estadoVale.setLabel("Estado");estadoVale.setItems(EstadoValeCaja.values());estadoVale.setEmptySelectionAllowed(true);estadoVale.setEmptySelectionCaption("Todos");estadoVale.setItemLabelGenerator(x->x==EstadoValeCaja.VOIDED?"Anulado":"Confirmado");
        desdeVale.setLocale(new Locale("es","DO"));hastaVale.setLocale(new Locale("es","DO"));
        buscarVale.addValueChangeListener(e->{if(e.isFromClient())reiniciarVales();});sucursalVale.addValueChangeListener(e->{if(e.isFromClient()){actualizarCajasVale();reiniciarVales();}});cajaVale.addValueChangeListener(e->{if(e.isFromClient())reiniciarVales();});usuarioVale.addValueChangeListener(e->{if(e.isFromClient())reiniciarVales();});estadoVale.addValueChangeListener(e->{if(e.isFromClient())reiniciarVales();});desdeVale.addValueChangeListener(e->{if(e.isFromClient())reiniciarVales();});hastaVale.addValueChangeListener(e->{if(e.isFromClient())reiniciarVales();});
        vales.addColumn(ValeCajaDto::numero).setHeader("Número").setAutoWidth(true);vales.addColumn(x->x.fechaHora().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).setHeader("Fecha/Hora").setAutoWidth(true);vales.addColumn(ValeCajaDto::caja).setHeader("Caja").setFlexGrow(1);vales.addColumn(ValeCajaDto::sucursal).setHeader("Sucursal").setFlexGrow(1);vales.addColumn(ValeCajaDto::usuario).setHeader("Usuario").setFlexGrow(1);vales.addColumn(ValeCajaDto::beneficiario).setHeader("Beneficiario").setFlexGrow(1);vales.addColumn(ValeCajaDto::concepto).setHeader("Concepto").setFlexGrow(1);vales.addColumn(x->x.monedaCodigo()+" "+PosNumberFormatter.money(x.importe())).setHeader("Importe").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);vales.addComponentColumn(x->badge(x.estado()==EstadoValeCaja.VOIDED?"Anulado":"Confirmado",x.estado()==EstadoValeCaja.VOIDED?"error":"success")).setHeader("Estado").setAutoWidth(true);vales.addComponentColumn(x->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver vale",e->verVale(x.id()))).setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);vales.setEmptyStateText("No se encontraron vales de caja.");
    }

    private HorizontalLayout filtrosOperaciones(){HorizontalLayout h=new HorizontalLayout(buscar,sucursal,estado);h.addClassName("cc-filter-bar");h.setAlignItems(Alignment.END);return h;}
    private HorizontalLayout filtrosHistorial(){HorizontalLayout h=new HorizontalLayout(desde,hasta,revision);h.addClassName("cc-filter-bar");h.setAlignItems(Alignment.END);return h;}
    private Div filtrosRecuentos(){Div d=new Div(buscarRecuento,sucursalRecuento,cajaRecuento,usuarioRecuento,desdeRecuento,hastaRecuento,resultadoRecuento);d.addClassNames("cc-filter-bar","cc-cash-count-filters");return d;}
    private Div filtrosVales(){Div d=new Div(buscarVale,sucursalVale,cajaVale,usuarioVale,estadoVale,desdeVale,hastaVale);d.addClassNames("cc-filter-bar","cc-cash-voucher-filters");return d;}
    private Component accionesCaja(OperacionCajaDto item){HorizontalLayout h=acciones();
        if(item.estadoOperativo()==EstadoSesionCaja.OPEN){h.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver turno",e->detalle(item.sesionId())));
            if(puede("operaciones_caja.cerrar")||puede("cierres_caja.ejecutar"))h.add(new AppActionButton(ActionType.CLOSE_CASH,ButtonSize.GRID_ACTION,"Cerrar caja",e->cerrar(item.sesionId())));
        }else if(item.activa()&&puede("operaciones_caja.abrir"))h.add(new AppActionButton(ActionType.OPEN_CASH,ButtonSize.GRID_ACTION,"Abrir caja",e->abrir(item)));
        return h;}
    private Component accionesHistorial(SesionCajaDto item){HorizontalLayout h=acciones();h.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver cierre",e->verCierre(item)));
        if(puedeRevisar(item.estadoRevision(),puede("operaciones_caja.revisar_cierre")))
            h.add(new AppActionButton(ActionType.REVIEW,ButtonSize.GRID_ACTION,"Revisar cierre",e->revisar(item.id())));
        return h;}

    private void cargarCajas(){try{if(sucursal.getDataProvider().size(new com.vaadin.flow.data.provider.Query<>())==0)sucursal.setItems(service.sucursalesOperables());var r=paginacionCajas.currentRequest();var page=service.buscarOperaciones(buscar.getValue(),sucursal.getValue()==null?null:sucursal.getValue().id(),estadoFiltro(),r.page(),r.size(),"caja",true);cajas.setItems(page.getContent());paginacionCajas.setTotal(page.getTotalElements());}catch(RuntimeException ex){error(ex,"No fue posible cargar las operaciones de caja.");}}
    private void cargarHistorial(){if(!puede("operaciones_caja.ver_historial")){historial.setEmptyStateText("No tienes permiso para consultar el historial.");historial.setItems(List.of());return;}try{var r=paginacionHistorial.currentRequest();var page=service.buscarHistorial("",desde.getValue(),hasta.getValue(),null,revision.getValue(),r.page(),r.size());historial.setItems(page.getContent());paginacionHistorial.setTotal(page.getTotalElements());}catch(RuntimeException ex){error(ex,"No fue posible cargar el historial.");}}
    private void cargarRecuentos(){
        if(!puede("recuentos_caja.ver")){recuentos.setEmptyStateText("No tienes permiso para consultar los recuentos.");recuentos.setItems(List.of());paginacionRecuentos.setTotal(0);return;}
        recuentos.getElement().setAttribute("aria-busy","true");recuentos.setEnabled(false);
        try{
            if(catalogosRecuento==null){catalogosRecuento=consultaRecuentos.catalogos();sucursalRecuento.setItems(catalogosRecuento.sucursales());usuarioRecuento.setItems(catalogosRecuento.usuarios());actualizarCajasRecuento();}
            var r=paginacionRecuentos.currentRequest();var page=consultaRecuentos.buscar(buscarRecuento.getValue(),
                id(sucursalRecuento.getValue()),id(cajaRecuento.getValue()),id(usuarioRecuento.getValue()),
                desdeRecuento.getValue(),hastaRecuento.getValue(),resultadoRecuento.getValue(),r.page(),r.size());
            recuentos.setEmptyStateText(tieneFiltrosRecuento()?"No se encontraron recuentos con los filtros seleccionados.":"No se encontraron recuentos de caja.");
            recuentos.setItems(page.getContent());paginacionRecuentos.setTotal(page.getTotalElements());
        }catch(RuntimeException ex){recuentos.setItems(List.of());paginacionRecuentos.setTotal(0);UiErrorHandler.show(ex,"No fue posible consultar los recuentos de caja.");}
        finally{recuentos.getElement().removeAttribute("aria-busy");recuentos.setEnabled(true);}
    }
    private void cargarVales(){if(!puede("vales_caja.ver")){vales.setItems(List.of());paginacionVales.setTotal(0);return;}try{if(catalogosVale==null)catalogosVale=valesCaja.catalogos();sucursalVale.setItems(catalogosVale.sucursales());usuarioVale.setItems(catalogosVale.usuarios());actualizarCajasVale();var r=paginacionVales.currentRequest();var page=valesCaja.buscarAdmin(buscarVale.getValue(),id(sucursalVale.getValue()),id(cajaVale.getValue()),id(usuarioVale.getValue()),estadoVale.getValue(),desdeVale.getValue(),hastaVale.getValue(),r.page(),r.size());vales.setItems(page.getContent());paginacionVales.setTotal(page.getTotalElements());}catch(RuntimeException ex){vales.setItems(List.of());paginacionVales.setTotal(0);UiErrorHandler.show(ex,"No fue posible consultar los vales de caja.");}}
    private void reiniciarVales(){paginacionVales.reset();cargarVales();}
    private void actualizarCajasVale(){if(catalogosVale==null)return;UUID sid=id(sucursalVale.getValue()),seleccion=id(cajaVale.getValue());var disponibles=catalogosVale.cajas().stream().filter(x->sid==null||sid.equals(x.sucursalId())).toList();cajaVale.setItems(disponibles);if(seleccion!=null&&disponibles.stream().noneMatch(x->seleccion.equals(x.id())))cajaVale.clear();}
    private void reiniciarRecuentos(){paginacionRecuentos.reset();cargarRecuentos();}
    private void actualizarCajasRecuento(){if(catalogosRecuento==null)return;UUID sucursalId=id(sucursalRecuento.getValue());UUID seleccion=id(cajaRecuento.getValue());var disponibles=catalogosRecuento.cajas().stream().filter(x->sucursalId==null||sucursalId.equals(x.sucursalId())).toList();cajaRecuento.setItems(disponibles);if(seleccion!=null&&disponibles.stream().noneMatch(x->seleccion.equals(x.id())))cajaRecuento.clear();}
    private boolean tieneFiltrosRecuento(){return !buscarRecuento.getValue().isBlank()||sucursalRecuento.getValue()!=null||cajaRecuento.getValue()!=null||usuarioRecuento.getValue()!=null||desdeRecuento.getValue()!=null||hastaRecuento.getValue()!=null||resultadoRecuento.getValue()!=null;}
    private Boolean estadoFiltro(){return switch(estado.getValue()==null?"Todas":estado.getValue()){case "Abiertas"->true;case "Cerradas"->false;default->null;};}

    private void abrir(OperacionCajaDto item){try{Dialog d=dialog("ABRIR CAJA");BigDecimalField fondo=new BigDecimalField("Fondo inicial");fondo.setRequiredIndicatorVisible(true);fondo.setValue(BigDecimal.ZERO);TextArea nota=new TextArea("Observación");nota.setMaxLength(500);d.add(new AppDetailSection("Información").field("Caja",item.cajaNombre()).field("Sucursal",item.sucursalNombre()).field("Moneda",item.monedaCodigo()).field("Fecha operativa",DATE.format(service.fechaOperativaActual())),form(fondo,nota));AppActionButton confirmar=new AppActionButton(ActionType.OPEN_CASH,ButtonSize.MAIN,"Abrir caja",e->{try{service.abrir(item.cajaId(),new AperturaCajaInput(fondo.getValue(),nota.getValue()));d.close();cargarCajas();Notification.show("Caja abierta correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible abrir la caja.");}});d.getFooter().add(confirmar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}catch(RuntimeException ex){error(ex,"No fue posible preparar la apertura.");}}
    private void cerrar(UUID sesionId){try{new CierreCajaDialog(sesionId,cierresCaja,c->{cargarCajas();cargarHistorial();Notification.show("Caja cerrada correctamente.");},this::imprimirCierre).open();}catch(RuntimeException ex){error(ex,"No fue posible preparar el cierre.");}}
    private void detalle(UUID id){try{SesionCajaDto s=service.obtener(id);ResumenTurnoCajaDto r=service.resumen(id);Dialog d=dialog("TURNO DE CAJA");VerticalLayout resumen=new VerticalLayout(new AppDetailSection("Información").field("Turno",s.codigoVisible()).field("Caja",s.cajaNombre()).field("Sucursal",s.sucursalNombre()).field("Fecha operativa",DATE.format(s.fechaOperativa())).field("Cajero",s.cajeroNombre()).field("Moneda",s.monedaCodigo()).field("Estado",s.estado()==EstadoSesionCaja.OPEN?"Abierto":"Cerrado").field("Fondo inicial",dinero(s.fondoInicial())).field("Entradas en efectivo",dinero(r.entradasEfectivo())).field("Salidas en efectivo",dinero(r.salidasEfectivo())).field("Tarjeta",dinero(r.entradasTarjeta())).field("Transferencia bancaria",dinero(r.entradasTransferencia())).field("Otras entradas",dinero(r.otrasEntradas())).field("Total entradas",dinero(r.totalEntradas())).field("Efectivo esperado",dinero(s.estado()==EstadoSesionCaja.CLOSED?s.efectivoEsperado():r.efectivoEsperado())).field("Efectivo contado",dinero(s.efectivoContado())).field("Diferencia",dinero(s.diferencia())).field("Revisión",revision(s.estadoRevision())));resumen.setPadding(false);AppGrid<MovimientoTurnoCajaDto> grid=new AppGrid<>(MovimientoTurnoCajaDto.class);grid.addColumn(m->m.fechaHora()==null?"—":m.fechaHora().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))).setHeader("Hora");grid.addColumn(m->m.tipo()==TipoMovimientoFinanciero.INCOME?"Entrada":"Salida").setHeader("Tipo");grid.addColumn(m->m.medioPago().getEtiqueta()).setHeader("Medio");grid.addColumn(MovimientoTurnoCajaDto::concepto).setHeader("Concepto").setFlexGrow(1);grid.addColumn(m->(m.tipo()==TipoMovimientoFinanciero.INCOME?"+":"-")+dinero(m.monto())).setHeader("Monto");AppPagination[] holder=new AppPagination[1];holder[0]=new AppPagination(req->{var page=service.movimientos(id,req.page(),req.size());grid.setItems(page.getContent());holder[0].setTotal(page.getTotalElements());});VerticalLayout movimientosPanel=new VerticalLayout(grid,holder[0]);movimientosPanel.setPadding(false);movimientosPanel.setVisible(false);Tab tabResumen=new Tab("Resumen"),tabMovimientos=new Tab("Movimientos");Tabs tabs=new Tabs(tabResumen,tabMovimientos);tabs.addSelectedChangeListener(e->{boolean show=e.getSelectedTab()==tabResumen;resumen.setVisible(show);movimientosPanel.setVisible(!show);if(!show){var page=service.movimientos(id,0,10);grid.setItems(page.getContent());holder[0].setTotal(page.getTotalElements());}});d.add(tabs,resumen,movimientosPanel);d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();}catch(RuntimeException ex){error(ex,"No fue posible consultar el turno.");}}
    private void verCierre(SesionCajaDto sesion){try{CierreCajaDto c=cierresCaja.obtenerPorSesion(sesion.id());new ExpedienteCierreCajaDialog(c,expedienteCierres,
        puede("facturas.ver"),puede("recibos.ver"),puede("movimientos_caja.ver"),puede("recuentos_caja.ver"),puede("cierres_caja.imprimir"),
        this::imprimirCierre,this::verFacturaTurno,this::verReciboTurno,id->verMovimientoTurno(c.id(),id),this::verRecuento).open();
        }catch(RecursoNoEncontradoException legacy){detalle(sesion.id());}catch(RuntimeException ex){error(ex,"No fue posible consultar el cierre.");}}
    private void verFacturaTurno(UUID id){try{FacturaVentaDetalleDto f=facturasVenta.detalle(id);Dialog d=dialog("FACTURA "+f.numero());
        AppDetailSection info=new AppDetailSection("INFORMACIÓN GENERAL").field("Fecha/Hora",f.fechaHora().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
            .field("Cliente",f.cliente()).field("RNC/Cédula",valor(f.identificacion())).field("Estado",f.estado()==EstadoFacturaVenta.VOIDED?"Anulada":"Pagada")
            .field("Moneda",f.moneda()).field("Condición de pago",f.condicionPago()).field("Vendedor",f.vendedor());
        AppGrid<FacturaVentaDetalleDto.Linea> lineas=new AppGrid<>(FacturaVentaDetalleDto.Linea.class);lineas.addColumn(FacturaVentaDetalleDto.Linea::producto).setHeader("Producto").setFlexGrow(1);
        lineas.addColumn(FacturaVentaDetalleDto.Linea::unidad).setHeader("Unidad").setAutoWidth(true);lineas.addColumn(x->PosNumberFormatter.quantity(x.cantidad())).setHeader("Cantidad").setAutoWidth(true);
        lineas.addColumn(x->PosNumberFormatter.money(x.precio())).setHeader("Precio").setAutoWidth(true);lineas.addColumn(x->PosNumberFormatter.money(x.impuesto())).setHeader("Impuesto").setAutoWidth(true);
        lineas.addColumn(x->PosNumberFormatter.money(x.total())).setHeader("Total").setAutoWidth(true);lineas.setItems(f.lineas());
        AppDetailSection totales=new AppDetailSection("TOTALES").field("Subtotal",f.moneda()+" "+PosNumberFormatter.money(f.subtotal()))
            .field("Descuento",f.moneda()+" "+PosNumberFormatter.money(f.descuento())).field("Recargo",f.moneda()+" "+PosNumberFormatter.money(f.recargo()))
            .field("Impuestos",f.moneda()+" "+PosNumberFormatter.money(f.impuestos())).field("Total",f.moneda()+" "+PosNumberFormatter.money(f.total()));
        d.add(info,new H4("PRODUCTOS"),lineas,totales);d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
        }catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible consultar la factura.");}}
    private void verReciboTurno(UUID id){try{CobroClienteDto x=cobrosClientes.detalle(id);Dialog d=dialog("RECIBO "+x.numero());
        AppDetailSection info=new AppDetailSection("INFORMACIÓN").field("Cliente",x.cliente()).field("Fecha",DATE.format(x.fecha()))
            .field("Moneda",x.moneda()).field("Recibido",x.moneda()+" "+PosNumberFormatter.money(x.recibido()))
            .field("Aplicado",x.moneda()+" "+PosNumberFormatter.money(x.aplicado())).field("Disponible",x.moneda()+" "+PosNumberFormatter.money(x.disponible()))
            .field("Referencia",valor(x.referencia())).field("Observación",valor(x.observacion()));
        AppGrid<CobroClienteDto.Aplicacion> apps=new AppGrid<>(CobroClienteDto.Aplicacion.class);apps.addColumn(CobroClienteDto.Aplicacion::factura).setHeader("Factura");
        apps.addColumn(a->PosNumberFormatter.money(a.monto())).setHeader("Aplicado");apps.addColumn(a->PosNumberFormatter.money(a.saldoRestante())).setHeader("Saldo restante");apps.setItems(x.aplicaciones());
        AppGrid<CobroClienteDto.Medio> medios=new AppGrid<>(CobroClienteDto.Medio.class);medios.addColumn(CobroClienteDto.Medio::medio).setHeader("Medio");
        medios.addColumn(CobroClienteDto.Medio::destino).setHeader("Destino");medios.addColumn(CobroClienteDto.Medio::referencia).setHeader("Referencia");
        medios.addColumn(m->PosNumberFormatter.money(m.monto())).setHeader("Monto");medios.setItems(x.medios());
        d.add(info,new H4("APLICACIONES"),apps,new H4("MEDIOS DE PAGO"),medios);d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
        }catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible consultar el recibo.");}}
    private void verMovimientoTurno(UUID cierreId,UUID movimientoId){try{MovimientoCajaDto x=expedienteCierres.movimiento(cierreId,movimientoId);Dialog d=dialog("MOVIMIENTO "+x.numero());
        d.add(new AppDetailSection("INFORMACIÓN").field("Fecha/Hora",x.fechaHora()==null?"—":x.fechaHora().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
            .field("Tipo",x.tipo()==TipoMovimientoFinanciero.INCOME?"Entrada":"Salida").field("Concepto",x.concepto()).field("Origen",x.origen().name().replace('_',' '))
            .field("Referencia",valor(x.referencia())).field("Monto",x.monedaCodigo()+" "+PosNumberFormatter.money(x.importe()))
            .field("Saldo",x.monedaCodigo()+" "+PosNumberFormatter.money(x.saldo())).field("Estado",x.estado()==EstadoMovimientoFinanciero.VOIDED?"Anulado":"Registrado")
            .field("Observación",valor(x.observacion())));d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
        }catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible consultar el movimiento.");}}
    private void revisar(UUID id){Dialog d=dialog("REVISAR CIERRE");Select<EstadoRevisionCaja> resultado=new Select<>();resultado.setLabel("Resultado");resultado.setItems(EstadoRevisionCaja.APPROVED,EstadoRevisionCaja.REQUIRES_REVIEW);resultado.setItemLabelGenerator(OperacionesCajaView::revision);TextArea nota=new TextArea("Observación");nota.setMaxLength(500);d.add(form(resultado,nota));d.getFooter().add(new AppActionButton(ActionType.REVIEW,ButtonSize.MAIN,"Guardar revisión",e->{try{service.revisar(id,new RevisionCierreCajaInput(resultado.getValue(),nota.getValue()));d.close();cargarHistorial();Notification.show("Cierre revisado correctamente.");}catch(RuntimeException ex){cargarHistorial();error(ex,"No fue posible revisar el cierre.");}}),new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}

    private void verRecuento(UUID id){try{new DetalleRecuentoCajaDialog(consultaRecuentos.obtener(id),puede("recuentos_caja.imprimir"),this::imprimirRecuento).open();}catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible cargar el recuento.");}}
    private void imprimirRecuento(UUID id){try{ArchivoPdfDto archivo=pdfRecuentos.generar(id);StreamResource recurso=new StreamResource(archivo.nombreArchivo(),()->new ByteArrayInputStream(archivo.contenido()));recurso.setContentType("application/pdf");recurso.setCacheTime(0);var registro=VaadinSession.getCurrent().getResourceRegistry().registerResource(recurso);UI.getCurrent().getPage().open(registro.getResourceUri().toString(),"_blank");}catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible generar el comprobante de recuento.");}}
    private void imprimirCierre(UUID id){try{ArchivoPdfDto archivo=pdfCierres.generar(id);StreamResource recurso=new StreamResource(archivo.nombreArchivo(),()->new ByteArrayInputStream(archivo.contenido()));recurso.setContentType("application/pdf");recurso.setCacheTime(0);var registro=VaadinSession.getCurrent().getResourceRegistry().registerResource(recurso);UI.getCurrent().getPage().open(registro.getResourceUri().toString(),"_blank");}catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible generar el comprobante de cierre.");}}
    private void verVale(UUID id){try{ValeCajaDto v=valesCaja.obtener(id);Dialog d=dialog("VALE DE CAJA "+v.numero());d.add(new AppDetailSection("Información").field("Estado",v.estado()==EstadoValeCaja.VOIDED?"Anulado":"Confirmado").field("Fecha/Hora",v.fechaHora().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).field("Caja",v.caja()).field("Sucursal",v.sucursal()).field("Usuario",v.usuario()).field("Beneficiario",v.beneficiario()).field("Concepto",v.concepto()).field("Referencia",v.referencia()==null?"—":v.referencia()).field("Importe",v.monedaCodigo()+" "+PosNumberFormatter.money(v.importe())).field("Observación",v.observacion()==null?"—":v.observacion()).field("Motivo de anulación",v.motivoAnulacion()==null?"—":v.motivoAnulacion()));if(puede("vales_caja.imprimir"))d.getFooter().add(new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Reimprimir vale",e->imprimirVale(v.id())));if(puede("vales_caja.anular")&&v.anulable())d.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular",e->anularVale(d,v.id())));d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();}catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible cargar el vale de caja.");}}
    private void imprimirVale(UUID id){try{ArchivoPdfDto archivo=pdfVales.generar(id);StreamResource recurso=new StreamResource(archivo.nombreArchivo(),()->new ByteArrayInputStream(archivo.contenido()));recurso.setContentType("application/pdf");recurso.setCacheTime(0);var registro=VaadinSession.getCurrent().getResourceRegistry().registerResource(recurso);UI.getCurrent().getPage().open(registro.getResourceUri().toString(),"_blank");}catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible generar el comprobante del vale.");}}
    private void anularVale(Dialog detalle,UUID id){Dialog d=dialog("ANULAR VALE");TextArea motivo=new TextArea("Motivo de anulación");motivo.setRequiredIndicatorVisible(true);motivo.setMaxLength(500);motivo.setWidthFull();d.add(motivo);UUID clave=UUID.randomUUID();d.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular vale",e->{try{valesCaja.anular(new AnulacionValeCajaInput(id,motivo.getValue(),clave));d.close();detalle.close();cargarVales();Notification.show("Vale de caja anulado correctamente.");}catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible anular el vale de caja.");}}),new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}

    private static HorizontalLayout acciones(){HorizontalLayout h=new HorizontalLayout();h.addClassName("cc-grid-actions");h.setPadding(false);h.setSpacing(false);return h;}
    private static Dialog dialog(String titulo){Dialog d=new Dialog();d.setHeaderTitle(titulo);d.setWidth("min(800px, 96vw)");return d;}
    private static Div form(Component... campos){Div d=new Div(campos);d.addClassName("cc-dialog-form");return d;}
    private static Span badge(String texto,String tema){Span s=new Span(texto);s.getElement().getThemeList().add("badge "+tema);return s;}
    static Span badgeRevision(EstadoRevisionCaja estado){String tema=estado==EstadoRevisionCaja.APPROVED?"success":estado==EstadoRevisionCaja.REQUIRES_REVIEW?"error":"contrast";Span s=badge(revision(estado),tema);s.addClassName("cc-review-badge");return s;}
    static boolean puedeRevisar(EstadoRevisionCaja estado,boolean tienePermiso){return tienePermiso&&(estado==EstadoRevisionCaja.PENDING||estado==EstadoRevisionCaja.REQUIRES_REVIEW);}
    static Span badgeResultado(ResultadoRecuentoCaja resultado){String tema=resultado==ResultadoRecuentoCaja.BALANCED?"success":resultado==ResultadoRecuentoCaja.SHORTAGE?"error":"warning";Span s=badge(resultadoCorto(resultado),tema);s.addClassName("cc-cash-count-status");return s;}
    static String resultadoCorto(ResultadoRecuentoCaja resultado){
        if(resultado==null)return "Todos";
        return switch(resultado){case BALANCED->"Cuadre";case SHORTAGE->"Faltante";case OVERAGE->"Sobrante";};
    }
    private static String importeRecuento(RecuentoCajaDto r,BigDecimal valor){return r.monedaCodigo()+" "+PosNumberFormatter.money(valor);}
    private static String diferenciaRecuento(RecuentoCajaDto r,BigDecimal valor){return r.monedaCodigo()+" "+(valor.signum()>0?"+":"")+PosNumberFormatter.money(valor);}
    private static String valor(String texto){return texto==null||texto.isBlank()?"—":texto;}
    private static UUID id(Object opcion){if(opcion instanceof RecuentoCajaCatalogosDto.SucursalOpcion x)return x.id();if(opcion instanceof RecuentoCajaCatalogosDto.CajaOpcion x)return x.id();if(opcion instanceof RecuentoCajaCatalogosDto.UsuarioOpcion x)return x.id();return null;}
    private static String dinero(BigDecimal v){return v==null?"—":String.format(Locale.US,"%,.2f",v);}
    private static String revision(EstadoRevisionCaja e){return switch(e){case APPROVED->"Aprobado";case REQUIRES_REVIEW->"Requiere revisión";default->"Pendiente";};}
    private static boolean puede(String permiso){return TenantContext.principalActual().permisos().contains(permiso);}
    private static void error(RuntimeException ex,String fallback){String m=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():fallback;Notification.show(m==null||m.isBlank()?fallback:m);}
}
