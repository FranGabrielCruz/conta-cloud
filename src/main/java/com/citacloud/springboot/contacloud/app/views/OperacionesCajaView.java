package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
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
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="operaciones-caja",layout=MainLayout.class)
@PageTitle("Operaciones de caja | ContaCloud")
@PermitAll
public class OperacionesCajaView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final CashRegisterSessionService service;
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

    public OperacionesCajaView(CashRegisterSessionService service,EmpresaModuloService modulos){
        this.service=service;this.modulos=modulos;addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        add(new AppPageHeader("OPERACIONES DE CAJA","Abre turnos, consulta el efectivo esperado y realiza cierres de caja."));
        configurarOperaciones();configurarHistorial();
        paginacionCajas=new AppPagination(r->cargarCajas());paginacionHistorial=new AppPagination(r->cargarHistorial());
        panelOperaciones.setPadding(false);panelOperaciones.setSpacing(false);panelOperaciones.add(filtrosOperaciones(),cajas,paginacionCajas);
        panelHistorial.setPadding(false);panelHistorial.setSpacing(false);panelHistorial.add(filtrosHistorial(),historial,paginacionHistorial);panelHistorial.setVisible(false);
        Tab actual=new Tab("Estado actual"),historico=new Tab("Historial de cierres");Tabs tabs=new Tabs(actual,historico);
        tabs.addSelectedChangeListener(e->{boolean esActual=e.getSelectedTab()==actual;panelOperaciones.setVisible(esActual);panelHistorial.setVisible(!esActual);if(esActual)cargarCajas();else cargarHistorial();});
        add(tabs,panelOperaciones,panelHistorial);
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

    private HorizontalLayout filtrosOperaciones(){HorizontalLayout h=new HorizontalLayout(buscar,sucursal,estado);h.addClassName("cc-filter-bar");h.setAlignItems(Alignment.END);return h;}
    private HorizontalLayout filtrosHistorial(){HorizontalLayout h=new HorizontalLayout(desde,hasta,revision);h.addClassName("cc-filter-bar");h.setAlignItems(Alignment.END);return h;}
    private Component accionesCaja(OperacionCajaDto item){HorizontalLayout h=acciones();
        if(item.estadoOperativo()==EstadoSesionCaja.OPEN){h.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver turno",e->detalle(item.sesionId())));
            if(puede("operaciones_caja.cerrar"))h.add(new AppActionButton(ActionType.CLOSE_CASH,ButtonSize.GRID_ACTION,"Cerrar caja",e->cerrar(item.sesionId())));
        }else if(item.activa()&&puede("operaciones_caja.abrir"))h.add(new AppActionButton(ActionType.OPEN_CASH,ButtonSize.GRID_ACTION,"Abrir caja",e->abrir(item)));
        return h;}
    private Component accionesHistorial(SesionCajaDto item){HorizontalLayout h=acciones();h.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver cierre",e->detalle(item.id())));
        if(puedeRevisar(item.estadoRevision(),puede("operaciones_caja.revisar_cierre")))
            h.add(new AppActionButton(ActionType.REVIEW,ButtonSize.GRID_ACTION,"Revisar cierre",e->revisar(item.id())));
        return h;}

    private void cargarCajas(){try{if(sucursal.getDataProvider().size(new com.vaadin.flow.data.provider.Query<>())==0)sucursal.setItems(service.sucursalesOperables());var r=paginacionCajas.currentRequest();var page=service.buscarOperaciones(buscar.getValue(),sucursal.getValue()==null?null:sucursal.getValue().id(),estadoFiltro(),r.page(),r.size(),"caja",true);cajas.setItems(page.getContent());paginacionCajas.setTotal(page.getTotalElements());}catch(RuntimeException ex){error(ex,"No fue posible cargar las operaciones de caja.");}}
    private void cargarHistorial(){if(!puede("operaciones_caja.ver_historial")){historial.setEmptyStateText("No tienes permiso para consultar el historial.");historial.setItems(List.of());return;}try{var r=paginacionHistorial.currentRequest();var page=service.buscarHistorial("",desde.getValue(),hasta.getValue(),null,revision.getValue(),r.page(),r.size());historial.setItems(page.getContent());paginacionHistorial.setTotal(page.getTotalElements());}catch(RuntimeException ex){error(ex,"No fue posible cargar el historial.");}}
    private Boolean estadoFiltro(){return switch(estado.getValue()==null?"Todas":estado.getValue()){case "Abiertas"->true;case "Cerradas"->false;default->null;};}

    private void abrir(OperacionCajaDto item){try{Dialog d=dialog("ABRIR CAJA");BigDecimalField fondo=new BigDecimalField("Fondo inicial");fondo.setRequiredIndicatorVisible(true);fondo.setValue(BigDecimal.ZERO);TextArea nota=new TextArea("Observación");nota.setMaxLength(500);d.add(new AppDetailSection("Información").field("Caja",item.cajaNombre()).field("Sucursal",item.sucursalNombre()).field("Moneda",item.monedaCodigo()).field("Fecha operativa",DATE.format(service.fechaOperativaActual())),form(fondo,nota));AppActionButton confirmar=new AppActionButton(ActionType.OPEN_CASH,ButtonSize.MAIN,"Abrir caja",e->{try{service.abrir(item.cajaId(),new AperturaCajaInput(fondo.getValue(),nota.getValue()));d.close();cargarCajas();Notification.show("Caja abierta correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible abrir la caja.");}});d.getFooter().add(confirmar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}catch(RuntimeException ex){error(ex,"No fue posible preparar la apertura.");}}
    private void cerrar(UUID sesionId){try{SesionCajaDto s=service.obtener(sesionId);ResumenTurnoCajaDto r=service.resumen(sesionId);Dialog d=dialog("CERRAR CAJA");BigDecimalField contado=new BigDecimalField("Efectivo contado");contado.setRequiredIndicatorVisible(true);BigDecimalField diferencia=new BigDecimalField("Diferencia");diferencia.setReadOnly(true);contado.addValueChangeListener(e->diferencia.setValue(e.getValue()==null?null:e.getValue().subtract(r.efectivoEsperado())));TextArea nota=new TextArea("Observación");nota.setMaxLength(500);d.add(new AppDetailSection("Resumen").field("Turno",s.codigoVisible()).field("Caja",s.cajaNombre()).field("Fondo inicial",dinero(r.fondoInicial())).field("Entradas en efectivo",dinero(r.entradasEfectivo())).field("Salidas en efectivo",dinero(r.salidasEfectivo())).field("Efectivo esperado",dinero(r.efectivoEsperado())),form(contado,diferencia,nota));d.getFooter().add(new AppActionButton(ActionType.CLOSE_CASH,ButtonSize.MAIN,"Cerrar caja",e->{try{service.cerrar(sesionId,new CierreCajaInput(contado.getValue(),nota.getValue()));d.close();cargarCajas();Notification.show("Caja cerrada correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible cerrar la caja.");}}),new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}catch(RuntimeException ex){error(ex,"No fue posible preparar el cierre.");}}
    private void detalle(UUID id){try{SesionCajaDto s=service.obtener(id);ResumenTurnoCajaDto r=service.resumen(id);Dialog d=dialog("TURNO DE CAJA");VerticalLayout resumen=new VerticalLayout(new AppDetailSection("Información").field("Turno",s.codigoVisible()).field("Caja",s.cajaNombre()).field("Sucursal",s.sucursalNombre()).field("Fecha operativa",DATE.format(s.fechaOperativa())).field("Cajero",s.cajeroNombre()).field("Moneda",s.monedaCodigo()).field("Estado",s.estado()==EstadoSesionCaja.OPEN?"Abierto":"Cerrado").field("Fondo inicial",dinero(s.fondoInicial())).field("Entradas en efectivo",dinero(r.entradasEfectivo())).field("Salidas en efectivo",dinero(r.salidasEfectivo())).field("Tarjeta",dinero(r.entradasTarjeta())).field("Transferencia bancaria",dinero(r.entradasTransferencia())).field("Otras entradas",dinero(r.otrasEntradas())).field("Total entradas",dinero(r.totalEntradas())).field("Efectivo esperado",dinero(s.estado()==EstadoSesionCaja.CLOSED?s.efectivoEsperado():r.efectivoEsperado())).field("Efectivo contado",dinero(s.efectivoContado())).field("Diferencia",dinero(s.diferencia())).field("Revisión",revision(s.estadoRevision())));resumen.setPadding(false);AppGrid<MovimientoTurnoCajaDto> grid=new AppGrid<>(MovimientoTurnoCajaDto.class);grid.addColumn(m->m.fechaHora()==null?"—":m.fechaHora().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))).setHeader("Hora");grid.addColumn(m->m.tipo()==TipoMovimientoFinanciero.INCOME?"Entrada":"Salida").setHeader("Tipo");grid.addColumn(m->m.medioPago().getEtiqueta()).setHeader("Medio");grid.addColumn(MovimientoTurnoCajaDto::concepto).setHeader("Concepto").setFlexGrow(1);grid.addColumn(m->(m.tipo()==TipoMovimientoFinanciero.INCOME?"+":"-")+dinero(m.monto())).setHeader("Monto");AppPagination[] holder=new AppPagination[1];holder[0]=new AppPagination(req->{var page=service.movimientos(id,req.page(),req.size());grid.setItems(page.getContent());holder[0].setTotal(page.getTotalElements());});VerticalLayout movimientosPanel=new VerticalLayout(grid,holder[0]);movimientosPanel.setPadding(false);movimientosPanel.setVisible(false);Tab tabResumen=new Tab("Resumen"),tabMovimientos=new Tab("Movimientos");Tabs tabs=new Tabs(tabResumen,tabMovimientos);tabs.addSelectedChangeListener(e->{boolean show=e.getSelectedTab()==tabResumen;resumen.setVisible(show);movimientosPanel.setVisible(!show);if(!show){var page=service.movimientos(id,0,10);grid.setItems(page.getContent());holder[0].setTotal(page.getTotalElements());}});d.add(tabs,resumen,movimientosPanel);d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();}catch(RuntimeException ex){error(ex,"No fue posible consultar el turno.");}}
    private void revisar(UUID id){Dialog d=dialog("REVISAR CIERRE");Select<EstadoRevisionCaja> resultado=new Select<>();resultado.setLabel("Resultado");resultado.setItems(EstadoRevisionCaja.APPROVED,EstadoRevisionCaja.REQUIRES_REVIEW);resultado.setItemLabelGenerator(OperacionesCajaView::revision);TextArea nota=new TextArea("Observación");nota.setMaxLength(500);d.add(form(resultado,nota));d.getFooter().add(new AppActionButton(ActionType.REVIEW,ButtonSize.MAIN,"Guardar revisión",e->{try{service.revisar(id,new RevisionCierreCajaInput(resultado.getValue(),nota.getValue()));d.close();cargarHistorial();Notification.show("Cierre revisado correctamente.");}catch(RuntimeException ex){cargarHistorial();error(ex,"No fue posible revisar el cierre.");}}),new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}

    private static HorizontalLayout acciones(){HorizontalLayout h=new HorizontalLayout();h.addClassName("cc-grid-actions");h.setPadding(false);h.setSpacing(false);return h;}
    private static Dialog dialog(String titulo){Dialog d=new Dialog();d.setHeaderTitle(titulo);d.setWidth("min(800px, 96vw)");return d;}
    private static Div form(Component... campos){Div d=new Div(campos);d.addClassName("cc-dialog-form");return d;}
    private static Span badge(String texto,String tema){Span s=new Span(texto);s.getElement().getThemeList().add("badge "+tema);return s;}
    static Span badgeRevision(EstadoRevisionCaja estado){String tema=estado==EstadoRevisionCaja.APPROVED?"success":estado==EstadoRevisionCaja.REQUIRES_REVIEW?"error":"contrast";Span s=badge(revision(estado),tema);s.addClassName("cc-review-badge");return s;}
    static boolean puedeRevisar(EstadoRevisionCaja estado,boolean tienePermiso){return tienePermiso&&(estado==EstadoRevisionCaja.PENDING||estado==EstadoRevisionCaja.REQUIRES_REVIEW);}
    private static String dinero(BigDecimal v){return v==null?"—":String.format(Locale.US,"%,.2f",v);}
    private static String revision(EstadoRevisionCaja e){return switch(e){case APPROVED->"Aprobado";case REQUIRES_REVIEW->"Requiere revisión";default->"Pendiente";};}
    private static boolean puede(String permiso){return TenantContext.principalActual().permisos().contains(permiso);}
    private static void error(RuntimeException ex,String fallback){String m=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():fallback;Notification.show(m==null||m.isBlank()?fallback:m);}
}
