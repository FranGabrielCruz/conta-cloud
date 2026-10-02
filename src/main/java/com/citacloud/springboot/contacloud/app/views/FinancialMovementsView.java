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
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

abstract class FinancialMovementsView extends VerticalLayout implements BeforeEnterObserver,BeforeLeaveObserver {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final FinancialMovementService service;
    private final EmpresaModuloService modulos;
    private final TipoMovimientoFinanciero tipo;
    private final String recurso;
    private final TextField buscar=new TextField("Buscar");
    private final DatePicker desde=new DatePicker("Desde");
    private final DatePicker hasta=new DatePicker("Hasta");
    private final ComboBox<CuentaDineroOpcionDto> cuenta=new ComboBox<>("Cuenta");
    private final Select<EstadoMovimientoFinanciero> estado=new Select<>();
    private final AppGrid<MovimientoFinancieroDto> grid=new AppGrid<>(MovimientoFinancieroDto.class);
    private final AppPagination pagination;
    private String ordenarPor="fecha"; private boolean ascendente=false;
    private Dialog formularioAbierto; private EstadoFormulario cambios;

    FinancialMovementsView(FinancialMovementService service,EmpresaModuloService modulos,TipoMovimientoFinanciero tipo){
        this.service=service;this.modulos=modulos;this.tipo=tipo;this.recurso=tipo==TipoMovimientoFinanciero.INCOME?"ingresos":"egresos";
        addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        String plural=tipo==TipoMovimientoFinanciero.INCOME?"INGRESOS":"EGRESOS";
        String singular=tipo==TipoMovimientoFinanciero.INCOME?"ingreso":"egreso";
        AppActionButton nuevo=puede(recurso+".crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,
            "Nuevo "+singular,e->formulario(null)):null;
        add(new AppPageHeader(plural,"Administra los movimientos de "+singular+" de caja y bancos.",
            nuevo==null?new Component[0]:new Component[]{nuevo}));
        configurarFiltros();configurarGrid();pagination=new AppPagination(r->cargar());
        add(filtros(),grid,pagination);
    }

