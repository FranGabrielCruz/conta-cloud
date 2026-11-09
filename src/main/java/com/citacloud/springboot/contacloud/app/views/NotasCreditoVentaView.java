package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.repositories.NotaCreditoVentaConsultaRepository.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.*;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="notas-credito",layout=MainLayout.class)
@PageTitle("Notas de crédito | ContaCloud") @PermitAll
public class NotasCreditoVentaView extends VerticalLayout implements BeforeEnterObserver {
    @org.springframework.beans.factory.annotation.Autowired private UltimasFacturasService facturasHistoricas;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final NotaCreditoVentaService service;private final NotaCreditoVentaPdfService pdf;private final EmpresaModuloService modulos;
    private final TextField buscar=new TextField("Buscar"),cliente=new TextField("Cliente");
    private final DatePicker desde=fecha("Desde"),hasta=fecha("Hasta");
    private final Select<String> estado=new Select<>();private final AppGrid<Resumen> grid=new AppGrid<>(Resumen.class);
    private final AppPagination pagination;
    public NotasCreditoVentaView(NotaCreditoVentaService service,NotaCreditoVentaPdfService pdf,EmpresaModuloService modulos){
        this.service=service;this.pdf=pdf;this.modulos=modulos;setPadding(false);setSpacing(false);setWidthFull();addClassName("cc-page");
        var nuevo=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nueva nota de crédito",e->nueva());nuevo.setVisible(puede("notas_credito.crear"));
        add(new AppPageHeader("NOTAS DE CRÉDITO","Consulta las notas emitidas y prepara créditos sobre facturas.",nuevo));
        buscar.setPlaceholder("Nota, factura o cliente...");buscar.setClearButtonVisible(true);cliente.setClearButtonVisible(true);
        estado.setLabel("Estado");estado.setItems("Emitida","Anulada");estado.setEmptySelectionAllowed(true);estado.setEmptySelectionCaption("Todos");
        pagination=new AppPagination(r->cargar(),10,10,20,50);
        for(TextField f:List.of(buscar,cliente)){f.setValueChangeMode(ValueChangeMode.LAZY);f.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});}
        desde.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});hasta.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});estado.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        grid.addColumn(Resumen::numero).setHeader("Nº nota").setAutoWidth(true);grid.addColumn(x->DATE.format(x.fecha())).setHeader("Fecha").setAutoWidth(true);
        grid.addColumn(Resumen::cliente).setHeader("Cliente").setFlexGrow(1);grid.addColumn(Resumen::factura).setHeader("Factura").setAutoWidth(true);
        grid.addColumn(x->valor(x.ncf())).setHeader("NCF").setAutoWidth(true);grid.addColumn(x->money(x.moneda(),x.total())).setHeader("Total").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(x->EstadoDocumentoVenta.nota(x.estado())).setHeader("Estado").setKey("estado").setAutoWidth(true).setFlexGrow(0);grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);grid.setEmptyStateText("No se encontraron notas de crédito.");
        Div filtros=new Div(buscar,cliente,estado,desde,hasta);filtros.addClassNames("cc-filter-bar","cc-credit-note-filters");add(filtros,grid,pagination);
    }
    @Override public void beforeEnter(BeforeEnterEvent event){if(!modulos.habilitado("VENTAS")||!puede("notas_credito.ver")){Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}cargar();}
    Component acciones(Resumen n){return new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver nota de crédito",e->{try{ver(service.obtener(n.id()));}catch(RuntimeException ex){error(ex);}});}

    private void cargar(){try{var p=pagination.currentRequest();var page=service.buscar(buscar.getValue(),cliente.getValue(),desde.getValue(),hasta.getValue(),p.page(),p.size(),estado.getValue()==null?"":"Anulada".equals(estado.getValue())?"VOIDED":"CONFIRMED");grid.setItems(page.getContent());pagination.setTotal(page.getTotalElements());}catch(RuntimeException ex){grid.setItems(List.of());pagination.setTotal(0);error(ex);}}
    private void reiniciar(){pagination.reset();cargar();}
    private void nueva(){
        Dialog d=new Dialog();boolean[] dirty={false};d.setCloseOnEsc(false);d.setCloseOnOutsideClick(false);d.setHeaderTitle("NUEVA NOTA DE CRÉDITO");d.setWidth("min(1180px,96vw)");d.setMaxHeight("92vh");
        ComboBox<FacturaOpcion> factura=crearSelectorFactura();
        DatePicker fecha=fecha("Fecha");fecha.setValue(service.fechaActual());fecha.setRequiredIndicatorVisible(true);
        ComboBox<PreparacionNotaCreditoVentaDto.Motivo> motivo=new ComboBox<>("Motivo");motivo.setItemLabelGenerator(PreparacionNotaCreditoVentaDto.Motivo::nombre);motivo.setRequiredIndicatorVisible(true);
        TextArea descripcion=new TextArea("Descripción / motivo detallado");descripcion.setMaxLength(1000);descripcion.setRequiredIndicatorVisible(true);descripcion.setWidthFull();
        TextArea observacion=new TextArea("Observación");observacion.setMaxLength(1000);observacion.setWidthFull();
        Div origen=new Div(),totales=new Div();origen.setWidthFull();totales.setWidthFull();var detalles=new AppGrid<Seleccion>(Seleccion.class);List<Seleccion> cantidades=new ArrayList<>();
        detalles.addColumn(s->s.linea.descripcion()).setHeader("Producto / concepto").setFlexGrow(1);detalles.addColumn(s->s.linea.facturada()).setHeader("Facturada").setAutoWidth(true);
        detalles.addColumn(s->s.linea.acreditada()).setHeader("Acreditada").setAutoWidth(true);detalles.addColumn(s->s.linea.disponible()).setHeader("Disponible").setAutoWidth(true);detalles.addComponentColumn(s->s.cantidad).setHeader("Cant. crédito").setAutoWidth(true);
        detalles.addColumn(s->PosNumberFormatter.money(s.importe)).setHeader("Importe").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        UUID intento=UUID.randomUUID();AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar nota de crédito",null);
        java.util.function.Supplier<EmitirNotaCreditoVentaInput> input=()->new EmitirNotaCreditoVentaInput(factura.getValue()==null?null:factura.getValue().id(),fecha.getValue(),motivo.getValue()==null?null:motivo.getValue().id(),descripcion.getValue(),observacion.getValue(),cantidades.stream().map(s->new EmitirNotaCreditoVentaInput.Linea(s.linea.id(),s.cantidad.getValue()==null?BigDecimal.ZERO:s.cantidad.getValue())).toList(),intento);
        Runnable recalcular=()->{guardar.setEnabled(false);totales.removeAll();cantidades.forEach(s->s.importe=BigDecimal.ZERO);detalles.getDataProvider().refreshAll();if(factura.getValue()==null||motivo.getValue()==null||descripcion.isEmpty()||cantidades.stream().noneMatch(s->s.cantidad.getValue()!=null&&s.cantidad.getValue().signum()>0))return;try{var preview=service.calcular(input.get());for(var s:cantidades)preview.lineas().stream().filter(l->l.lineaFacturaId().equals(s.linea.id())).findFirst().ifPresent(l->s.importe=l.total());detalles.getDataProvider().refreshAll();totales.add(resumen(preview));guardar.setEnabled(puede("notas_credito.emitir"));}catch(RuntimeException ex){error(ex);}};
        factura.addValueChangeListener(e->{dirty[0]=true;origen.removeAll();cantidades.clear();detalles.setItems(List.of());guardar.setEnabled(false);totales.removeAll();if(e.getValue()==null)return;try{
            var prep=service.preparar(e.getValue().id());motivo.clear();motivo.setItems(prep.motivos());
            origen.add(facturaOrigen(prep));
            for(var l:prep.lineas()){var seleccion=new Seleccion(l);seleccion.cantidad.addValueChangeListener(ev->{if(ev.isFromClient()){dirty[0]=true;recalcular.run();}});cantidades.add(seleccion);}detalles.setItems(cantidades);
        }catch(RuntimeException ex){error(ex);}});
        motivo.addValueChangeListener(e->{if(e.isFromClient()){dirty[0]=true;recalcular.run();}});descripcion.setValueChangeMode(ValueChangeMode.LAZY);descripcion.addValueChangeListener(e->{if(e.isFromClient()){dirty[0]=true;recalcular.run();}});fecha.addValueChangeListener(e->{if(e.isFromClient()){dirty[0]=true;recalcular.run();}});observacion.addValueChangeListener(e->{if(e.isFromClient())dirty[0]=true;});
        guardar.addClickListener(e->{try{var request=input.get();var preview=service.calcular(request);confirmar(d,request,preview);}catch(RuntimeException ex){error(ex);}});
        guardar.setEnabled(false);HorizontalLayout campos=new HorizontalLayout(fecha,motivo);campos.setWidthFull();motivo.setWidthFull();
        var seleccionar=new AppActionButton(ActionType.VIEW,ButtonSize.MAIN,"Ver factura",e->{if(factura.getValue()!=null)verFactura(factura.getValue().id());});
        seleccionar.setEnabled(false);factura.addValueChangeListener(e->seleccionar.setEnabled(e.getValue()!=null&&puede("facturas.ver")));
        var selector=new HorizontalLayout(factura,seleccionar);selector.setAlignItems(Alignment.END);selector.setWidthFull();selector.setFlexGrow(1,factura);
        d.add(selector,origen,new H4("DATOS DE LA NOTA"),campos,descripcion,new Paragraph("Crédito financiero: no modifica inventario ni genera un reembolso automático."),new H4("DETALLE"),detalles,totales,observacion);
        d.getFooter().add(guardar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->cancelar(d,dirty[0])));d.open();
    }
    static AppDetailSection facturaOrigen(PreparacionNotaCreditoVentaDto p){
        var section=new AppDetailSection("FACTURA DE ORIGEN").field("Factura",p.factura()).field("Fecha",DATE.format(p.fechaFactura())).field("Cliente",NotaCreditoVentaService.nombreCliente(p.cliente())).field("Moneda",p.moneda()).field("Total factura",money(p.moneda(),p.totalFactura())).field("Total acreditado",money(p.moneda(),p.acreditado())).field("Disponible para acreditar",money(p.moneda(),p.disponible())).field("Condición",valor(p.condicionPago()));section.addClassName("cc-detail-four");return section;
    }
    static void cancelar(Dialog form,boolean dirty){
        if(!dirty){form.close();return;}var confirm=confirmacionDescartar(form);confirm.open();
    }
    static com.vaadin.flow.component.confirmdialog.ConfirmDialog confirmacionDescartar(Dialog form){
        var confirm=new com.vaadin.flow.component.confirmdialog.ConfirmDialog();confirm.setHeader("Descartar cambios");confirm.setText("Hay cambios sin guardar. ¿Deseas descartarlos?");confirm.setCancelable(true);confirm.setCancelText("Continuar editando");confirm.setConfirmText("Descartar cambios");confirm.setConfirmButtonTheme("error primary");confirm.addConfirmListener(e->form.close());return confirm;
    }
    private void verFactura(UUID id){
        try{var f=facturasHistoricas.detalle(id);var dialog=new Dialog();dialog.setHeaderTitle("FACTURA "+f.numero());dialog.setWidth("min(1100px,96vw)");
            dialog.add(new AppDetailSection("FACTURA DE ORIGEN").field("Factura",f.numero()).field("Cliente",NotaCreditoVentaService.nombreCliente(f.cliente())).field("Moneda",f.moneda()).field("Condición",f.condicionPago()).field("Total",money(f.moneda(),f.total())));
            var rows=new AppGrid<FacturaVentaDetalleDto.Linea>(FacturaVentaDetalleDto.Linea.class);rows.addColumn(FacturaVentaDetalleDto.Linea::producto).setHeader("Producto");rows.addColumn(x->PosNumberFormatter.quantity(x.cantidad())).setHeader("Cantidad");rows.addColumn(x->money(f.moneda(),x.precio())).setHeader("Precio");rows.addColumn(x->money(f.moneda(),x.total())).setHeader("Total");rows.setItems(f.lineas());dialog.add(rows);dialog.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->dialog.close()));dialog.open();
        }catch(RuntimeException ex){error(ex);}
    }
    ComboBox<FacturaOpcion> crearSelectorFactura(){
        ComboBox<FacturaOpcion> factura=new ComboBox<>("Factura de origen");factura.setWidthFull();factura.setPageSize(25);factura.setPlaceholder("Buscar por factura o cliente...");factura.setItemLabelGenerator(f->f.numero()+" · "+NotaCreditoVentaService.nombreCliente(f.cliente())+" · "+money(f.moneda(),f.total()));
        factura.setItems(query->service.facturas(query.getFilter().orElse(""),query.getPage(),query.getPageSize()).stream());
        return factura;
    }
    private void seleccionarFactura(ComboBox<FacturaOpcion> campo){
        Dialog dialog=new Dialog();dialog.setHeaderTitle("SELECCIONAR FACTURA");dialog.setWidth("min(1050px,96vw)");
        TextField texto=new TextField("Buscar");texto.setPlaceholder("Número de factura o cliente...");texto.setClearButtonVisible(true);texto.setWidthFull();texto.setValueChangeMode(ValueChangeMode.LAZY);
        var tabla=new AppGrid<FacturaOpcion>(FacturaOpcion.class);final AppPagination[] pages=new AppPagination[1];
        Runnable cargar=()->{try{var p=pages[0].currentRequest();var result=service.facturas(texto.getValue(),p.page(),p.size());tabla.setItems(result.getContent());pages[0].setTotal(result.getTotalElements());}catch(RuntimeException ex){error(ex);}};
        pages[0]=new AppPagination(p->cargar.run(),10,10,20,50);
        tabla.addColumn(FacturaOpcion::numero).setHeader("Factura").setAutoWidth(true);tabla.addColumn(x->DATE.format(x.fecha())).setHeader("Fecha").setAutoWidth(true);tabla.addColumn(FacturaOpcion::cliente).setHeader("Cliente").setFlexGrow(1);tabla.addColumn(x->money(x.moneda(),x.total())).setHeader("Total").setTextAlign(ColumnTextAlign.END);
        tabla.addColumn(x->money(x.moneda(),x.disponible())).setHeader("Disponible").setTextAlign(ColumnTextAlign.END);
        tabla.addComponentColumn(x->new AppActionButton(ActionType.ACCEPT,ButtonSize.GRID_ACTION,"Seleccionar factura",e->{campo.setValue(x);dialog.close();})).setHeader("Acción").setAutoWidth(true).setFlexGrow(0);
        texto.addValueChangeListener(e->{if(e.isFromClient()){pages[0].reset();cargar.run();}});dialog.getHeader().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->dialog.close()));dialog.add(texto,tabla,pages[0]);cargar.run();dialog.open();
    }
    private void confirmar(Dialog form,EmitirNotaCreditoVentaInput input,NotaCreditoVentaDocumento preview){
        Dialog c=new Dialog();c.setHeaderTitle("EMITIR NOTA DE CRÉDITO");c.add(new Paragraph("Se emitirá una nota por "+money(preview.moneda(),preview.total())+" contra la factura "+preview.factura()+". La operación aplicará el crédito financiero correspondiente."));
        var aceptar=new AppActionButton(ActionType.ACCEPT,ButtonSize.MAIN,"Emitir nota de crédito",null);aceptar.setDisableOnClick(true);aceptar.addClickListener(e->{try{var issued=service.emitir(input);c.close();form.close();reiniciar();ver(issued);}catch(RuntimeException ex){aceptar.setEnabled(true);error(ex);}});
        c.getFooter().add(aceptar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->c.close()));c.open();
    }
    private void ver(NotaCreditoVentaDocumento n){
        Dialog d=new Dialog();d.setHeaderTitle("NOTA DE CRÉDITO "+n.numero());d.setWidth("min(1100px,96vw)");d.setMaxHeight("92vh");
        var ciclo=service.ciclo(n.id());d.add(informacionNota(n,ciclo),new H4("MOTIVO"),new Paragraph(n.motivo()),new Paragraph(valor(n.descripcion())));
        if("VOIDED".equals(ciclo.estado()))d.add(new AppDetailSection("ANULACIÓN").field("Motivo",ciclo.motivo()).field("Anulada por",ciclo.anuladaPor()).field("Fecha",ciclo.anuladaEn()==null?"—":ciclo.anuladaEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
        var ls=new AppGrid<NotaCreditoVentaDocumento.Linea>(NotaCreditoVentaDocumento.Linea.class);ls.addColumn(x->PosNumberFormatter.quantityInput(x.cantidad())).setHeader("Cant.").setAutoWidth(true);ls.addColumn(NotaCreditoVentaDocumento.Linea::descripcion).setHeader("Descripción").setFlexGrow(1);
        ls.addColumn(x->money(n.moneda(),x.precio())).setHeader("Precio").setTextAlign(ColumnTextAlign.END);ls.addColumn(x->money(n.moneda(),x.descuento())).setHeader("Desc.").setTextAlign(ColumnTextAlign.END);ls.addColumn(x->money(n.moneda(),x.impuesto())).setHeader("Impuesto").setTextAlign(ColumnTextAlign.END);ls.addColumn(x->money(n.moneda(),x.total())).setHeader("Importe").setTextAlign(ColumnTextAlign.END);ls.setItems(n.lineas());d.add(new H4("DETALLE"),ls,resumen(n));
        if(n.saldoAnterior()!=null)d.add(new AppDetailSection("APLICACIÓN DEL CRÉDITO").field("Saldo anterior",money(n.moneda(),n.saldoAnterior())).field("Crédito aplicado",money(n.moneda(),n.aplicadoCxC())).field("Saldo final",money(n.moneda(),n.saldoPosterior())));
        if(n.creditoCliente().signum()>0)d.add(new Paragraph("Saldo a favor del cliente: "+money(n.moneda(),n.creditoCliente())));
        if(n.observacion()!=null&&!n.observacion().isBlank())d.add(new H4("OBSERVACIONES"),new Paragraph(n.observacion()));
        if(n.emitidaPor()!=null)d.add(new Paragraph("Emitida por: "+n.emitidaPor()));
        d.getFooter().add(accionesDetalle(d,n,ciclo).toArray(Component[]::new));d.open();
    }
    List<AppActionButton> accionesDetalle(Dialog dialog,NotaCreditoVentaDocumento nota,NotaCreditoVentaCicloDto ciclo){
        List<AppActionButton> acciones=new ArrayList<>();
        if(puede("notas_credito.pdf"))acciones.add(botonImprimir(nota.id()));
        if(puede("notas_credito.anular")&&"CONFIRMED".equals(ciclo.estado())&&ciclo.anulable())acciones.add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular nota de crédito",e->confirmarAnulacion(dialog,nota,ciclo)));
        acciones.add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->dialog.close()));
        return acciones;
    }
    static AppDetailSection informacionNota(NotaCreditoVentaDocumento n,NotaCreditoVentaCicloDto c){
        var section=new AppDetailSection("INFORMACIÓN / DOCUMENTO ORIGEN").field("Fecha",DATE.format(n.fecha())).badge("Estado",EstadoDocumentoVenta.nota(c.estado())).field("Cliente",NotaCreditoVentaService.nombreCliente(n.cliente())).field("RNC/Cédula",valor(n.identificacion())).field("Factura",n.factura()).field("Fecha factura",DATE.format(n.fechaFactura())).field("NCF afectado",valor(n.ncfFactura())).field("NCF de la nota",valor(n.ncf())).field("Moneda",n.moneda()).field("Origen",c.devolucionId()==null?"Crédito financiero":"Devolución").field("Documento origen",valor(n.devolucion())).field("Estado origen",c.estadoOrigen()==null?"—":"CONFIRMED".equals(c.estadoOrigen())?"Confirmada":"VOIDED".equals(c.estadoOrigen())?"Anulada":c.estadoOrigen());section.addClassName("cc-detail-four");return section;
    }
    private void confirmarAnulacion(Dialog parent,NotaCreditoVentaDocumento n,NotaCreditoVentaCicloDto state){
        Dialog confirm=new Dialog();confirm.setHeaderTitle("ANULAR NOTA DE CRÉDITO");confirm.add(new Paragraph("Está a punto de anular la nota "+n.numero()+". Se revertirán únicamente sus efectos financieros y quedará registrada en el historial."));
        TextArea reason=new TextArea("Motivo");reason.setRequiredIndicatorVisible(true);reason.setMinLength(3);reason.setMaxLength(1000);reason.setWidthFull();confirm.add(reason);UUID key=UUID.randomUUID();
        var accept=new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular nota de crédito",null);accept.setDisableOnClick(true);accept.addClickListener(e->{try{var changed=service.anular(new AnularNotaCreditoVentaInput(n.id(),state.version(),reason.getValue(),key));confirm.close();parent.close();reiniciar();ver(changed);}catch(RuntimeException ex){accept.setEnabled(true);error(ex);}});
        confirm.getFooter().add(accept,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->confirm.close()));confirm.open();
    }
    private Component resumen(NotaCreditoVentaDocumento n){var section=new AppDetailSection("TOTALES").field("Subtotal",money(n.moneda(),n.subtotal())).field("Descuento",money(n.moneda(),n.descuento()));if(n.recargo().signum()>0)section.field("Recargo",money(n.moneda(),n.recargo()));
        var taxes=new LinkedHashMap<String,BigDecimal>();for(var l:n.lineas())if(l.impuesto().signum()!=0)taxes.merge(valor(l.impuestoNombre())+" "+l.tasa().stripTrailingZeros().toPlainString()+"%",l.impuesto(),BigDecimal::add);taxes.forEach((label,amount)->section.field(label,money(n.moneda(),amount)));section.field("TOTAL "+n.moneda(),money(n.moneda(),n.total()));return section;}
    AppActionButton botonImprimir(UUID id){return new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Imprimir nota de crédito",e->abrirPdf(id));}
    private void abrirPdf(UUID id){try{var f=pdf.generar(id);var resource=new StreamResource(f.nombreArchivo(),()->new ByteArrayInputStream(f.contenido()));resource.setContentType("application/pdf");resource.setCacheTime(0);var registration=VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);UI.getCurrent().getPage().open(registration.getResourceUri().toString(),"_blank");}catch(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible generar el PDF. La nota permanece emitida.");}}
    private static DatePicker fecha(String label){var f=new DatePicker(label);f.setLocale(Locale.forLanguageTag("es-DO"));f.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));f.setClearButtonVisible(true);return f;}
    private static boolean puede(String permiso){return TenantContext.principalActual().permisos().contains(permiso);}
    private static String valor(String x){return x==null||x.isBlank()?"—":x;}
    private static String money(String currency,BigDecimal x){return currency+" "+PosNumberFormatter.money(x);}
    private static void error(RuntimeException ex){UiErrorHandler.show(ex,"No fue posible procesar la nota de crédito.");}
    private static final class Seleccion {final PreparacionNotaCreditoVentaDto.Linea linea;BigDecimal importe=BigDecimal.ZERO;final BigDecimalField cantidad=new BigDecimalField();Seleccion(PreparacionNotaCreditoVentaDto.Linea linea){this.linea=linea;cantidad.setValue(BigDecimal.ZERO);cantidad.setAriaLabel("Cantidad a acreditar de "+linea.descripcion());cantidad.setEnabled(linea.disponible().signum()>0);cantidad.setWidth("120px");}}
}
