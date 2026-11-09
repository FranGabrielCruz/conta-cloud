package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import com.citacloud.springboot.contacloud.app.repositories.PaymentReceiptSnapshotRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="recibos",layout=MainLayout.class)
@PageTitle("Recibos | ContaCloud")
@PermitAll
public class RecibosVentaView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA_DOCUMENTO=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final ReciboHistorialService historial;
    private final CobroClienteService cobros;
    private final CobroClientePdfService pdf;
    private final EmpresaModuloService modulos;
    private final AppGrid<ReciboHistorialDto.Resumen> grid=new AppGrid<>(ReciboHistorialDto.Resumen.class);
    private final AppPagination paginas=new AppPagination(r->cargar(),10,10,25,50);
    private final TextField buscar=new TextField("Buscar");
    private final DatePicker desde=fecha("Desde"),hasta=fecha("Hasta");
    private final Select<Boolean> estado=new Select<>();

    public RecibosVentaView(ReciboHistorialService historial,CobroClienteService cobros,CobroClientePdfService pdf,EmpresaModuloService modulos) {
        this.historial=historial;this.cobros=cobros;this.pdf=pdf;this.modulos=modulos;
        setPadding(false);setSpacing(false);setWidthFull();addClassName("cc-page");
        buscar.setPlaceholder("Recibo, cliente, documento o referencia…");buscar.setMaxLength(150);
        buscar.setWidthFull();buscar.setClearButtonVisible(true);buscar.setValueChangeMode(ValueChangeMode.LAZY);
        estado.setLabel("Estado");estado.setItems(false,true);estado.setItemLabelGenerator(v->Boolean.TRUE.equals(v)?"Anulado":"Emitido");
        estado.setEmptySelectionAllowed(true);estado.setEmptySelectionCaption("Todos");
        buscar.addValueChangeListener(e->reiniciar());desde.addValueChangeListener(e->reiniciar());
        hasta.addValueChangeListener(e->reiniciar());estado.addValueChangeListener(e->reiniciar());
        grid.addColumn(ReciboHistorialDto.Resumen::numero).setHeader("Recibo").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(r->FECHA.format(r.fecha())).setHeader("Fecha").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(ReciboHistorialDto.Resumen::cliente).setHeader("Cliente").setFlexGrow(2);
        grid.addComponentColumn(r->documentos(r.documentos())).setHeader("Documento(s)").setAutoWidth(true);
        grid.addColumn(r->importe(r.moneda(),r.total())).setHeader("Total").setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(r->estado(r.estado())).setHeader("Estado").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::acciones)
            .setHeader("Acc.").setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        grid.setEmptyStateText("No se encontraron recibos.");
        var filtros=new Div(buscar,desde,hasta,estado);filtros.addClassNames("cc-filter-bar","cc-debit-note-filters");
        add(new AppPageHeader("RECIBOS","Consulta de recibos de cobro emitidos."),filtros,grid,paginas);
    }

    @Override public void beforeEnter(BeforeEnterEvent e) {
        if(!modulos.habilitado("VENTAS")||!permiso("recibos.ver")){
            Notification.show("No tienes permiso para consultar recibos.");e.rerouteTo(DashboardView.class);return;
        }
        cargar();
    }
    private void reiniciar(){paginas.reset();cargar();}
    private void cargar(){
        try{
            var p=paginas.currentRequest();
            var result=historial.buscar(buscar.getValue(),desde.getValue(),hasta.getValue(),estado.getValue(),p.page(),p.size());
            grid.setItems(result.getContent());
            grid.recalculateColumnWidths();
            paginas.setTotal(result.getTotalElements());
        }catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible consultar los recibos.");}
    }
    private static Span documentos(List<String> documentos){
        var texto=new Span(documentos.isEmpty()?"—":documentos.size()==1?documentos.getFirst():documentos.size()+" documentos");
        if(documentos.size()>1)texto.getElement().setAttribute("title",String.join("\n",documentos));
        return texto;
    }
    Component acciones(ReciboHistorialDto.Resumen recibo){
        var acciones=new Div();
        acciones.addClassNames("cc-grid-actions","cc-receipt-grid-actions");
        if(permiso("recibos.ver"))
            acciones.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver recibo",e->abrir(recibo.id())));
        return acciones;
    }

    void abrir(UUID id){
        try{crearDetalle(historial.detalle(id)).open();}
        catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible abrir el recibo.");}
    }

    Dialog crearDetalle(ReciboHistorialDto detalle){
        var dialog=new Dialog();
        dialog.setHeaderTitle("RECIBO "+detalle.recibo().numero());
        dialog.setWidth("min(1100px,96vw)");
        dialog.setMaxHeight("92vh");
        actualizarDetalle(dialog,detalle);
        return dialog;
    }

    private void actualizarDetalle(Dialog dialog,ReciboHistorialDto detalle){
        var r=detalle.recibo();var d=detalle.documento();
        var info=new AppDetailSection("INFORMACIÓN")
            .field("Fecha",FECHA.format(d.receiptDateTime())).field("Cliente",d.customer().name())
            .field("RNC/Cédula",valor(d.customer().identification())).field("Moneda",r.moneda())
            .field("Estado",estado(r.estado())).field("Total cobrado",importe(r.moneda(),d.totalCollected()))
            .field("Caja",valor(d.cashRegister())).field("Usuario",valor(d.operator()));
        info.addClassName("cc-detail-four");
        var aplicaciones=new AppGrid<CobroClienteDto.Aplicacion>(CobroClienteDto.Aplicacion.class);
        aplicaciones.addColumn(CobroClienteDto.Aplicacion::factura).setHeader("Documento").setFlexGrow(2);
        aplicaciones.addColumn(a->a.fechaFactura()==null?"—":FECHA_DOCUMENTO.format(a.fechaFactura())).setHeader("Fecha");
        aplicaciones.addColumn(a->importe(r.moneda(),a.totalDocumento())).setHeader("Total documento").setTextAlign(ColumnTextAlign.END);
        aplicaciones.addColumn(a->importe(r.moneda(),a.monto())).setHeader("Aplicado en este recibo").setTextAlign(ColumnTextAlign.END);
        aplicaciones.setItems(r.aplicaciones());aplicaciones.setEmptyStateText("Sin documentos aplicados.");
        var medios=new AppGrid<com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument.PaymentMethod>(
            com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument.PaymentMethod.class);
        medios.addColumn(m->m.name()+(m.reference()==null?"":" · "+m.reference())).setHeader("Forma de pago").setFlexGrow(2);
        medios.addColumn(m->importe(r.moneda(),m.amount())).setHeader("Importe").setTextAlign(ColumnTextAlign.END);
        medios.setItems(d.paymentMethods().stream().filter(m->m.amount().signum()>0).toList());
        var totals=new AppDetailSection("TOTALES")
            .field("Total aplicado",importe(r.moneda(),r.aplicado())).field("Total cobrado",importe(r.moneda(),d.totalCollected()));
        if(r.disponible().signum()>0)totals.field("Disponible",importe(r.moneda(),r.disponible()));
        var cuerpo=new VerticalLayout(info,new H4("DOCUMENTOS PAGADOS"),aplicaciones,new H4("FORMAS DE PAGO"),medios,totals);
        cuerpo.setPadding(false);cuerpo.addClassName("cc-receipt-detail");
        String ref=PaymentReceiptSnapshotRepository.safeReference(r.referencia(),null);
        if(ref!=null||r.observacion()!=null&&!r.observacion().isBlank()){
            var adicional=new AppDetailSection("INFORMACIÓN ADICIONAL");
            if(ref!=null)adicional.field("Referencia",ref);
            if(r.observacion()!=null&&!r.observacion().isBlank())adicional.field("Observación",r.observacion());
            cuerpo.add(adicional);
        }
        if(r.estado()==EstadoReciboCliente.VOIDED)cuerpo.add(new AppDetailSection("ANULACIÓN")
            .field("Motivo",valor(r.motivoAnulacion()))
            .field("Fecha",detalle.anuladoEn()==null?"—":FECHA.format(detalle.anuladoEn()))
            .field("Anulado por",valor(detalle.anuladoPor())));
        dialog.removeAll();
        dialog.add(cuerpo);
        dialog.getFooter().removeAll();
        if(permiso("recibos.imprimir"))dialog.getFooter().add(new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Imprimir recibo",e->imprimir(r.id())));
        if(r.estado()!=EstadoReciboCliente.VOIDED && detalle.anulable() && permiso("recibos.anular"))
            dialog.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular recibo",
                e->anular(r.id(),r.numero(),()->refrescarTrasAnulacion(dialog,r.id()))));
        dialog.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->dialog.close()));
    }

    private void refrescarTrasAnulacion(Dialog dialog,UUID id){
        cargar();
        try{
            actualizarDetalle(dialog,historial.detalle(id));
        }catch(RuntimeException ex){
            UiErrorHandler.show(ex,"No fue posible actualizar el detalle del recibo.");
        }
    }

    void anular(UUID id,String numero,Runnable despuesDeAnular) {
        confirmarAnulacion(id,numero,despuesDeAnular).open();
    }

    Dialog confirmarAnulacion(UUID id,String numero,Runnable despuesDeAnular) {
        var dialogo=new Dialog();
        dialogo.setHeaderTitle("ANULAR RECIBO");
        var motivo=new TextArea("Motivo de anulación");
        motivo.setWidthFull();
        motivo.setMaxLength(CobroClienteService.LONGITUD_MOTIVO_ANULACION);
        motivo.setRequiredIndicatorVisible(true);
        motivo.addValueChangeListener(e->motivo.setInvalid(false));
        dialogo.add(new Paragraph("¿Desea anular el recibo "+numero+"?"),
            new Paragraph("Se revertirán sus aplicaciones y movimientos financieros. El recibo permanecerá en el historial como Anulado."),
            motivo);
        var aceptar=new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular recibo",null);
        aceptar.addClickListener(e->{
            String texto=motivo.getValue().strip();
            if(texto.isEmpty()){
                motivo.setInvalid(true);
                motivo.setErrorMessage("Indica el motivo de anulación.");
                motivo.focus();
                return;
            }
            aceptar.setEnabled(false);
            CobroClienteService.ResultadoAnulacion resultado;
            try {
                resultado=cobros.anularConResultado(id,texto);
            } catch(org.springframework.security.access.AccessDeniedException ex) {
                Notification.show("No tiene permisos para anular este recibo.");
                aceptar.setEnabled(true);
                return;
            } catch(RuntimeException ex) {
                UiErrorHandler.show(ex,"No fue posible anular el recibo.");
                aceptar.setEnabled(true);
                return;
            }
            dialogo.close();
            Notification.show(resultado.yaAnulado()?"El recibo ya se encuentra anulado.":
                "El recibo "+resultado.recibo().numero()+" fue anulado correctamente.");
            despuesDeAnular.run();
        });
        dialogo.getFooter().add(aceptar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->dialogo.close()));
        return dialogo;
    }

    void imprimir(UUID id) {
        try {
            PdfEnPestana.abrir(pdf.generar(id));
        } catch(RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible generar el PDF del recibo.");
        }
    }

    private static DatePicker fecha(String label){
        var d=new DatePicker(label);d.setLocale(Locale.forLanguageTag("es-DO"));
        d.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));d.setClearButtonVisible(true);return d;
    }
    static String estado(EstadoReciboCliente estado){return estado==EstadoReciboCliente.VOIDED?"Anulado":"Emitido";}
    static String importe(String moneda,BigDecimal valor){return valor==null?"—":moneda+" "+PosNumberFormatter.money(valor);}
    private static String valor(String s){return s==null||s.isBlank()?"—":s;}
    private static boolean permiso(String permiso){return TenantContext.principalActual().permisos().contains(permiso);}
}
