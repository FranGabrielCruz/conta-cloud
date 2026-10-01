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
import com.vaadin.flow.component.grid.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="conciliacion-bancaria",layout=MainLayout.class)
@PageTitle("Conciliación bancaria | ContaCloud")
@PermitAll
public class ConciliacionBancariaView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final BankReconciliationService service;private final BankStatementMovementService movimientosBanco;
    private final EmpresaModuloService modulos;
    private final ComboBox<CuentaBancariaConciliacionDto> cuenta=new ComboBox<>("Cuenta bancaria");
    private final DatePicker desde=new DatePicker("Desde"),hasta=new DatePicker("Hasta");
    private final Select<EstadoConciliacionBancaria> estado=new Select<>();
    private final AppGrid<ConciliacionBancariaDto> grid=new AppGrid<>(ConciliacionBancariaDto.class);
    private final AppPagination pagination;private String ordenar="periodo";private boolean ascendente;

    public ConciliacionBancariaView(BankReconciliationService service,BankStatementMovementService movimientosBanco,
            EmpresaModuloService modulos){
        this.service=service;this.movimientosBanco=movimientosBanco;this.modulos=modulos;
        addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        AppActionButton nuevo=puede("conciliacion_bancaria.crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,
            "Nueva conciliación",e->nueva()):null;
        add(new AppPageHeader("CONCILIACIÓN BANCARIA","Compara y concilia los movimientos de ContaCloud con el estado de cuenta bancario.",
            nuevo==null?new Component[0]:new Component[]{nuevo}));configurarFiltros();configurarGrid();
        pagination=new AppPagination(e->cargar());add(filtros(),grid,pagination);
    }
    @Override public void beforeEnter(BeforeEnterEvent event){if(!modulos.habilitado("CAJA_BANCOS")||!puede("conciliacion_bancaria.ver")){
        Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}cargarCatalogos();cargar();}
    private void configurarFiltros(){preparar(desde);preparar(hasta);cuenta.setClearButtonVisible(true);cuenta.setItemLabelGenerator(CuentaBancariaConciliacionDto::etiqueta);
        estado.setLabel("Estado");estado.setItems(EstadoConciliacionBancaria.values());estado.setEmptySelectionAllowed(true);estado.setEmptySelectionCaption("Todos");
        estado.setItemLabelGenerator(this::estado);cuenta.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        desde.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});hasta.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        estado.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});}
    private HorizontalLayout filtros(){HorizontalLayout h=new HorizontalLayout(cuenta,desde,hasta,estado);h.addClassName("cc-filter-bar");
        h.setAlignItems(Alignment.END);h.setWidthFull();return h;}
    private void configurarGrid(){grid.addColumn(r->DATE.format(r.fechaInicial())+" - "+DATE.format(r.fechaFinal())).setHeader("Período").setKey("periodo").setSortable(true).setAutoWidth(true);
        grid.addColumn(ConciliacionBancariaDto::cuentaEtiqueta).setHeader("Cuenta bancaria").setKey("cuenta").setSortable(true).setFlexGrow(1);
        grid.addColumn(ConciliacionBancariaDto::monedaCodigo).setHeader("Moneda").setKey("moneda").setSortable(true).setAutoWidth(true);
        grid.addComponentColumn(r->badge(r.estado())).setHeader("Estado").setKey("estado").setSortable(true).setAutoWidth(true);
        grid.addComponentColumn(r->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver conciliación",e->workspace(r.id())))
            .setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);grid.setEmptyStateText("No hay conciliaciones bancarias registradas.");
        grid.addSortListener(e->{if(!e.isFromClient()||e.getSortOrder().isEmpty()||pagination==null)return;var s=e.getSortOrder().getFirst();
            ordenar=s.getSorted().getKey();ascendente=s.getDirection()==SortDirection.ASCENDING;reiniciar();});}
    private void cargarCatalogos(){try{cuenta.setItems(service.cuentasDisponibles());}catch(RuntimeException ex){error(ex,"No fue posible cargar las cuentas bancarias.");}}
    private void cargar(){try{var p=pagination.currentRequest();UUID cuentaId=cuenta.getValue()==null?null:cuenta.getValue().id();
        var page=service.buscar(cuentaId,desde.getValue(),hasta.getValue(),estado.getValue(),p.page(),p.size(),ordenar,ascendente);
        grid.setItems(page.getContent());pagination.setTotal(page.getTotalElements());}catch(RuntimeException ex){error(ex,"No fue posible cargar las conciliaciones bancarias.");}}
    private void reiniciar(){pagination.reset();cargar();}

    private void nueva(){try{List<CuentaBancariaConciliacionDto> opciones=service.cuentasDisponibles();Dialog d=dialog("NUEVA CONCILIACIÓN","800px");
        ComboBox<CuentaBancariaConciliacionDto> c=new ComboBox<>("Cuenta bancaria");c.setItems(opciones);c.setItemLabelGenerator(CuentaBancariaConciliacionDto::etiqueta);c.setRequired(true);
        DatePicker inicio=new DatePicker("Fecha inicial"),fin=new DatePicker("Fecha final");preparar(inicio);preparar(fin);inicio.setRequired(true);fin.setRequired(true);
        BigDecimalField saldoInicio=new BigDecimalField("Saldo inicial según banco"),saldoFin=new BigDecimalField("Saldo final según banco");
        saldoInicio.setRequiredIndicatorVisible(true);saldoFin.setRequiredIndicatorVisible(true);Div form=new Div(c,inicio,fin,saldoInicio,saldoFin);form.addClassName("cc-dialog-form");d.add(form);
        AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",null);guardar.addClickListener(e->{guardar.setEnabled(false);try{
            var creado=service.crear(new ConciliacionBancariaInput(c.getValue()==null?null:c.getValue().id(),inicio.getValue(),fin.getValue(),saldoInicio.getValue(),saldoFin.getValue()));
            d.close();cargar();Notification.show("Conciliación creada correctamente.");workspace(creado.id());}catch(RuntimeException ex){guardar.setEnabled(true);error(ex,"No fue posible crear la conciliación.");}});
        d.getFooter().add(guardar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();
        }catch(RuntimeException ex){error(ex,"No fue posible abrir la nueva conciliación.");}}

    private void workspace(UUID id){try{ConciliacionBancariaDto r=service.obtener(id);Dialog d=dialog("CONCILIACIÓN BANCARIA","min(1400px, 98vw)");
        d.addClassName("cc-reconciliation-workspace");VerticalLayout content=new VerticalLayout();content.setPadding(false);content.setSpacing(true);
        H3 cuentaTitulo=new H3(r.cuentaEtiqueta()+" - "+r.monedaCodigo());Paragraph periodo=new Paragraph(DATE.format(r.fechaInicial())+" - "+DATE.format(r.fechaFinal())+" · "+estado(r.estado()));
        Div resumen=new Div();resumen.addClassName("cc-reconciliation-summary");content.add(cuentaTitulo,periodo,resumen);
        Div pendientes=new Div(),conciliados=new Div();Tab tp=new Tab("PENDIENTES"),tc=new Tab("CONCILIADOS");Tabs tabs=new Tabs(tp,tc);content.add(tabs,pendientes,conciliados);conciliados.setVisible(false);
        tabs.addSelectedChangeListener(e->{boolean p=e.getSelectedTab()==tp;pendientes.setVisible(p);conciliados.setVisible(!p);});
        boolean enProceso=r.estado()==EstadoConciliacionBancaria.IN_PROGRESS;
        boolean conciliable=enProceso&&puede("conciliacion_bancaria.conciliar");
        boolean editableBanco=enProceso&&puede("conciliacion_bancaria.movimiento_banco_editar");
        boolean eliminableBanco=enProceso&&puede("conciliacion_bancaria.movimiento_banco_eliminar");
        construirPendientes(r,d,pendientes,resumen,conciliable,editableBanco,eliminableBanco);
        construirConciliados(r,d,conciliados,resumen,conciliable);
        if(r.estado()==EstadoConciliacionBancaria.FINALIZED&&r.finalizadoEn()!=null)content.add(new Paragraph("Finalizada por: "+texto(r.finalizadoPorNombre())+
            " · Fecha: "+r.finalizadoEn().toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
        if(r.estado()==EstadoConciliacionBancaria.VOIDED&&r.anuladoEn()!=null)content.add(new Paragraph("Anulada por: "+texto(r.anuladoPorNombre())+
            " · Fecha: "+r.anuladoEn().toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))+" · Motivo: "+texto(r.motivoAnulacion())));
        d.add(content);if(r.estado()==EstadoConciliacionBancaria.IN_PROGRESS&&puede("conciliacion_bancaria.finalizar"))
            d.getFooter().add(new AppActionButton(ActionType.FINALIZE,ButtonSize.MAIN,"Finalizar conciliación",e->confirmarFinalizar(r.id(),d)));
        if(r.estado()!=EstadoConciliacionBancaria.VOIDED&&puede("conciliacion_bancaria.anular"))
            d.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular conciliación",e->anular(r.id(),d)));
        d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));actualizarResumen(r.id(),resumen);d.open();
        }catch(RuntimeException ex){error(ex,"No fue posible cargar la conciliación bancaria.");}}

    private void construirPendientes(ConciliacionBancariaDto r,Dialog dialog,Div container,Div resumen,
            boolean conciliable,boolean editableBanco,boolean eliminableBanco){
        VerticalLayout root=new VerticalLayout();root.setPadding(false);HorizontalLayout grids=new HorizontalLayout();grids.setWidthFull();grids.addClassName("cc-reconciliation-grids");
        VerticalLayout left=new VerticalLayout(new H3("MOVIMIENTOS CONTACLOUD")),right=new VerticalLayout(new H3("MOVIMIENTOS BANCO"));left.setPadding(false);right.setPadding(false);left.setWidthFull();right.setWidthFull();
        AppGrid<MovimientoConciliableDto> internos=new AppGrid<>(MovimientoConciliableDto.class);internos.setSelectionMode(Grid.SelectionMode.SINGLE);
        internos.addColumn(x->DATE.format(x.fecha())).setHeader("Fecha").setAutoWidth(true);internos.addColumn(MovimientoConciliableDto::concepto).setHeader("Concepto").setFlexGrow(1);
        internos.addColumn(x->texto(x.referencia())).setHeader("Referencia").setAutoWidth(true);internos.addColumn(x->montoFirmado(x.direccion(),x.monto())).setHeader("Monto").setAutoWidth(true);
        internos.addColumn(x->x.pendienteAnterior()?"Pendiente anterior":"Período actual").setHeader("Origen").setAutoWidth(true);internos.setEmptyStateText("No hay movimientos de ContaCloud pendientes de conciliación.");
        AppGrid<MovimientoEstadoBancarioDto> banco=new AppGrid<>(MovimientoEstadoBancarioDto.class);banco.setSelectionMode(Grid.SelectionMode.SINGLE);
        Runnable[] loadBanco=new Runnable[1];
        banco.addColumn(x->DATE.format(x.fecha())).setHeader("Fecha").setAutoWidth(true);banco.addColumn(MovimientoEstadoBancarioDto::descripcion).setHeader("Descripción").setFlexGrow(1);
        banco.addColumn(x->texto(x.referencia())).setHeader("Referencia").setAutoWidth(true);banco.addColumn(x->montoFirmado(x.direccion(),x.monto())).setHeader("Monto").setAutoWidth(true);
        if(editableBanco||eliminableBanco)banco.addComponentColumn(x->{HorizontalLayout acciones=new HorizontalLayout();acciones.addClassName("cc-grid-actions");acciones.setPadding(false);acciones.setSpacing(false);
            if(editableBanco)acciones.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar movimiento bancario",e->editarMovimientoBanco(r,x,()->{loadBanco[0].run();actualizarResumen(r.id(),resumen);}))); 
            if(eliminableBanco)acciones.add(new AppActionButton(ActionType.DELETE,ButtonSize.GRID_ACTION,"Eliminar movimiento bancario",e->confirmarEliminarMovimientoBanco(r,x,()->{loadBanco[0].run();actualizarResumen(r.id(),resumen);}))); 
            return acciones;}).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        banco.setEmptyStateText("No hay movimientos bancarios registrados para esta conciliación.");
        AppPagination[] pi=new AppPagination[1],pb=new AppPagination[1];Runnable loadI=()->{var q=pi[0].currentRequest();var page=service.movimientosContaCloud(r.id(),q.page(),q.size());internos.setItems(page.getContent());pi[0].setTotal(page.getTotalElements());};
        loadBanco[0]=()->{var q=pb[0].currentRequest();var page=service.movimientosBanco(r.id(),q.page(),q.size());banco.setItems(page.getContent());pb[0].setTotal(page.getTotalElements());};
        Runnable loadB=()->loadBanco[0].run();
        cargarPendientes=()->{loadI.run();loadB.run();};
        pi[0]=new AppPagination(x->loadI.run());pb[0]=new AppPagination(x->loadB.run());left.add(internos,pi[0]);right.add(banco,pb[0]);grids.add(left,right);root.add(grids);
        if(conciliable){HorizontalLayout actions=new HorizontalLayout();actions.setWidthFull();actions.setJustifyContentMode(JustifyContentMode.CENTER);
            AppActionButton match=new AppActionButton(ActionType.MATCH,ButtonSize.MAIN,"Conciliar movimientos",e->{var i=internos.asSingleSelect().getValue();var b=banco.asSingleSelect().getValue();
                if(i==null||b==null){Notification.show("Selecciona un movimiento de ContaCloud y uno del banco.");return;}confirmarMatch(r,i,b,()->{loadI.run();loadB.run();actualizarResumen(r.id(),resumen);});});
            AppActionButton add=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Agregar movimiento bancario",e->agregarBanco(r,()->{loadB.run();actualizarResumen(r.id(),resumen);}));actions.add(match,add);root.add(actions);}
        container.add(root);loadI.run();loadB.run();}

    private void construirConciliados(ConciliacionBancariaDto r,Dialog dialog,Div container,Div resumen,boolean editable){
        AppGrid<AsociacionConciliacionDto> g=new AppGrid<>(AsociacionConciliacionDto.class);g.addColumn(x->DATE.format(x.fechaContaCloud())).setHeader("Fecha ContaCloud").setAutoWidth(true);
        g.addColumn(AsociacionConciliacionDto::conceptoContaCloud).setHeader("ContaCloud").setFlexGrow(1);g.addColumn(AsociacionConciliacionDto::descripcionBanco).setHeader("Banco").setFlexGrow(1);
        g.addColumn(x->montoFirmado(x.direccion(),x.monto())).setHeader("Monto").setAutoWidth(true);g.addColumn(x->"Conciliado").setHeader("Resultado").setAutoWidth(true);
        if(editable)g.addComponentColumn(x->new AppActionButton(ActionType.UNMATCH,ButtonSize.GRID_ACTION,"Desconciliar",e->{try{service.desconciliar(r.id(),x.id());
            Notification.show("Conciliación de movimientos deshecha correctamente.");cargarMatches.run();cargarPendientes.run();actualizarResumen(r.id(),resumen);}catch(RuntimeException ex){error(ex,"No fue posible desconciliar los movimientos.");}})).setHeader("Acciones").setAutoWidth(true);
        g.setEmptyStateText("No hay movimientos conciliados.");AppPagination[] p=new AppPagination[1];Runnable load=()->{var q=p[0].currentRequest();var page=service.conciliados(r.id(),q.page(),q.size());g.setItems(page.getContent());p[0].setTotal(page.getTotalElements());};
        cargarMatches=load;p[0]=new AppPagination(x->load.run());container.add(g,p[0]);load.run();}
    private Runnable cargarMatches=()->{};
    private Runnable cargarPendientes=()->{};

    private void agregarBanco(ConciliacionBancariaDto r,Runnable after){Dialog d=dialog("AGREGAR MOVIMIENTO BANCARIO","760px");DatePicker fecha=new DatePicker("Fecha");preparar(fecha);fecha.setMin(r.fechaInicial());fecha.setMax(r.fechaFinal());
        Select<DireccionMovimientoBancario> tipo=new Select<>();tipo.setLabel("Tipo");tipo.setItems(DireccionMovimientoBancario.values());
        tipo.setItemLabelGenerator(x->x==null?"Selecciona":x==DireccionMovimientoBancario.INFLOW?"Crédito":"Débito");
        TextField descripcion=new TextField("Descripción");descripcion.setMaxLength(180);BigDecimalField monto=new BigDecimalField("Monto");TextField referencia=new TextField("Referencia");referencia.setMaxLength(100);
        Div form=new Div(fecha,tipo,descripcion,monto,referencia);form.addClassName("cc-dialog-form");d.add(form);AppActionButton save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{try{
            service.agregarMovimientoBanco(r.id(),new MovimientoEstadoBancarioInput(fecha.getValue(),tipo.getValue(),descripcion.getValue(),referencia.getValue(),monto.getValue()));d.close();after.run();Notification.show("Movimiento bancario agregado correctamente.");
            }catch(RuntimeException ex){error(ex,"No fue posible agregar el movimiento bancario.");}});d.getFooter().add(save,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}
    private void editarMovimientoBanco(ConciliacionBancariaDto r,MovimientoEstadoBancarioDto actual,Runnable after){
        Dialog d=dialog("EDITAR MOVIMIENTO BANCARIO","760px");DatePicker fecha=new DatePicker("Fecha");preparar(fecha);fecha.setMin(r.fechaInicial());fecha.setMax(r.fechaFinal());fecha.setRequired(true);
        Select<DireccionMovimientoBancario> tipo=new Select<>();tipo.setLabel("Tipo");tipo.setItems(DireccionMovimientoBancario.values());tipo.setRequiredIndicatorVisible(true);
        tipo.setItemLabelGenerator(x->x==null?"Selecciona":x==DireccionMovimientoBancario.INFLOW?"Crédito":"Débito");
        TextField descripcion=new TextField("Descripción");descripcion.setRequired(true);descripcion.setMaxLength(180);
        BigDecimalField monto=new BigDecimalField("Monto");monto.setRequiredIndicatorVisible(true);TextField referencia=new TextField("Referencia");referencia.setMaxLength(100);
        fecha.setValue(actual.fecha());tipo.setValue(actual.direccion());descripcion.setValue(actual.descripcion());monto.setValue(actual.monto());referencia.setValue(actual.referencia()==null?"":actual.referencia());
        EstadoFormulario cambios=new EstadoFormulario();fecha.addValueChangeListener(e->cambios.marcar(e.isFromClient()));tipo.addValueChangeListener(e->cambios.marcar(e.isFromClient()));
        descripcion.addValueChangeListener(e->cambios.marcar(e.isFromClient()));monto.addValueChangeListener(e->cambios.marcar(e.isFromClient()));referencia.addValueChangeListener(e->cambios.marcar(e.isFromClient()));
        Div form=new Div(fecha,tipo,descripcion,monto,referencia);form.addClassName("cc-dialog-form");d.add(form);
        AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",null);guardar.addClickListener(e->{guardar.setEnabled(false);try{
            movimientosBanco.actualizar(r.id(),actual.id(),new MovimientoEstadoBancarioInput(fecha.getValue(),tipo.getValue(),descripcion.getValue(),referencia.getValue(),monto.getValue()));
            cambios.dirty=false;d.close();after.run();Notification.show("Movimiento bancario actualizado correctamente.");
            }catch(RuntimeException ex){guardar.setEnabled(true);error(ex,"No fue posible actualizar el movimiento bancario.");}});
        d.getFooter().add(guardar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->cerrarEdicion(d,cambios)));
        d.setCloseOnEsc(false);d.setCloseOnOutsideClick(false);d.addDialogCloseActionListener(e->cerrarEdicion(d,cambios));d.open();
    }
    private void confirmarEliminarMovimientoBanco(ConciliacionBancariaDto r,MovimientoEstadoBancarioDto movimiento,Runnable after){
        Dialog d=dialog("ELIMINAR MOVIMIENTO BANCARIO","650px");d.add(new Paragraph("¿Deseas eliminar este movimiento bancario?"),
            new AppDetailSection("Información").field("Fecha",DATE.format(movimiento.fecha())).field("Descripción",movimiento.descripcion())
                .field("Monto",montoFirmado(movimiento.direccion(),movimiento.monto())+" "+r.monedaCodigo()),
            new Paragraph("Esta acción eliminará el movimiento bancario utilizado en esta conciliación."));
        AppActionButton eliminar=new AppActionButton(ActionType.DELETE,ButtonSize.MAIN,"Eliminar",null);eliminar.addClickListener(e->{eliminar.setEnabled(false);try{
            movimientosBanco.eliminar(r.id(),movimiento.id());d.close();after.run();Notification.show("Movimiento bancario eliminado correctamente.");
            }catch(RuntimeException ex){eliminar.setEnabled(true);error(ex,"No fue posible eliminar el movimiento bancario.");}});
        d.getFooter().add(eliminar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();
    }
    private static void cerrarEdicion(Dialog dialog,EstadoFormulario cambios){if(!cambios.dirty){dialog.close();return;}
        ConfirmDialog confirmar=new ConfirmDialog();confirmar.setHeader("Cambios sin guardar");confirmar.setText("Hay cambios sin guardar. ¿Deseas descartarlos?");
        confirmar.setCancelable(true);confirmar.setCancelText("Continuar editando");confirmar.setConfirmText("Descartar cambios");confirmar.setConfirmButtonTheme("error primary");
        confirmar.addConfirmListener(e->{cambios.dirty=false;dialog.close();});confirmar.open();}
    private void confirmarMatch(ConciliacionBancariaDto r,MovimientoConciliableDto i,MovimientoEstadoBancarioDto b,Runnable after){ConfirmDialog c=new ConfirmDialog();c.setHeader("CONCILIAR MOVIMIENTOS");
        c.setText("ContaCloud: "+i.concepto()+" · "+dinero(i.monto())+" "+r.monedaCodigo()+"\nBanco: "+b.descripcion()+" · "+dinero(b.monto())+" "+r.monedaCodigo());c.setCancelable(true);c.setCancelText("Cancelar");c.setConfirmText("Confirmar");
        c.addConfirmListener(e->{try{service.conciliar(r.id(),i.id(),b.id());after.run();cargarMatches.run();Notification.show("Movimientos conciliados correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible conciliar los movimientos.");}});c.open();}
    private void actualizarResumen(UUID id,Div target){try{ResumenConciliacionBancariaDto x=service.resumen(id);target.removeAll();target.add(card("Saldo inicial banco",x.saldoInicialBanco()),card("Movimientos banco",x.netoBanco()),
            card("Saldo final banco",x.saldoFinalBanco()),card("Saldo conciliado",x.saldoConciliado()),card("Diferencia",x.diferencia()));
        Paragraph estado=new Paragraph(x.cuadrada()&&x.pendientesBanco()==0?"✓ Conciliación cuadrada":"⚠ La conciliación presenta diferencias.");
        estado.addClassName(x.cuadrada()&&x.pendientesBanco()==0?"cc-success-text":"cc-warning-text");target.add(estado,new Paragraph("Pendientes ContaCloud: "+x.pendientesContaCloud()+" · Pendientes banco: "+x.pendientesBanco()+" · Conciliados: "+x.conciliados()));
        if(!x.estadoCuentaConsistente())target.add(new Paragraph("Los movimientos bancarios registrados no coinciden con el saldo final indicado por el banco."));
        }catch(RuntimeException ex){error(ex,"No fue posible calcular el resumen de conciliación.");}}
    private static Div card(String label,BigDecimal value){Div d=new Div(new Span(label),new H3(dinero(value)));d.addClassName("cc-card");return d;}
    private void confirmarFinalizar(UUID id,Dialog parent){ConfirmDialog c=new ConfirmDialog();c.setHeader("Finalizar conciliación");c.setText("Al finalizar la conciliación, las asociaciones quedarán cerradas y no podrán modificarse directamente. ¿Deseas continuar?");
        c.setCancelable(true);c.setCancelText("Cancelar");c.setConfirmText("Finalizar");c.addConfirmListener(e->{try{service.finalizar(id);c.close();parent.close();cargar();Notification.show("Conciliación bancaria finalizada correctamente.");workspace(id);}catch(RuntimeException ex){error(ex,"No fue posible finalizar la conciliación.");}});c.open();}
    private void anular(UUID id,Dialog parent){Dialog d=dialog("ANULAR CONCILIACIÓN","650px");TextArea motivo=new TextArea("Motivo de anulación");motivo.setRequired(true);motivo.setMaxLength(500);motivo.setWidthFull();d.add(motivo);
        d.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular conciliación",e->{try{service.anular(id,motivo.getValue());d.close();parent.close();cargar();Notification.show("Conciliación bancaria anulada correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible anular la conciliación.");}}),
            new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}
    private String estado(EstadoConciliacionBancaria e){return e==null?"Todos":switch(e){case IN_PROGRESS->"En proceso";case FINALIZED->"Finalizada";case VOIDED->"Anulada";};}
    private static Span badge(EstadoConciliacionBancaria e){Span s=new Span(switch(e){case IN_PROGRESS->"En proceso";case FINALIZED->"Finalizada";case VOIDED->"Anulada";});s.getElement().getThemeList().add("badge "+(e==EstadoConciliacionBancaria.VOIDED?"error":e==EstadoConciliacionBancaria.FINALIZED?"success":"contrast"));return s;}
    private static String montoFirmado(DireccionMovimientoBancario d,BigDecimal n){return (d==DireccionMovimientoBancario.INFLOW?"+":"-")+dinero(n);}
    private static String dinero(BigDecimal n){return String.format(Locale.US,"%,.2f",n==null?BigDecimal.ZERO:n);}
    private static String texto(String s){return s==null||s.isBlank()?"—":s;}
    private static void preparar(DatePicker f){f.setLocale(new Locale("es","DO"));f.setPlaceholder("DD/MM/YYYY");f.setClearButtonVisible(true);}
    private static Dialog dialog(String title,String width){Dialog d=new Dialog();d.setHeaderTitle(title);d.setWidth(width);d.setMaxWidth("98vw");d.setMaxHeight("96vh");return d;}
    private static boolean puede(String p){return TenantContext.principalActual().permisos().contains(p);}
    private static void error(RuntimeException ex,String fallback){String m=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():fallback;Notification.show(m==null||m.isBlank()?fallback:m);}
    private static final class EstadoFormulario{private boolean dirty;void marcar(boolean desdeCliente){if(desdeCliente)dirty=true;}}
}
