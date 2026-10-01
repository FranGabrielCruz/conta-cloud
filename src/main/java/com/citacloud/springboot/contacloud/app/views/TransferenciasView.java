package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="transferencias",layout=MainLayout.class)
@PageTitle("Transferencias | ContaCloud")
@PermitAll
public class TransferenciasView extends VerticalLayout implements BeforeEnterObserver,BeforeLeaveObserver {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final FinancialTransferService service;private final EmpresaModuloService modulos;
    private final TextField buscar=new TextField("Buscar");private final DatePicker desde=new DatePicker("Desde");
    private final DatePicker hasta=new DatePicker("Hasta");private final Select<EstadoMovimientoFinanciero> estado=new Select<>();
    private final AppGrid<TransferenciaFinancieraDto> grid=new AppGrid<>(TransferenciaFinancieraDto.class);
    private final AppPagination pagination;private String ordenarPor="fecha";private boolean ascendente;
    private Dialog formularioAbierto;private EstadoFormulario cambios;

    public TransferenciasView(FinancialTransferService service,EmpresaModuloService modulos){
        this.service=service;this.modulos=modulos;addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        AppActionButton nuevo=puede("transferencias.crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,
            "Nueva transferencia",e->formulario()):null;
        add(new AppPageHeader("TRANSFERENCIAS","Administra los movimientos de dinero entre cajas y cuentas bancarias.",
            nuevo==null?new Component[0]:new Component[]{nuevo}));
        configurarFiltros();configurarGrid();pagination=new AppPagination(r->cargar());add(filtros(),grid,pagination);
    }

