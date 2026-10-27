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

public class MovimientosCajaDialog extends Dialog {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final UUID sesionId;
    private final MovimientoCajaService service;
    private final boolean puedeCrear;
    private final boolean puedeRevertir;
    private final MovimientoCajaContextoDto contexto;
    private final TextField buscar=new TextField("Buscar");
    private final Select<TipoMovimientoFinanciero> tipo=new Select<>();
    private final Select<TipoOrigenMovimiento> origen=new Select<>();
    private final AppGrid<MovimientoCajaDto> grid=new AppGrid<>(MovimientoCajaDto.class);
    private final AppPagination paginacion;

    public MovimientosCajaDialog(UUID sesionId,MovimientoCajaService service,boolean puedeCrear,boolean puedeRevertir){
        this.sesionId=Objects.requireNonNull(sesionId);this.service=Objects.requireNonNull(service);
        this.puedeCrear=puedeCrear;this.puedeRevertir=puedeRevertir;this.contexto=service.contexto(sesionId);
        setHeaderTitle("MOVIMIENTOS DE CAJA");setWidth("min(1280px, 96vw)");setMaxHeight("92vh");
        addClassName("cc-cash-movements-dialog");
        configurarFiltros();configurarGrid();
        paginacion=new AppPagination(req->cargar(),5,5,10,20,50);
        Div encabezado=new Div(new H3(contexto.caja()+" · "+contexto.sucursal()),estado());
        encabezado.addClassName("cc-cash-movement-heading");
        Div filtros=new Div(buscar,tipo,origen);filtros.addClassNames("cc-filter-bar","cc-cash-movement-filters");
        VerticalLayout contenido=new VerticalLayout(encabezado,filtros,grid,paginacion);contenido.setPadding(false);contenido.setSpacing(false);
        add(contenido);
        if(puedeCrear)getFooter().add(new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nuevo movimiento",e->nuevo()));
        getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->close()));
        cargar();
    }

    private Span estado(){Span estado=new Span(contexto.abierta()?"● Abierta":"● Cerrada");
        estado.addClassName(contexto.abierta()?"cc-cash-movement-open":"cc-cash-movement-closed");return estado;}
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
        grid.addColumn(x->fechaHora(x.fechaHora())).setHeader("Fecha/Hora").setAutoWidth(true);
        grid.addColumn(MovimientoCajaDto::concepto).setHeader("Concepto").setFlexGrow(1);
        grid.addColumn(x->origen(x.origen())).setHeader("Origen").setAutoWidth(true);
        grid.addColumn(MovimientoCajaDto::referencia).setHeader("Referencia").setAutoWidth(true);
        grid.addColumn(x->x.tipo()==TipoMovimientoFinanciero.INCOME?dinero(x):"").setHeader("Entrada").setAutoWidth(true)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addColumn(x->x.tipo()==TipoMovimientoFinanciero.EXPENSE?dinero(x):"").setHeader("Salida").setAutoWidth(true)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addColumn(x->x.monedaCodigo()+" "+PosNumberFormatter.money(x.saldo())).setHeader("Saldo").setAutoWidth(true)
            .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addComponentColumn(x->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver movimiento",e->ver(x.id())))
            .setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No se encontraron movimientos de caja.");
    }
    private void cargar(){try{var p=paginacion.currentRequest();var pagina=service.buscar(sesionId,buscar.getValue(),tipo.getValue(),origen.getValue(),p.page(),p.size());
        grid.setItems(pagina.getContent());paginacion.setTotal(pagina.getTotalElements());}
        catch(RuntimeException ex){grid.setItems(List.of());paginacion.setTotal(0);UiErrorHandler.show(ex,"No fue posible consultar los movimientos de caja.");}}
    private void reiniciar(){paginacion.reset();cargar();}

    private void nuevo(){
        MovimientoCajaContextoDto actual=service.contexto(sesionId);if(!actual.abierta()){UiErrorHandler.show(
            new IllegalStateException(),"La caja debe estar abierta para registrar un movimiento.");return;}
        Dialog d=new Dialog();d.setHeaderTitle("NUEVO MOVIMIENTO DE CAJA");d.setWidth("min(640px, 96vw)");
        RadioButtonGroup<TipoMovimientoFinanciero> direccion=new RadioButtonGroup<>("Tipo");direccion.setItems(TipoMovimientoFinanciero.values());
        direccion.setItemLabelGenerator(MovimientosCajaDialog::tipo);direccion.setValue(TipoMovimientoFinanciero.INCOME);
        ComboBox<ConceptoMovimientoCajaDto> concepto=new ComboBox<>("Concepto");concepto.setRequiredIndicatorVisible(true);
        concepto.setItemLabelGenerator(ConceptoMovimientoCajaDto::nombre);concepto.setWidthFull();
        BigDecimalField importe=new BigDecimalField("Importe");importe.setRequiredIndicatorVisible(true);importe.setPrefixComponent(new Span(actual.monedaCodigo()));
        TextField referencia=new TextField("Referencia");referencia.setMaxLength(100);referencia.setWidthFull();
        TextArea observacion=new TextArea("Observación");observacion.setMaxLength(500);observacion.setWidthFull();
        PosNumericKeypad teclado=new PosNumericKeypad(PosNumericKeypad.Mode.POPOVER);
        var tecladoBoton=teclado.bind(importe,d,PosNumericKeypad.Options.money(()->{}),v->{});
        HorizontalLayout importeFila=new HorizontalLayout(importe,tecladoBoton);importeFila.setAlignItems(FlexComponent.Alignment.END);importeFila.setWidthFull();importe.setWidthFull();importeFila.expand(importe);
        Runnable conceptos=()->{concepto.clear();concepto.setItems(actual.conceptos().stream().filter(x->x.tipo()==direccion.getValue()).toList());};
        direccion.addValueChangeListener(e->conceptos.run());conceptos.run();
        Div formulario=new Div(direccion,concepto,importeFila,referencia,observacion,teclado);formulario.addClassName("cc-cash-movement-form");d.add(formulario);
        UUID clave=UUID.randomUUID();AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar movimiento",null);
        guardar.addClickListener(e->{
            guardar.setEnabled(false);try{service.crear(new MovimientoCajaInput(sesionId,direccion.getValue(),
                concepto.getValue()==null?null:concepto.getValue().id(),importe.getValue(),referencia.getValue(),observacion.getValue(),clave));
                d.close();reiniciar();com.vaadin.flow.component.notification.Notification.show("Movimiento de caja registrado correctamente.");
            }catch(RuntimeException ex){guardar.setEnabled(true);UiErrorHandler.show(ex,"No fue posible registrar el movimiento de caja.");}});
        d.getFooter().add(guardar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->{teclado.close();d.close();}));d.open();
    }

    private void ver(UUID id){try{MovimientoCajaDto m=service.obtener(sesionId,id);Dialog d=new Dialog();d.setHeaderTitle("VER MOVIMIENTO DE CAJA");d.setWidth("min(760px, 96vw)");
        AppDetailSection detalle=new AppDetailSection("Información").field("Número",m.numero()).field("Fecha/Hora",fechaHora(m.fechaHora()))
            .field("Caja",m.caja()+" · "+m.sucursal()).field("Usuario",m.usuario()).field("Tipo",tipo(m.tipo()))
            .field("Origen",origen(m.origen())).field("Concepto",m.concepto()).field("Referencia",m.referencia())
            .field("Importe",dinero(m)).field("Saldo posterior",m.monedaCodigo()+" "+PosNumberFormatter.money(m.saldo()));
        d.add(detalle);if(m.observacion()!=null&&!m.observacion().isBlank()){Paragraph nota=new Paragraph(m.observacion());nota.addClassName("cc-cash-movement-observation");d.add(new H4("Observación"),nota);}
        if(puedeRevertir&&m.reversible())d.getFooter().add(new AppActionButton(ActionType.REVERSE,ButtonSize.MAIN,"Revertir movimiento",e->confirmarReversion(d,m)));
        d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
    }catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible consultar el movimiento de caja.");}}
    private void confirmarReversion(Dialog detalle,MovimientoCajaDto m){Dialog d=new Dialog();d.setHeaderTitle("REVERTIR MOVIMIENTO");
        Paragraph aviso=new Paragraph("Se creará un movimiento de reversión por "+dinero(m)+". El movimiento original permanecerá en el historial.");
        TextArea motivo=new TextArea("Motivo de reversión");motivo.setRequiredIndicatorVisible(true);motivo.setMaxLength(500);motivo.setWidthFull();d.add(aviso,motivo);
        UUID clave=UUID.randomUUID();AppActionButton confirmar=new AppActionButton(ActionType.REVERSE,ButtonSize.MAIN,"Revertir movimiento",null);
        confirmar.addClickListener(e->{confirmar.setEnabled(false);
            try{service.revertir(new ReversionMovimientoCajaInput(sesionId,m.id(),motivo.getValue(),clave));d.close();detalle.close();reiniciar();
                com.vaadin.flow.component.notification.Notification.show("Movimiento revertido correctamente.");}
            catch(RuntimeException ex){confirmar.setEnabled(true);UiErrorHandler.show(ex,"No fue posible revertir el movimiento de caja.");}});
        d.getFooter().add(confirmar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}

    private static String dinero(MovimientoCajaDto m){return m.monedaCodigo()+" "+PosNumberFormatter.money(m.importe());}
    private static String fechaHora(java.time.OffsetDateTime valor){return valor==null?"—":FECHA.format(valor.atZoneSameInstant(ZoneId.systemDefault()));}
    static String tipo(TipoMovimientoFinanciero tipo){if(tipo==null)return "Todos";return tipo==TipoMovimientoFinanciero.INCOME?"Entrada":"Salida";}
    static String origen(TipoOrigenMovimiento origen){if(origen==null)return "Todos";return switch(origen){
        case OPENING_BALANCE->"Apertura";case SALES_INVOICE->"Venta";case SALES_PAYMENT,CUSTOMER_RECEIPT->"Recibo/Cobro";
        case SUPPLIER_PAYMENT->"Pago a proveedor";case TRANSFER->"Transferencia";case MANUAL->"Movimiento manual";
        case REVERSAL->"Reversión";case CASH_VOUCHER->"Vale de caja";case ADJUSTMENT->"Ajuste";case OTHER->"Otro";};}
}
