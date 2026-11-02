package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.services.MovimientoCajaService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.math.*;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.*;

/** Shared read-only content used by the POS dialog and the Caja tab. */
public class MovimientosCajaPanel extends VerticalLayout {
    @FunctionalInterface public interface Consulta {
        ConsultaMovimientosCajaDto cargar(String buscar,TipoMovimientoFinanciero tipo,TipoOrigenMovimiento origen,int pagina,int tamano);
    }
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final Consulta consulta;
    private final java.util.function.Consumer<ConsultaMovimientosCajaDto.Resumen> onState;
    private final java.util.function.Consumer<MovimientoCajaDto> mostrarDetalle;
    private final Div resumen=new Div();
    private final Div encabezado=new Div();
    private final Span mensaje=new Span();
    private final com.vaadin.flow.component.progressbar.ProgressBar cargando=new com.vaadin.flow.component.progressbar.ProgressBar();
    private final AppActionButton actualizar=new AppActionButton(ActionType.REFRESH,ButtonSize.MAIN,"Actualizar movimientos",e->cargar());
    private boolean consultando;
    private final TextField buscar=new TextField("Buscar");
    private final Select<TipoMovimientoFinanciero> tipo=new Select<>();
    private final Select<TipoOrigenMovimiento> origen=new Select<>();
    private final AppGrid<MovimientoCajaDto> grid=new AppGrid<>(MovimientoCajaDto.class);
    private final AppPagination paginacion;