    @Override public void beforeEnter(BeforeEnterEvent event){
        if(!modulos.habilitado("CAJA_BANCOS")||!puede(recurso+".ver")){
            Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;
        }
        recargarCuentas();cargar();
    }
    @Override public void beforeLeave(BeforeLeaveEvent event){
        if(formularioAbierto==null||cambios==null||!cambios.dirty)return;
        var action=event.postpone();confirmarDescartar(()->{cambios.dirty=false;formularioAbierto.close();action.proceed();});
    }
    private void configurarFiltros(){
        buscar.setPlaceholder("Concepto, referencia o descripción...");buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);buscar.setValueChangeTimeout(400);
        prepararFecha(desde);prepararFecha(hasta);
        cuenta.setPlaceholder("Todas");cuenta.setClearButtonVisible(true);cuenta.setItemLabelGenerator(this::etiquetaCuenta);
        estado.setLabel("Estado");estado.setItems(EstadoMovimientoFinanciero.values());estado.setEmptySelectionAllowed(true);
        estado.setEmptySelectionCaption("Todos");estado.setItemLabelGenerator(this::estado);
        buscar.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        desde.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});hasta.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        cuenta.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});estado.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
    }
    private HorizontalLayout filtros(){HorizontalLayout h=new HorizontalLayout(buscar,desde,hasta,cuenta,estado);
        h.addClassName("cc-filter-bar");h.setAlignItems(Alignment.END);h.setWidthFull();return h;}
    private void configurarGrid(){
        grid.addColumn(i->DATE.format(i.fecha())).setHeader("Fecha").setKey("fecha").setSortable(true).setAutoWidth(true);
        grid.addColumn(MovimientoFinancieroDto::concepto).setHeader("Concepto").setKey("concepto").setSortable(true).setFlexGrow(1);
        grid.addColumn(MovimientoFinancieroDto::cuentaNombre).setHeader("Cuenta").setKey("cuenta").setSortable(true).setFlexGrow(1);
        grid.addColumn(MovimientoFinancieroDto::monedaCodigo).setHeader("Moneda").setKey("moneda").setSortable(true).setAutoWidth(true);
        grid.addColumn(i->String.format(Locale.US,"%,.2f",i.monto())).setHeader("Monto").setKey("monto").setSortable(true).setAutoWidth(true);
        grid.addComponentColumn(i->badge(i.estado())).setHeader("Estado").setKey("estado").setSortable(true).setAutoWidth(true);
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No hay movimientos registrados.");
        grid.addSortListener(e->{if(!e.isFromClient()||e.getSortOrder().isEmpty()||pagination==null)return;
            GridSortOrder<MovimientoFinancieroDto> s=e.getSortOrder().getFirst();ordenarPor=s.getSorted().getKey();
            ascendente=s.getDirection()==SortDirection.ASCENDING;reiniciar();});
    }
    private Component acciones(MovimientoFinancieroDto item){HorizontalLayout h=new HorizontalLayout();h.addClassName("cc-grid-actions");h.setPadding(false);h.setSpacing(false);
        h.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver movimiento",e->detalle(item.id())));
        if(item.editable()&&puede(recurso+".editar"))h.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar movimiento",e->formulario(item.id())));
        if(item.anulable()&&puede(recurso+".anular"))h.add(new AppActionButton(ActionType.VOID,ButtonSize.GRID_ACTION,"Anular movimiento",e->anular(item.id(),null)));
        return h;}
    private void recargarCuentas(){try{cuenta.setItems(catalogos().cuentas());}catch(RuntimeException ex){error(ex,"No fue posible cargar las cuentas.");}}
    private void cargar(){try{var r=pagination.currentRequest();CuentaDineroOpcionDto c=cuenta.getValue();
        var page=tipo==TipoMovimientoFinanciero.INCOME
            ?service.buscarIngresos(buscar.getValue(),desde.getValue(),hasta.getValue(),c==null?null:c.tipo(),c==null?null:c.id(),estado.getValue(),r.page(),r.size(),ordenarPor,ascendente)
            :service.buscarEgresos(buscar.getValue(),desde.getValue(),hasta.getValue(),c==null?null:c.tipo(),c==null?null:c.id(),estado.getValue(),r.page(),r.size(),ordenarPor,ascendente);
        grid.setItems(page.getContent());pagination.setTotal(page.getTotalElements());
        boolean filtrado=!buscar.getValue().isBlank()||desde.getValue()!=null||hasta.getValue()!=null||c!=null||estado.getValue()!=null;
        grid.setEmptyStateText(filtrado?"No se encontraron movimientos con los filtros seleccionados.":"No hay movimientos registrados.");
        }catch(RuntimeException ex){error(ex,"No fue posible cargar los movimientos.");}}
    private void reiniciar(){pagination.reset();cargar();}

    private void formulario(UUID id){try{
        MovimientoFinancieroDto actual=id==null?null:obtener(id);MovimientoCatalogosDto cats=catalogos();
        Dialog d=dialog(actual==null?"NUEVO "+singularMayus():"EDITAR "+singularMayus());
        DatePicker fecha=new DatePicker("Fecha");prepararFecha(fecha);fecha.setRequired(true);
        ComboBox<CuentaDineroOpcionDto> account=new ComboBox<>("Cuenta");account.setRequired(true);account.setItems(cats.cuentas());account.setItemLabelGenerator(this::etiquetaCuenta);
        TextField moneda=new TextField("Moneda");moneda.setReadOnly(true);
        TextField concepto=new TextField("Concepto");concepto.setRequired(true);concepto.setMaxLength(180);
        Select<MedioPagoMovimiento> medioPago=new Select<>();medioPago.setLabel("Medio de pago");
        medioPago.setItems(MedioPagoMovimiento.values());medioPago.setItemLabelGenerator(MedioPagoMovimiento::getEtiqueta);
        medioPago.setRequiredIndicatorVisible(true);
        BigDecimalField monto=new BigDecimalField("Monto");monto.setRequiredIndicatorVisible(true);
        if(actual!=null){monto.setReadOnly(true);monto.setHelperText("El monto no puede modificarse.");}
        TextField referencia=new TextField("Referencia");referencia.setMaxLength(100);
        TextArea descripcion=new TextArea("Descripción");descripcion.setMaxLength(1000);descripcion.addClassName("cc-dialog-span-2");
        account.addValueChangeListener(e->{moneda.setValue(e.getValue()==null?"":e.getValue().monedaCodigo());
            boolean caja=e.getValue()!=null&&e.getValue().tipo()==TipoCuentaDinero.CASH_REGISTER;
            medioPago.setVisible(caja);if(!caja)medioPago.setValue(MedioPagoMovimiento.BANK_TRANSFER);
            else if(medioPago.getValue()==MedioPagoMovimiento.BANK_TRANSFER)medioPago.setValue(MedioPagoMovimiento.CASH);});
        if(actual==null)fecha.setValue(java.time.LocalDate.now());else{
            fecha.setValue(actual.fecha());cats.cuentas().stream().filter(x->x.id().equals(actual.cuentaId())&&x.tipo()==actual.tipoCuenta()).findFirst().ifPresent(account::setValue);
            concepto.setValue(actual.concepto());monto.setValue(actual.monto());referencia.setValue(nvl(actual.referencia()));descripcion.setValue(nvl(actual.descripcion()));moneda.setValue(actual.monedaCodigo());
            medioPago.setValue(actual.medioPago());medioPago.setVisible(actual.tipoCuenta()==TipoCuentaDinero.CASH_REGISTER);}
        EstadoFormulario change=new EstadoFormulario();
        fecha.addValueChangeListener(e->change.marcar(e.isFromClient()));account.addValueChangeListener(e->change.marcar(e.isFromClient()));
        concepto.addValueChangeListener(e->change.marcar(e.isFromClient()));monto.addValueChangeListener(e->change.marcar(e.isFromClient()));
        referencia.addValueChangeListener(e->change.marcar(e.isFromClient()));descripcion.addValueChangeListener(e->change.marcar(e.isFromClient()));
        medioPago.addValueChangeListener(e->change.marcar(e.isFromClient()));
        d.add(form(fecha,account,moneda,concepto,monto,medioPago,referencia,descripcion));
        AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",null);
        guardar.addClickListener(e->{CuentaDineroOpcionDto a=account.getValue();MovimientoFinancieroInput input=new MovimientoFinancieroInput(fecha.getValue(),a==null?null:a.tipo(),a==null?null:a.id(),concepto.getValue(),monto.getValue(),referencia.getValue(),descripcion.getValue(),medioPago.getValue());
            guardar(d,guardar,change,actual,input);});d.getFooter().add(guardar);
        if(actual!=null&&actual.anulable()&&puede(recurso+".anular"))d.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular",e->anular(actual.id(),d)));
        d.getFooter().add(new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->cerrar(d,change)));
        d.setCloseOnEsc(false);d.setCloseOnOutsideClick(false);d.addDialogCloseActionListener(e->cerrar(d,change));
        formularioAbierto=d;cambios=change;d.addClosedListener(e->{if(formularioAbierto==d){formularioAbierto=null;cambios=null;}});d.open();
        }catch(RuntimeException ex){error(ex,"No fue posible abrir el movimiento.");}}
    private void guardar(Dialog d,AppActionButton b,EstadoFormulario change,MovimientoFinancieroDto actual,MovimientoFinancieroInput input){b.setEnabled(false);try{
        if(tipo==TipoMovimientoFinanciero.INCOME){if(actual==null)service.crearIngreso(input);else service.actualizarIngreso(actual.id(),input);}
        else{if(actual==null)service.crearEgreso(input);else service.actualizarEgreso(actual.id(),input);}
        change.dirty=false;d.close();recargarCuentas();cargar();Notification.show(actual==null?"Movimiento registrado correctamente.":"Movimiento actualizado correctamente.");
        }catch(RuntimeException ex){b.setEnabled(true);error(ex,"No fue posible guardar el movimiento.");}}
    private void detalle(UUID id){try{MovimientoFinancieroDto i=obtener(id);Dialog d=dialog(singularMayus());AppDetailSection section=new AppDetailSection("Información")
        .field("Fecha",DATE.format(i.fecha())).field("Concepto",i.concepto()).field("Cuenta",i.cuentaNombre())
        .field("Moneda",i.monedaCodigo()).field("Monto",String.format(Locale.US,"%,.2f",i.monto()))
        .field("Medio de pago",i.medioPago()==null?"—":i.medioPago().getEtiqueta())
        .field("Estado",estado(i.estado())).field("Referencia",texto(i.referencia())).field("Descripción",texto(i.descripcion()));
        if(i.estado()==EstadoMovimientoFinanciero.VOIDED)section.field("Anulado el",i.anuladoEn()==null?"—":i.anuladoEn().toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
            .field("Anulado por",texto(i.anuladoPorNombre())).field("Motivo de anulación",texto(i.motivoAnulacion()));
        d.add(section);if(i.editable()&&puede(recurso+".editar"))d.getFooter().add(new AppActionButton(ActionType.EDIT,ButtonSize.MAIN,"Editar",e->{d.close();formulario(i.id());}));
        if(i.anulable()&&puede(recurso+".anular"))d.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular",e->anular(i.id(),d)));
        d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
        }catch(RuntimeException ex){error(ex,"No fue posible consultar el movimiento.");}}
    private void anular(UUID id,Dialog parent){Dialog d=dialog("ANULAR "+singularMayus());TextArea motivo=new TextArea("Motivo de anulación");motivo.setRequired(true);motivo.setMaxLength(500);motivo.setWidthFull();
        d.add(new Paragraph("El movimiento permanecerá en el historial y no podrá editarse después de anularlo."),motivo);
        AppActionButton confirmar=new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Confirmar anulación",e->{confirmarAnulacion(id,motivo.getValue(),parent,d);});
        d.getFooter().add(confirmar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}
    private void confirmarAnulacion(UUID id,String motivo,Dialog parent,Dialog confirm){try{if(tipo==TipoMovimientoFinanciero.INCOME)service.anularIngreso(id,motivo);else service.anularEgreso(id,motivo);
        confirm.close();if(parent!=null)parent.close();cargar();Notification.show("Movimiento anulado correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible anular el movimiento.");}}

    private MovimientoCatalogosDto catalogos(){return tipo==TipoMovimientoFinanciero.INCOME?service.catalogosIngresos():service.catalogosEgresos();}
    private MovimientoFinancieroDto obtener(UUID id){return tipo==TipoMovimientoFinanciero.INCOME?service.obtenerIngreso(id):service.obtenerEgreso(id);}
    private String singularMayus(){return tipo==TipoMovimientoFinanciero.INCOME?"INGRESO":"EGRESO";}
    private String etiquetaCuenta(CuentaDineroOpcionDto c){return c.nombre()+" - "+c.monedaCodigo();}
    private String estado(EstadoMovimientoFinanciero e){return e==EstadoMovimientoFinanciero.VOIDED?"Anulado":"Registrado";}
    private static Span badge(EstadoMovimientoFinanciero e){Span s=new Span(e==EstadoMovimientoFinanciero.VOIDED?"Anulado":"Registrado");s.getElement().getThemeList().add("badge "+(e==EstadoMovimientoFinanciero.VOIDED?"error":"success"));return s;}
    private static void prepararFecha(DatePicker f){f.setLocale(new Locale("es","DO"));f.setPlaceholder("DD/MM/YYYY");f.setClearButtonVisible(true);}
    private static Dialog dialog(String title){Dialog d=new Dialog();d.setHeaderTitle(title);d.setWidth("min(800px, 96vw)");return d;}
    private static Div form(Component... fields){Div d=new Div(fields);d.addClassName("cc-dialog-form");return d;}
    private static void cerrar(Dialog d,EstadoFormulario c){if(!c.dirty){d.close();return;}confirmarDescartar(()->{c.dirty=false;d.close();});}
    private static void confirmarDescartar(Runnable action){ConfirmDialog c=new ConfirmDialog();c.setHeader("Cambios sin guardar");c.setText("Hay cambios sin guardar. ¿Deseas descartarlos?");c.setCancelable(true);c.setCancelText("Continuar editando");c.setConfirmText("Descartar cambios");c.setConfirmButtonTheme("error primary");c.addConfirmListener(e->action.run());c.open();}
    private static boolean puede(String permiso){return TenantContext.principalActual().permisos().contains(permiso);}
    private static String texto(String s){return s==null||s.isBlank()?"—":s;}private static String nvl(String s){return s==null?"":s;}
    private static void error(RuntimeException ex,String fallback){String m=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():fallback;Notification.show(m==null||m.isBlank()?fallback:m);}
    private static final class EstadoFormulario{private boolean dirty;void marcar(boolean client){if(client)dirty=true;}}
}