    @Override public void beforeEnter(BeforeEnterEvent event){
        if(!modulos.habilitado("CAJA_BANCOS")||!puede("transferencias.ver")){
            Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}
        cargar();
    }
    @Override public void beforeLeave(BeforeLeaveEvent event){
        if(formularioAbierto==null||cambios==null||!cambios.dirty)return;
        var action=event.postpone();confirmarDescartar(()->{cambios.dirty=false;formularioAbierto.close();action.proceed();});
    }
    private void configurarFiltros(){
        buscar.setPlaceholder("Referencia, descripción, origen o destino...");buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);buscar.setValueChangeTimeout(400);prepararFecha(desde);prepararFecha(hasta);
        estado.setLabel("Estado");estado.setItems(EstadoMovimientoFinanciero.values());estado.setEmptySelectionAllowed(true);
        estado.setEmptySelectionCaption("Todos");estado.setItemLabelGenerator(this::estado);
        buscar.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});desde.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        hasta.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});estado.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
    }
    private HorizontalLayout filtros(){HorizontalLayout h=new HorizontalLayout(buscar,desde,hasta,estado);h.addClassName("cc-filter-bar");
        h.setAlignItems(Alignment.END);h.setWidthFull();return h;}
    private void configurarGrid(){
        grid.addColumn(i->DATE.format(i.fecha())).setHeader("Fecha").setKey("fecha").setSortable(true).setAutoWidth(true);
        grid.addColumn(TransferenciaFinancieraDto::origenNombre).setHeader("Origen").setKey("origen").setSortable(true).setFlexGrow(1);
        grid.addColumn(TransferenciaFinancieraDto::destinoNombre).setHeader("Destino").setKey("destino").setSortable(true).setFlexGrow(1);
        grid.addColumn(this::monedas).setHeader("Moneda").setKey("moneda").setSortable(true).setAutoWidth(true);
        grid.addColumn(i->dinero(i.montoOrigen())).setHeader("Monto").setKey("monto").setSortable(true).setAutoWidth(true);
        grid.addComponentColumn(i->badge(i.estado())).setHeader("Estado").setKey("estado").setSortable(true).setAutoWidth(true);
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No hay transferencias registradas.");
        grid.addSortListener(e->{if(!e.isFromClient()||e.getSortOrder().isEmpty()||pagination==null)return;
            GridSortOrder<TransferenciaFinancieraDto> s=e.getSortOrder().getFirst();ordenarPor=s.getSorted().getKey();
            ascendente=s.getDirection()==SortDirection.ASCENDING;reiniciar();});
    }
    private Component acciones(TransferenciaFinancieraDto item){HorizontalLayout h=new HorizontalLayout();h.addClassName("cc-grid-actions");
        h.setPadding(false);h.setSpacing(false);h.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,
            "Ver transferencia",e->detalle(item.id())));return h;}
    private void cargar(){try{var r=pagination.currentRequest();var page=service.buscar(buscar.getValue(),desde.getValue(),
        hasta.getValue(),estado.getValue(),r.page(),r.size(),ordenarPor,ascendente);grid.setItems(page.getContent());
        pagination.setTotal(page.getTotalElements());boolean filtrado=!buscar.getValue().isBlank()||desde.getValue()!=null
            ||hasta.getValue()!=null||estado.getValue()!=null;grid.setEmptyStateText(filtrado
            ?"No se encontraron transferencias con los filtros seleccionados.":"No hay transferencias registradas.");
        }catch(RuntimeException ex){error(ex,"No fue posible cargar las transferencias.");}}
    private void reiniciar(){pagination.reset();cargar();}

    private void formulario(){try{
        List<CuentaDineroOpcionDto> opciones=service.catalogos().cuentas();Dialog d=dialog("NUEVA TRANSFERENCIA");
        DatePicker fecha=new DatePicker("Fecha");prepararFecha(fecha);fecha.setRequired(true);fecha.setValue(java.time.LocalDate.now());
        ComboBox<CuentaDineroOpcionDto> origen=new ComboBox<>("Origen");origen.setRequired(true);origen.setItems(opciones);origen.setItemLabelGenerator(this::etiqueta);
        ComboBox<CuentaDineroOpcionDto> destino=new ComboBox<>("Destino");destino.setRequired(true);destino.setItems(opciones);destino.setItemLabelGenerator(this::etiqueta);
        TextField monedaOrigen=new TextField("Moneda origen");monedaOrigen.setReadOnly(true);
        TextField monedaDestino=new TextField("Moneda destino");monedaDestino.setReadOnly(true);
        BigDecimalField monto=new BigDecimalField("Monto");monto.setRequiredIndicatorVisible(true);
        BigDecimalField tasa=new BigDecimalField("Tasa de cambio");tasa.setRequiredIndicatorVisible(true);tasa.setVisible(false);
        BigDecimalField montoDestino=new BigDecimalField("Monto destino");montoDestino.setReadOnly(true);montoDestino.setVisible(false);
        Span equivalencia=new Span();equivalencia.addClassName("cc-dialog-span-2");equivalencia.setVisible(false);
        TextField referencia=new TextField("Referencia");referencia.setMaxLength(100);referencia.addClassName("cc-dialog-span-2");
        TextArea descripcion=new TextArea("Descripción");descripcion.setMaxLength(1000);descripcion.addClassName("cc-dialog-span-2");
        EstadoFormulario change=new EstadoFormulario();
        Runnable actualizarConversion=()->actualizarConversion(fecha,origen,destino,monto,tasa,montoDestino,
            monedaOrigen,monedaDestino,equivalencia,opciones);
        origen.addValueChangeListener(e->{if(e.isFromClient())change.dirty=true;CuentaDineroOpcionDto anterior=destino.getValue();
            destino.setItems(opciones.stream().filter(x->e.getValue()==null||x.tipo()!=e.getValue().tipo()||!x.id().equals(e.getValue().id())).toList());
            if(anterior!=null&&(e.getValue()==null||anterior.tipo()!=e.getValue().tipo()||!anterior.id().equals(e.getValue().id())))destino.setValue(anterior);
            else if(anterior!=null)destino.clear();actualizarConversion.run();});
        destino.addValueChangeListener(e->{change.marcar(e.isFromClient());actualizarConversion.run();});
        fecha.addValueChangeListener(e->{change.marcar(e.isFromClient());actualizarConversion.run();});
        monto.addValueChangeListener(e->{change.marcar(e.isFromClient());calcularDestino(monto,tasa,montoDestino);});
        tasa.addValueChangeListener(e->{change.marcar(e.isFromClient());calcularDestino(monto,tasa,montoDestino);
            CuentaDineroOpcionDto o=origen.getValue(),x=destino.getValue();equivalencia.setText(o==null||x==null||e.getValue()==null?"":
                "1 "+x.monedaCodigo()+" = "+e.getValue().stripTrailingZeros().toPlainString()+" "+o.monedaCodigo());});
        referencia.addValueChangeListener(e->change.marcar(e.isFromClient()));descripcion.addValueChangeListener(e->change.marcar(e.isFromClient()));
        d.add(form(fecha,tasa,origen,destino,monto,montoDestino,monedaOrigen,monedaDestino,equivalencia,referencia,descripcion));
        AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",null);
        guardar.addClickListener(e->{CuentaDineroOpcionDto o=origen.getValue(),x=destino.getValue();guardar.setEnabled(false);try{
            service.crear(new TransferenciaFinancieraInput(fecha.getValue(),o==null?null:o.tipo(),o==null?null:o.id(),
                x==null?null:x.tipo(),x==null?null:x.id(),monto.getValue(),tasa.isVisible()?tasa.getValue():null,
                referencia.getValue(),descripcion.getValue()));change.dirty=false;d.close();cargar();
            Notification.show("Transferencia registrada correctamente.");}catch(RuntimeException ex){guardar.setEnabled(true);
                error(ex,"No fue posible registrar la transferencia.");}});
        d.getFooter().add(guardar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->cerrar(d,change)));
        d.setCloseOnEsc(false);d.setCloseOnOutsideClick(false);d.addDialogCloseActionListener(e->cerrar(d,change));
        formularioAbierto=d;cambios=change;d.addClosedListener(e->{if(formularioAbierto==d){formularioAbierto=null;cambios=null;}});d.open();
        }catch(RuntimeException ex){error(ex,"No fue posible abrir la transferencia.");}}

    private void actualizarConversion(DatePicker fecha,ComboBox<CuentaDineroOpcionDto> origen,
            ComboBox<CuentaDineroOpcionDto> destino,BigDecimalField monto,BigDecimalField tasa,
            BigDecimalField montoDestino,TextField monedaOrigen,TextField monedaDestino,Span equivalencia,
            List<CuentaDineroOpcionDto> opciones){
        CuentaDineroOpcionDto o=origen.getValue(),d=destino.getValue();monedaOrigen.setValue(o==null?"":o.monedaCodigo());
        monedaDestino.setValue(d==null?"":d.monedaCodigo());boolean conversion=o!=null&&d!=null&&!o.monedaId().equals(d.monedaId());
        tasa.setVisible(conversion);montoDestino.setVisible(conversion);equivalencia.setVisible(conversion);
        monto.setLabel(conversion?"Monto origen":"Monto");
        if(!conversion){tasa.clear();montoDestino.clear();equivalencia.setText("");return;}
        try{Optional<TasaTransferenciaDto> encontrada=service.resolverTasa(o.monedaId(),d.monedaId(),fecha.getValue());
            if(encontrada.isPresent()){tasa.setValue(encontrada.get().tasa());tasa.setHelperText("Tasa configurada para la fecha seleccionada.");
                equivalencia.setText(encontrada.get().equivalencia());}
            else{tasa.clear();tasa.setHelperText("No se encontró una tasa configurada para esta fecha.");equivalencia.setText("");}
        }catch(RuntimeException ex){tasa.clear();tasa.setHelperText("No se encontró una tasa configurada para esta fecha.");}
        calcularDestino(monto,tasa,montoDestino);
    }
    private static void calcularDestino(BigDecimalField monto,BigDecimalField tasa,BigDecimalField destino){
        if(monto.getValue()==null||tasa.getValue()==null||tasa.getValue().signum()<=0){destino.clear();return;}
        destino.setValue(monto.getValue().divide(tasa.getValue(),2,RoundingMode.HALF_UP));
    }

    private void detalle(UUID id){try{TransferenciaFinancieraDto t=service.obtener(id);Dialog d=dialog("TRANSFERENCIA");
        AppDetailSection s=new AppDetailSection("Información").field("Fecha",DATE.format(t.fecha())).field("Estado",estado(t.estado()))
            .field("Origen",t.origenNombre()).field("Destino",t.destinoNombre());
        if(t.monedaOrigenId().equals(t.monedaDestinoId()))s.field("Moneda",t.monedaOrigenCodigo()).field("Monto",dinero(t.montoOrigen()));
        else s.field("Monto origen",dinero(t.montoOrigen())+" "+t.monedaOrigenCodigo())
            .field("Monto destino",dinero(t.montoDestino())+" "+t.monedaDestinoCodigo())
            .field("Tasa aplicada",t.tasaCambio().stripTrailingZeros().toPlainString()).field("Equivalencia",t.descripcionTasa());
        s.field("Tipo",tipo(t)).field("Referencia",texto(t.referencia())).field("Descripción",texto(t.descripcion()));
        if(t.estado()==EstadoMovimientoFinanciero.VOIDED)s.field("Anulada el",t.anuladoEn()==null?"—":
            t.anuladoEn().toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
            .field("Anulada por",texto(t.anuladoPorNombre())).field("Motivo de anulación",texto(t.motivoAnulacion()));
        d.add(s);if(t.anulable()&&puede("transferencias.anular"))d.getFooter().add(new AppActionButton(ActionType.VOID,
            ButtonSize.MAIN,"Anular transferencia",e->anular(t.id(),d)));
        d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
        }catch(RuntimeException ex){error(ex,"No fue posible consultar la transferencia.");}}
    private void anular(UUID id,Dialog parent){Dialog d=dialog("ANULAR TRANSFERENCIA");TextArea motivo=new TextArea("Motivo de anulación");
        motivo.setRequired(true);motivo.setMaxLength(500);motivo.setWidthFull();d.add(new Paragraph("Esta transferencia será anulada y sus movimientos financieros dejarán de formar parte de los saldos vigentes. El registro se conservará para fines históricos. ¿Deseas continuar?"),motivo);
        AppActionButton confirmar=new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular transferencia",e->{try{
            service.anular(id,motivo.getValue());d.close();if(parent!=null)parent.close();cargar();Notification.show("Transferencia anulada correctamente.");
            }catch(RuntimeException ex){error(ex,"No fue posible anular la transferencia.");}});
        d.getFooter().add(confirmar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}

    private String monedas(TransferenciaFinancieraDto t){return t.monedaOrigenCodigo().equals(t.monedaDestinoCodigo())
        ?t.monedaOrigenCodigo():t.monedaOrigenCodigo()+" → "+t.monedaDestinoCodigo();}
    private String tipo(TransferenciaFinancieraDto t){return cuenta(t.tipoOrigen())+" → "+cuenta(t.tipoDestino());}
    private static String cuenta(TipoCuentaDinero t){return t==TipoCuentaDinero.CASH_REGISTER?"Caja":"Banco";}
    private String etiqueta(CuentaDineroOpcionDto c){return c.nombre()+" - "+c.monedaCodigo();}
    private String estado(EstadoMovimientoFinanciero e){return e==EstadoMovimientoFinanciero.VOIDED?"Anulada":"Registrada";}
    private static Span badge(EstadoMovimientoFinanciero e){Span s=new Span(e==EstadoMovimientoFinanciero.VOIDED?"Anulada":"Registrada");
        s.getElement().getThemeList().add("badge "+(e==EstadoMovimientoFinanciero.VOIDED?"error":"success"));return s;}
    private static String dinero(BigDecimal n){return String.format(Locale.US,"%,.2f",n);}
    private static String texto(String s){return s==null||s.isBlank()?"—":s;}
    private static void prepararFecha(DatePicker f){f.setLocale(new Locale("es","DO"));f.setPlaceholder("DD/MM/YYYY");f.setClearButtonVisible(true);}
    private static Dialog dialog(String title){Dialog d=new Dialog();d.setHeaderTitle(title);d.setWidth("min(800px, 96vw)");return d;}
    private static Div form(Component... fields){Div d=new Div(fields);d.addClassName("cc-dialog-form");return d;}
    private static void cerrar(Dialog d,EstadoFormulario c){if(!c.dirty){d.close();return;}confirmarDescartar(()->{c.dirty=false;d.close();});}
    private static void confirmarDescartar(Runnable action){ConfirmDialog c=new ConfirmDialog();c.setHeader("Cambios sin guardar");
        c.setText("Hay cambios sin guardar. ¿Deseas descartarlos?");c.setCancelable(true);c.setCancelText("Continuar editando");
        c.setConfirmText("Descartar cambios");c.setConfirmButtonTheme("error primary");c.addConfirmListener(e->action.run());c.open();}
    private static boolean puede(String permiso){return TenantContext.principalActual().permisos().contains(permiso);}
    private static void error(RuntimeException ex,String fallback){String m=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException
        ?ex.getMessage():fallback;Notification.show(m==null||m.isBlank()?fallback:m);}
    private static final class EstadoFormulario{private boolean dirty;void marcar(boolean client){if(client)dirty=true;}}
}