    public MovimientosCajaPanel(Consulta consulta){this(consulta,null,r->{},true);}
    public MovimientosCajaPanel(Consulta consulta,java.util.function.Consumer<MovimientoCajaDto> detalle,
            java.util.function.Consumer<ConsultaMovimientosCajaDto.Resumen> onState,boolean titulo){
        this.consulta=Objects.requireNonNull(consulta);this.mostrarDetalle=detalle;this.onState=Objects.requireNonNull(onState);
        setPadding(false);setSpacing(false);setWidthFull();addClassName("cc-cash-movements-panel");setId("cash-movements-content");
        configurarFiltros();configurarGrid();paginacion=new AppPagination(req->cargar(),10,5,10,20,50);
        encabezado.addClassName("cc-cash-movement-heading");
        Div filtros=new Div(buscar,tipo,origen);filtros.addClassNames("cc-filter-bar","cc-cash-movement-filters");
        cargando.setIndeterminate(true);cargando.setVisible(false);resumen.addClassName("cc-cash-session-summary");mensaje.setVisible(false);
        actualizar.addClassName("cc-cash-refresh");
        if(titulo)add(new H2("MOVIMIENTOS DE CAJA"));
        add(actualizar,cargando,encabezado,mensaje,resumen,new H4("DETALLE DE MOVIMIENTOS"),filtros,grid,paginacion);cargar();
    }
    private Span estado(EstadoSesionCaja valor){Span estado=new Span(valor==EstadoSesionCaja.OPEN?"● ABIERTA":"● CERRADA");
        estado.addClassName(valor==EstadoSesionCaja.OPEN?"cc-cash-movement-open":"cc-cash-movement-closed");return estado;}
    private void configurarFiltros(){
        buscar.setPlaceholder("Concepto o referencia...");buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);buscar.setValueChangeTimeout(350);
        tipo.setLabel("Tipo");tipo.setItems(TipoMovimientoFinanciero.values());tipo.setEmptySelectionAllowed(true);
        tipo.setEmptySelectionCaption("Todos");tipo.setItemLabelGenerator(MovimientosCajaDialog::tipo);
        origen.setLabel("Origen");origen.setItems(TipoOrigenMovimiento.values());origen.setEmptySelectionAllowed(true);
        origen.setEmptySelectionCaption("Todos");origen.setItemLabelGenerator(MovimientosCajaDialog::origen);
        buscar.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        tipo.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        origen.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
    }
    private void configurarGrid(){
        grid.addColumn(x->fechaHora(x.fechaHora())).setHeader("Fecha/Hora").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(x->MovimientosCajaDialog.origen(x.origen())+(x.estado()==EstadoMovimientoFinanciero.VOIDED?" · Anulado":"")).setHeader("Tipo").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(MovimientosCajaPanel::documento).setHeader("Documento").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(x->{Span text=new Span(x.concepto());text.getElement().setAttribute("title",x.concepto());text.addClassName("cc-cash-movement-concept");return text;}).setHeader("Concepto").setFlexGrow(1);
        grid.addColumn(x->x.tipo()==TipoMovimientoFinanciero.INCOME?dinero(x):"—").setHeader("Entrada").setAutoWidth(true).setFlexGrow(0)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addColumn(x->x.tipo()==TipoMovimientoFinanciero.EXPENSE?dinero(x):"—").setHeader("Salida").setAutoWidth(true).setFlexGrow(0)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        if(mostrarDetalle!=null)grid.addComponentColumn(x->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver movimiento",e->mostrarDetalle.accept(x)))
            .setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No se encontraron movimientos de caja.");
    }
    private void cargar(){
        if(consultando)return;consultando=true;cargando.setVisible(true);actualizar.setEnabled(false);grid.setEnabled(false);
        var p=paginacion.currentRequest();
        limpiarConsulta();
        try{
            var consulta=this.consulta.cargar(buscar.getValue(),tipo.getValue(),origen.getValue(),p.page(),p.size());
            if(consulta.resumen()==null){paginacion.setTotal(0);mensaje.setText("No hay una sesión de caja abierta.");mensaje.setVisible(true);return;}
            mostrarResumen(consulta.resumen());
            grid.setItems(consulta.movimientos().getContent());paginacion.setTotal(consulta.movimientos().getTotalElements());
        }catch(RuntimeException ex){
            limpiarConsulta();paginacion.setTotal(0);mensaje.setText("No fue posible actualizar los movimientos. Intente actualizar nuevamente.");mensaje.setVisible(true);
            UiErrorHandler.show(ex,"No fue posible consultar los movimientos de caja.");
        }finally{consultando=false;cargando.setVisible(false);actualizar.setEnabled(true);grid.setEnabled(true);}
    }
    private void limpiarConsulta(){
        resumen.removeAll();encabezado.removeAll();mensaje.setVisible(false);onState.accept(null);
        grid.setItems(List.of());
    }
    private void mostrarResumen(ConsultaMovimientosCajaDto.Resumen r){
        encabezado.add(new H3(r.caja()+" · "+r.sucursal()),estado(r.estado()));
        onState.accept(r);
        AppDetailSection sesion=new AppDetailSection("SESIÓN ACTUAL").field("Apertura",fechaHora(r.apertura()))
            .field("Cajero",r.cajero()).field("Moneda",r.moneda());
        Div esperado=tarjeta("EFECTIVO ESPERADO",r.moneda(),r.efectivoEsperado());esperado.addClassName("cc-cash-expected");
        Div totales=new Div(tarjeta("FONDO INICIAL",r.moneda(),r.fondoInicial()),
            tarjeta("ENTRADAS DE CAJA",r.moneda(),r.entradas()),tarjeta("SALIDAS DE CAJA",r.moneda(),r.salidas()),
            esperado);
        totales.addClassName("cc-cash-summary-cards");
        Div medios=new Div();medios.addClassName("cc-cash-summary-cards");
        for(var forma:r.formasPago())if(forma.importe().signum()!=0)medios.add(tarjeta(forma.nombre(),r.moneda(),forma.importe()));
        medios.add(tarjeta("TOTAL OPERACIONES",r.moneda(),r.totalOperaciones()));
        resumen.add(sesion,new H4("RESUMEN DE CAJA"),totales,new H4("FORMAS DE PAGO"),medios);
    }
    private static Div tarjeta(String titulo,String moneda,BigDecimal importe){
        Span label=new Span(titulo);Span valor=new Span(moneda+" "+PosNumberFormatter.money(importe));
        Div card=new Div(label,valor);card.addClassName("cc-cash-summary-card");return card;
    }
    private static String documento(MovimientoCajaDto m){
        String valor=m.origen()==TipoOrigenMovimiento.MANUAL?m.numero():m.referencia();
        return valor==null||valor.isBlank()||valor.equals("MOVIMIENTO")?"—":valor;
    }
    private void reiniciar(){paginacion.reset();cargar();}
    public void recargarPrimeraPagina(){reiniciar();}


    private static String dinero(MovimientoCajaDto m){return m.monedaCodigo()+" "+PosNumberFormatter.money(m.importe());}
    private static String fechaHora(java.time.OffsetDateTime valor){return valor==null?"—":FECHA.format(valor.atZoneSameInstant(ZoneId.systemDefault()));}
}
