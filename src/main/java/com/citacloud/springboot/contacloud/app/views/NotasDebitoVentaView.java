package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.repositories.NotaDebitoVentaRepository.*;
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
import java.io.ByteArrayInputStream;import java.math.BigDecimal;import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;

@Route(value="notas-debito",layout=MainLayout.class)
@PageTitle("Notas de débito | ContaCloud") @PermitAll
public class NotasDebitoVentaView extends VerticalLayout implements BeforeEnterObserver {
 private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
 private final NotaDebitoVentaService service;private final NotaDebitoVentaPdfService pdf;private final EmpresaModuloService modulos;private final UltimasFacturasService facturasHistoricas;
 private NotaDebitoVentaDocumento notaPdfActual;
 private final TextField buscar=new TextField("Buscar");private final Select<String> estado=new Select<>();
 private final DatePicker desde=fecha("Desde"),hasta=fecha("Hasta");private final AppPagination pagination;private final AppGrid<Resumen> grid=new AppGrid<>(Resumen.class);
 public NotasDebitoVentaView(NotaDebitoVentaService service,NotaDebitoVentaPdfService pdf,EmpresaModuloService modulos,UltimasFacturasService facturasHistoricas){
  this.service=service;this.pdf=pdf;this.modulos=modulos;this.facturasHistoricas=facturasHistoricas;
  setPadding(false);setSpacing(false);setWidthFull();addClassName("cc-page");
  getElement().addEventListener("debit-pdf-blocked",e->{if(notaPdfActual!=null)informarFalloPdf(notaPdfActual,new ReglaNegocioException("El navegador bloqueó la apertura del PDF."));});
  var nuevo=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nueva nota de débito",e->nueva());nuevo.setVisible(puede("notas_debito.crear"));
  add(new AppPageHeader("NOTAS DE DÉBITO","Administra los cargos adicionales relacionados con facturas de venta.",nuevo));
  buscar.setPlaceholder("Nota, factura o cliente...");buscar.setClearButtonVisible(true);buscar.setValueChangeMode(ValueChangeMode.LAZY);
  estado.setLabel("Estado");estado.setItems("Emitida","Anulada");estado.setEmptySelectionAllowed(true);estado.setEmptySelectionCaption("Todos");
  pagination=new AppPagination(r->cargar(),10,10,20,50);
  buscar.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});estado.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});desde.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});hasta.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
  grid.addColumn(Resumen::numero).setHeader("Número").setAutoWidth(true);grid.addColumn(x->DATE.format(x.fecha())).setHeader("Fecha").setAutoWidth(true);
  grid.addColumn(Resumen::factura).setHeader("Factura").setAutoWidth(true);grid.addColumn(Resumen::cliente).setHeader("Cliente").setFlexGrow(1);
  grid.addColumn(Resumen::moneda).setHeader("Moneda").setAutoWidth(true);grid.addColumn(x->PosNumberFormatter.money(x.total())).setHeader("Total").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
  grid.addColumn(x->estadoNota(x.estado())).setHeader("Estado").setAutoWidth(true);grid.addComponentColumn(this::acciones).setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);grid.setEmptyStateText("No se encontraron notas de débito.");
  var filters=new Div(buscar,estado,desde,hasta);filters.addClassNames("cc-filter-bar","cc-debit-note-filters");add(filters,grid,pagination);
 }
 @Override public void beforeEnter(BeforeEnterEvent e){if(!modulos.habilitado("VENTAS")||!puede("notas_debito.ver")){Notification.show("No tienes permiso para acceder a esta opción.");e.rerouteTo(DashboardView.class);return;}cargar();}
 Component acciones(Resumen r){return new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver nota de débito",e->{try{ver(service.obtener(r.id()));}catch(RuntimeException ex){error(ex);}});}
 private void cargar(){try{var p=pagination.currentRequest();var result=service.buscar(buscar.getValue(),estado.getValue()==null?"":"Anulada".equals(estado.getValue())?"VOIDED":"CONFIRMED",desde.getValue(),hasta.getValue(),p.page(),p.size());grid.setItems(result.getContent());pagination.setTotal(result.getTotalElements());}catch(RuntimeException ex){error(ex);}}
 private void reiniciar(){pagination.reset();cargar();}
 ComboBox<FacturaOpcion> crearSelectorFactura(){var c=new ComboBox<FacturaOpcion>("Factura de origen");c.setWidthFull();c.setPageSize(25);c.setPlaceholder("Buscar por factura, cliente o identificación...");c.setItemLabelGenerator(f->f.numero()+" · "+NotaCreditoVentaService.nombreCliente(f.cliente())+" · "+money(f.moneda(),f.total()));c.setItems(q->service.facturas(q.getFilter().orElse(""),q.getPage(),q.getPageSize()).stream());return c;}
 private void nueva(){
  Dialog d=new Dialog();d.addClassName("cc-debit-note-dialog");d.setHeaderTitle("NUEVA NOTA DE DÉBITO");d.setWidth("min(1180px,96vw)");d.setMaxHeight("92vh");d.setCloseOnEsc(false);d.setCloseOnOutsideClick(false);
  boolean[] dirty={false},detalleValido={false};var factura=crearSelectorFactura();var date=fecha("Fecha");date.setValue(service.fechaActual());date.setRequiredIndicatorVisible(true);
  var motivo=new ComboBox<PreparacionNotaDebitoVentaDto.Opcion>("Motivo");motivo.setItemLabelGenerator(PreparacionNotaDebitoVentaDto.Opcion::nombre);motivo.setRequiredIndicatorVisible(true);motivo.setWidthFull();
  TextArea description=new TextArea("Descripción / justificación");description.setRequiredIndicatorVisible(true);description.setMaxLength(1000);description.setWidthFull();description.setMinHeight("2.5rem");description.setMaxHeight("7rem");description.addClassName("cc-debit-note-description");description.setValueChangeMode(ValueChangeMode.LAZY);
  var origin=new Div();origin.setWidthFull();var totals=new Div();totals.setWidthFull();
  var details=new AppGrid<Cargo>(Cargo.class);List<Cargo> charges=new ArrayList<>();List<PreparacionNotaDebitoVentaDto.Impuesto> taxes=new ArrayList<>();
  var payment=new CobroContadoForm();
  var save=new AppActionButton(ActionType.ISSUE,ButtonSize.MAIN,"Emitir nota de débito",null);save.setEnabled(false);UUID key=UUID.randomUUID();
  java.util.function.Supplier<EmitirNotaDebitoVentaInput> input=()->new EmitirNotaDebitoVentaInput(factura.getValue()==null?null:factura.getValue().id(),date.getValue(),motivo.getValue()==null?null:motivo.getValue().id(),description.getValue(),charges.stream().map(Cargo::input).toList(),key,payment.condicion(),payment.pagos(),payment.cajaId(),payment.sesionCajaId());
  Runnable calculate=()->{dirty[0]=true;detalleValido[0]=false;save.setEnabled(false);totals.removeAll();charges.forEach(x->x.amount.setText("—"));if(factura.isEmpty()||motivo.isEmpty()||description.isEmpty()||charges.isEmpty()||charges.stream().anyMatch(x->!x.completo()))return;try{var preview=service.calcular(input.get());for(int i=0;i<charges.size();i++)charges.get(i).amount.setText(PosNumberFormatter.money(preview.lineas().get(i).total()));totals.add(resumenFormulario(preview));payment.total(preview.total(),preview.moneda());detalleValido[0]=true;save.setEnabled(puede("notas_debito.emitir")&&payment.completo());}catch(RuntimeException ex){error(ex);}};
  payment.onChange(()->{dirty[0]=true;save.setEnabled(detalleValido[0]&&puede("notas_debito.emitir")&&payment.completo());});
  details.addComponentColumn(x->x.description).setHeader("Descripción").setWidth("260px").setFlexGrow(2);details.addComponentColumn(x->x.quantity).setHeader("Cantidad").setWidth("92px").setFlexGrow(0);
  details.addComponentColumn(x->x.price).setHeader("Precio").setWidth("112px").setFlexGrow(0);details.addComponentColumn(x->x.discount).setHeader("Desc.").setWidth("102px").setFlexGrow(0);
  details.addComponentColumn(x->x.tax).setHeader("Impuesto").setWidth("150px").setFlexGrow(0);details.addComponentColumn(x->x.amount).setHeader("Importe").setWidth("112px").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
  details.addComponentColumn(x->new AppActionButton(ActionType.DELETE,ButtonSize.GRID_ACTION,"Eliminar cargo",e->{charges.remove(x);details.setItems(charges);calculate.run();})).setWidth("44px").setFlexGrow(0);
  Runnable addCharge=()->{var c=new Cargo(taxes,calculate);charges.add(c);details.setItems(charges);calculate.run();};
  var add=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Agregar cargo",e->addCharge.run());add.setEnabled(false);
  factura.addValueChangeListener(e->{dirty[0]=true;detalleValido[0]=false;payment.limpiar();save.setEnabled(false);origin.removeAll();totals.removeAll();charges.clear();details.setItems(charges);motivo.clear();taxes.clear();add.setEnabled(false);if(e.getValue()==null)return;try{var p=service.preparar(e.getValue().id());motivo.setItems(p.motivos());taxes.addAll(p.impuestos());origin.add(facturaOrigen(p));payment.preparar(p.clienteIdentificado(),service.catalogosPago(p.facturaId()),p.moneda());add.setEnabled(true);addCharge.run();}catch(RuntimeException ex){error(ex);}});
  motivo.addValueChangeListener(e->{if(e.isFromClient())calculate.run();});date.addValueChangeListener(e->{if(e.isFromClient())calculate.run();});description.addValueChangeListener(e->{if(e.isFromClient())calculate.run();});
  var view=new AppActionButton(ActionType.VIEW,ButtonSize.MAIN,"Ver factura",e->{if(!factura.isEmpty())verFactura(factura.getValue().id());});view.setEnabled(false);factura.addValueChangeListener(e->view.setEnabled(e.getValue()!=null&&puede("facturas.ver")));
  HorizontalLayout selector=new HorizontalLayout(factura,view);selector.setWidthFull();selector.setAlignItems(Alignment.END);selector.setFlexGrow(1,factura);
  var fields=datosNota(date,motivo,payment.campoCondicion());
  save.addClickListener(e->{try{var request=input.get();var preview=service.calcular(request);Dialog confirm=new Dialog();confirm.setHeaderTitle("EMITIR NOTA DE DÉBITO");confirm.add(new Paragraph("Se emitirá una nota de "+money(preview.moneda(),preview.total())+(request.condicion()==com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CASH?" con cobro inmediato.":" a crédito, con saldo pendiente.")+" La factura original permanece sin cambios."));
   var accept=new AppActionButton(ActionType.ISSUE,ButtonSize.MAIN,"Emitir nota de débito",null);accept.setDisableOnClick(true);accept.addClickListener(ev->{try{emitirYMostrar(request,()->{confirm.close();d.close();});}catch(RuntimeException ex){accept.setEnabled(true);error(ex);}});
   confirm.getFooter().add(accept,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",ev->confirm.close()));confirm.open();
  }catch(RuntimeException ex){error(ex);}});
  Div heading=new Div(new H4("DETALLE DE CARGOS"),add);heading.addClassName("cc-debit-section-heading");
  var form=new VerticalLayout(selector,origin,new H4("DATOS DE LA NOTA"),fields,description,heading,details,totals,payment);
  form.setPadding(false);form.setSpacing(false);form.setWidthFull();form.addClassName("cc-debit-note-form");d.add(form);
  d.getFooter().add(save,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->NotasCreditoVentaView.cancelar(d,dirty[0])));d.open();
 }
 static Div datosNota(DatePicker fecha,ComboBox<PreparacionNotaDebitoVentaDto.Opcion> motivo,Select<com.citacloud.springboot.contacloud.app.models.TipoCondicionPago> condicion){
  fecha.setWidthFull();motivo.setWidthFull();condicion.setWidthFull();var fields=new Div(fecha,motivo,condicion);fields.addClassName("cc-debit-note-data");return fields;
 }
 static Div resumenFormulario(NotaDebitoVentaDocumento nota){
  Div totals=new Div();totals.addClassName("cc-debit-note-totals");totals.add(filaTotal("Subtotal",nota.moneda(),nota.subtotal(),false));
  if(nota.descuento().signum()!=0)totals.add(filaTotal("Descuento",nota.moneda(),nota.descuento(),false));
  totals.add(filaTotal("Impuestos",nota.moneda(),nota.impuesto(),false),filaTotal("TOTAL",nota.moneda(),nota.total(),true));return totals;
 }
 private static Div filaTotal(String label,String moneda,BigDecimal amount,boolean total){
  Div row=new Div(new Span(label),new Span(money(moneda,amount)));row.addClassName("cc-debit-total-row");if(total)row.addClassName("cc-debit-total-main");return row;
 }
 static AppDetailSection facturaOrigen(PreparacionNotaDebitoVentaDto p){var s=new AppDetailSection("FACTURA DE ORIGEN").field("Factura",p.factura()).field("Fecha",DATE.format(p.fecha())).field("Cliente",NotaCreditoVentaService.nombreCliente(p.cliente())).field("Moneda",p.moneda()).field("Total factura",money(p.moneda(),p.total())).field("Saldo pendiente",money(p.moneda(),p.saldo())).field("Condición factura",p.condicion()).field("Estado",p.estado());s.addClassName("cc-detail-four");return s;}
 private void verFactura(UUID id){try{var f=facturasHistoricas.detalle(id);var d=new Dialog();d.setHeaderTitle("FACTURA "+f.numero());d.setWidth("min(1100px,96vw)");var section=new AppDetailSection("FACTURA DE ORIGEN").field("Factura",f.numero()).field("Cliente",NotaCreditoVentaService.nombreCliente(f.cliente())).field("Moneda",f.moneda()).field("Total",money(f.moneda(),f.total()));section.addClassName("cc-detail-four");var rows=new AppGrid<FacturaVentaDetalleDto.Linea>(FacturaVentaDetalleDto.Linea.class);rows.addColumn(FacturaVentaDetalleDto.Linea::producto).setHeader("Producto");rows.addColumn(x->PosNumberFormatter.quantity(x.cantidad())).setHeader("Cantidad");rows.addColumn(x->money(f.moneda(),x.precio())).setHeader("Precio");rows.addColumn(x->money(f.moneda(),x.total())).setHeader("Total");rows.setItems(f.lineas());d.add(section,rows);d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();}catch(RuntimeException ex){error(ex);}}
 private void ver(NotaDebitoVentaDocumento n){
  var c=service.ciclo(n.id());var d=new Dialog();d.setHeaderTitle("NOTA DE DÉBITO "+n.numero());d.setWidth("min(1100px,96vw)");d.setMaxHeight("92vh");
  var info=new AppDetailSection("INFORMACIÓN / DOCUMENTO ORIGEN").field("Fecha",DATE.format(n.fecha())).field("Estado",estadoNota(c.estado())).field("Cliente",n.cliente()).field("RNC/Cédula",valor(n.identificacion())).field("Factura",n.factura()).field("Fecha factura",DATE.format(n.fechaFactura())).field("NCF afectado","—").field("NCF de la nota","—").field("Moneda",n.moneda()).field("Condición factura",n.condicionPago()).field("Documento origen",n.factura()).field("Estado factura",n.estadoFactura());info.addClassName("cc-detail-four");
  d.add(new AppDetailSection("CONDICIÓN / SALDO DE LA NOTA").field("Condición de la nota",n.condicionNota()==com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CASH?"Contado":"Crédito").field("Pagado",money(n.moneda(),c.pagado()==null?n.pagado():c.pagado())).field("Saldo",money(n.moneda(),c.saldo()==null?n.saldoNota():c.saldo())),info,new H4("MOTIVO"),new Paragraph(n.motivo()),new H4("DESCRIPCIÓN"),new Paragraph(n.descripcion()));
  if("VOIDED".equals(c.estado()))d.add(new AppDetailSection("ANULACIÓN").field("Motivo",c.motivo()).field("Anulada por",c.anuladaPor()).field("Fecha",c.anuladaEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
  var lines=new AppGrid<NotaDebitoVentaDocumento.Linea>(NotaDebitoVentaDocumento.Linea.class);lines.addColumn(x->PosNumberFormatter.quantity(x.cantidad())).setHeader("Cant.").setAutoWidth(true);lines.addColumn(NotaDebitoVentaDocumento.Linea::descripcion).setHeader("Descripción").setFlexGrow(1);
  lines.addColumn(x->money(n.moneda(),x.precio())).setHeader("Precio").setTextAlign(ColumnTextAlign.END);lines.addColumn(x->money(n.moneda(),x.descuento())).setHeader("Desc.").setTextAlign(ColumnTextAlign.END);lines.addColumn(x->money(n.moneda(),x.impuesto())).setHeader("Impuesto").setTextAlign(ColumnTextAlign.END);lines.addColumn(x->money(n.moneda(),x.total())).setHeader("Importe").setTextAlign(ColumnTextAlign.END);lines.setItems(n.lineas());d.add(new H4("DETALLE"),lines,resumen(n));if(n.condicionNota()==com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CREDIT)d.add(new AppDetailSection("EFECTO FINANCIERO EN LA EMISIÓN").field("Saldo anterior",money(n.moneda(),n.saldoAnterior())).field("Cargo",money(n.moneda(),n.total())).field("Saldo posterior",money(n.moneda(),n.saldoPosterior())));
  if(!n.pagos().isEmpty()){var paid=new AppDetailSection("FORMA DE PAGO");for(var pay:n.pagos())paid.field(pay.metodo(),money(n.moneda(),pay.monto()));paid.field("Recibido",money(n.moneda(),n.recibido())).field("Cambio",money(n.moneda(),n.cambio()));d.add(paid);}
  d.getFooter().add(accionesDetalle(d,n,c).toArray(Component[]::new));d.open();
 }
 List<AppActionButton> accionesDetalle(Dialog d,NotaDebitoVentaDocumento n,NotaDebitoVentaCicloDto c){List<AppActionButton> actions=new ArrayList<>();if(puede("notas_debito.pdf"))actions.add(new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Imprimir nota de débito",e->abrirPdfEmitida(n)));if(puede("notas_debito.anular")&&c.anulable()&&"CONFIRMED".equals(c.estado()))actions.add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular nota de débito",e->anular(d,n,c)));actions.add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));return actions;}
 private void anular(Dialog parent,NotaDebitoVentaDocumento n,NotaDebitoVentaCicloDto state){
  var d=new Dialog();d.setHeaderTitle("ANULAR NOTA DE DÉBITO");d.add(new Paragraph("Está a punto de anular la nota "+n.numero()+". Se revertirá únicamente el efecto financiero de este documento."));
  var reason=new TextArea("Motivo de anulación");reason.setRequiredIndicatorVisible(true);reason.setMinLength(3);reason.setMaxLength(n.condicionNota()==com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CASH?500:1000);reason.setWidthFull();d.add(reason);UUID key=UUID.randomUUID();
  var accept=new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Anular nota de débito",null);accept.setDisableOnClick(true);accept.addClickListener(e->{try{var changed=service.anular(new AnularNotaDebitoVentaInput(n.id(),state.version(),reason.getValue(),key));d.close();parent.close();reiniciar();ver(changed);}catch(RuntimeException ex){accept.setEnabled(true);error(ex);}});d.getFooter().add(accept,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();
 }
 private static Component resumen(NotaDebitoVentaDocumento n){return new AppDetailSection("TOTALES").field("Subtotal",money(n.moneda(),n.subtotal())).field("Descuento",money(n.moneda(),n.descuento())).field("Impuestos",money(n.moneda(),n.impuesto())).field("TOTAL "+n.moneda(),money(n.moneda(),n.total()));}
 // The proxied transactional service has committed before any presentation/PDF work starts.
 NotaDebitoVentaDocumento emitirYMostrar(EmitirNotaDebitoVentaInput request,Runnable cerrarFormularios){
  var issued=service.emitir(request);
  try{cerrarFormularios.run();mostrarEmitida(issued);}
  catch(RuntimeException ex){UiErrorHandler.show(ex,"La nota de débito "+issued.numero()+" fue emitida correctamente, pero no fue posible actualizar su consulta.");}
  if(puede("notas_debito.pdf"))abrirPdfEmitida(issued);
  return issued;
 }
 void mostrarEmitida(NotaDebitoVentaDocumento issued){reiniciar();ver(issued);}
 void abrirPdfEmitida(NotaDebitoVentaDocumento issued){
  try{notaPdfActual=issued;abrirPdf(issued.id());}
  catch(RuntimeException ex){informarFalloPdf(issued,ex);}
 }
 void informarFalloPdf(NotaDebitoVentaDocumento issued,RuntimeException ex){
  String trace=com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate();
  org.slf4j.LoggerFactory.getLogger(NotasDebitoVentaView.class).atWarn().addKeyValue("DebitNoteId",issued.id()).addKeyValue("TraceId",trace).addKeyValue("ExceptionType",ex.getClass().getName()).log("sales_debit_note_pdf_failed");
  Notification.show(mensajeFalloPdf(issued),8000,Notification.Position.BOTTOM_START).addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
 }
 static String mensajeFalloPdf(NotaDebitoVentaDocumento issued){return "La nota de débito "+issued.numero()+" fue emitida correctamente, pero no fue posible abrir el PDF. Puede reintentarlo desde Imprimir.";}
 void abrirPdf(UUID id){
  var file=pdf.generar(id);var resource=new StreamResource(file.nombreArchivo(),()->new ByteArrayInputStream(file.contenido()));resource.setContentType("application/pdf");resource.setCacheTime(0);
  var registered=VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);
  getElement().executeJs("const opened=window.open($0,'_blank'); if(!opened)this.dispatchEvent(new CustomEvent('debit-pdf-blocked'));",registered.getResourceUri().toString());
 }
 private static DatePicker fecha(String label){var d=new DatePicker(label);d.setLocale(Locale.forLanguageTag("es-DO"));d.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));d.setClearButtonVisible(true);return d;}
 private static boolean puede(String code){return TenantContext.principalActual().permisos().contains(code);}
 private static String estadoNota(String x){return "VOIDED".equals(x)?"Anulada":"Emitida";}
 private static String valor(String x){return x==null||x.isBlank()?"—":x;}
 private static String money(String code,BigDecimal value){return code+" "+PosNumberFormatter.money(value);}
 private static void error(RuntimeException e){UiErrorHandler.show(e,"No fue posible procesar la nota de débito.");}
 private static final class Cargo {
  final TextField description=new TextField();final BigDecimalField quantity=numero("Cantidad",BigDecimal.ONE),price=numero("Precio",BigDecimal.ZERO),discount=numero("Descuento",BigDecimal.ZERO);
  final ComboBox<PreparacionNotaDebitoVentaDto.Impuesto> tax=new ComboBox<>();final Span amount=new Span("—");
  Cargo(List<PreparacionNotaDebitoVentaDto.Impuesto> taxes,Runnable calculate){description.setAriaLabel("Descripción del cargo");description.setMaxLength(500);description.setWidthFull();description.setValueChangeMode(ValueChangeMode.LAZY);description.addValueChangeListener(e->{if(e.isFromClient())calculate.run();});tax.setWidthFull();tax.setAriaLabel("Impuesto del cargo");tax.setClearButtonVisible(true);tax.setPlaceholder("Exento");tax.setItems(taxes);tax.setItemLabelGenerator(x->x.nombre()+" · "+x.tasa().stripTrailingZeros().toPlainString()+"%");tax.addValueChangeListener(e->{if(e.isFromClient())calculate.run();});for(var f:List.of(quantity,price,discount))f.addValueChangeListener(e->{if(e.isFromClient())calculate.run();});}
  boolean completo(){return !description.isEmpty()&&quantity.getValue()!=null&&price.getValue()!=null&&discount.getValue()!=null;}
  EmitirNotaDebitoVentaInput.Linea input(){return new EmitirNotaDebitoVentaInput.Linea(description.getValue(),quantity.getValue(),price.getValue(),discount.getValue(),tax.getValue()==null?null:tax.getValue().id());}
  static BigDecimalField numero(String label,BigDecimal value){var f=new BigDecimalField();f.setAriaLabel(label);f.setWidthFull();f.setValue(value);f.getStyle().set("text-align","right");f.addAttachListener(e->{if(f.getValue()!=null){if("Cantidad".equals(label))PosNumberFormatter.showQuantity(f);else PosNumberFormatter.showMoney(f);}});f.addFocusListener(e->{if(f.getValue()!=null)f.getElement().executeJs("if(this.inputElement)this.inputElement.value=$0",f.getValue().toPlainString());});f.addBlurListener(e->{if(f.getValue()!=null){if("Cantidad".equals(label))PosNumberFormatter.showQuantity(f);else PosNumberFormatter.showMoney(f);}});return f;}
 }
}
