package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.services.CobroVentaCalculo;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.math.BigDecimal;
import java.util.*;

public class CobroContadoForm extends VerticalLayout {
 private final Select<TipoCondicionPago> condicion=new Select<>();
 private final VerticalLayout rows=new VerticalLayout();
 private final ComboBox<CuentaDineroOpcionDto> caja=new ComboBox<>("Caja");
 private final Span contexto=new Span(),sinSesion=new Span(),resumen=new Span();
 private final List<PagoForm> pagos=new ArrayList<>();
 private CatalogosCobroContadoDto catalogo=new CatalogosCobroContadoDto(List.of(),List.of());
 private List<CuentaDineroOpcionDto> cajasDisponibles=List.of();
 private Runnable changed=()->{};
 private BigDecimal total=BigDecimal.ZERO;
 private String moneda="";
 private boolean preparado;

 public CobroContadoForm(){
  setPadding(false);setSpacing(false);setWidthFull();addClassName("cc-debit-payment-panel");setVisible(false);
  condicion.setLabel("Condición de la nota");condicion.setRequiredIndicatorVisible(true);condicion.setWidthFull();
  condicion.setItems(TipoCondicionPago.values());condicion.setItemLabelGenerator(x->x==TipoCondicionPago.CASH?"Contado":"Crédito");condicion.setValue(TipoCondicionPago.CASH);
  rows.setPadding(false);rows.setSpacing(false);rows.setWidthFull();rows.addClassName("cc-debit-payment-rows");
  caja.setItemLabelGenerator(CuentaDineroOpcionDto::nombre);caja.setRequiredIndicatorVisible(true);caja.addClassName("cc-debit-cash-selector");
  contexto.addClassName("cc-debit-cash-context");sinSesion.addClassName("cc-debit-cash-unavailable");
  sinSesion.setText("No existe una sesión de caja abierta disponible para registrar este cobro.");
  Div context=new Div(contexto,caja);context.addClassName("cc-debit-payment-context");
  Div heading=new Div(new H4("PAGO"),context);heading.addClassName("cc-debit-section-heading");
  var agregar=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Agregar forma de pago",e->{agregar();changed.run();});
  Div actions=new Div(agregar);actions.addClassName("cc-debit-payment-actions");
  resumen.addClassName("cc-debit-payment-summary");add(heading,sinSesion,rows,actions,resumen);
  condicion.addValueChangeListener(e->{actualizar();if(e.isFromClient())changed.run();});
  caja.addValueChangeListener(e->{actualizarContexto();if(e.isFromClient())changed.run();});
 }

 /** The note-data row owns this field; payment controls remain a separate conditional section. */
 public Select<TipoCondicionPago> campoCondicion(){return condicion;}
 public void onChange(Runnable listener){changed=listener;}
 public void limpiar(){
  preparado=false;pagos.clear();rows.removeAll();caja.clear();total=BigDecimal.ZERO;catalogo=new CatalogosCobroContadoDto(List.of(),List.of());cajasDisponibles=List.of();setVisible(false);
 }
 public void preparar(boolean clienteIdentificado,CatalogosCobroContadoDto catalogo,String moneda){
  limpiar();this.catalogo=catalogo;this.moneda=moneda;preparado=true;
  condicion.setReadOnly(!clienteIdentificado);
  condicion.setTooltipText("Define si el importe se cobra al emitir la nota o queda pendiente."+(clienteIdentificado?"":" Crédito requiere un cliente identificado."));
  condicion.setValue(TipoCondicionPago.CASH);
  cajasDisponibles=catalogo.cuentas().stream().filter(c->c.tipo()==TipoCuentaDinero.CASH_REGISTER&&catalogo.sesionesCaja().containsKey(c.id())).toList();
  caja.setItems(cajasDisponibles);if(cajasDisponibles.size()==1)caja.setValue(cajasDisponibles.getFirst());
  agregar();actualizar();
 }
 public TipoCondicionPago condicion(){return condicion.getValue();}
 public UUID cajaId(){return condicion()==TipoCondicionPago.CASH&&pagos.stream().anyMatch(PagoForm::efectivo)&&caja.getValue()!=null?caja.getValue().id():null;}
 public UUID sesionCajaId(){return cajaId()==null?null:catalogo.sesionesCaja().get(cajaId());}
 public List<AplicacionCobroInput> pagos(){return condicion()==TipoCondicionPago.CREDIT?List.of():pagos.stream().map(PagoForm::input).toList();}
 public void total(BigDecimal value,String moneda){
  total=value;this.moneda=moneda;
  if(pagos.size()==1&&!pagos.getFirst().editado){var field=pagos.getFirst().monto;field.setValue(value);PosNumberFormatter.showMoney(field);}
  actualizar();
 }
 public boolean completo(){
  if(condicion()==TipoCondicionPago.CREDIT)return true;
  if(pagos.isEmpty()||pagos.stream().anyMatch(p->p.metodo.isEmpty()||p.monto.getValue()==null||p.monto.getValue().signum()<=0))return false;
  if(pagos.stream().filter(PagoForm::efectivo).count()>1)return false;
  if(pagos.stream().anyMatch(p->p.efectivo()&&(caja.isEmpty()||sesionCajaId()==null||p.recibido.getValue()!=null&&p.recibido.getValue().compareTo(p.monto.getValue())<0)))return false;
  if(pagos.stream().anyMatch(p->!p.efectivo()&&p.cuenta.isEmpty()||p.metodo.getValue().requiereReferencia()&&p.referencia.isEmpty()))return false;
  return CobroVentaCalculo.totalAplicado(pagos.stream().map(p->p.monto.getValue()).toList()).compareTo(total)==0;
 }
 private void agregar(){if(pagos.size()>=20)return;var p=new PagoForm();pagos.add(p);rows.add(p.row);actualizar();}
 private void actualizarContexto(){
  boolean cash=preparado&&condicion()==TipoCondicionPago.CASH&&pagos.stream().anyMatch(PagoForm::efectivo);
  caja.setVisible(cash&&cajasDisponibles.size()>1);
  contexto.setVisible(cash&&cajasDisponibles.size()==1);
  contexto.setText(cajasDisponibles.size()==1?"Caja: "+cajasDisponibles.getFirst().nombre()+" · Turno abierto":"");
  sinSesion.setVisible(cash&&cajasDisponibles.isEmpty());
 }
 private void actualizar(){
  setVisible(preparado&&condicion()==TipoCondicionPago.CASH);actualizarContexto();
  BigDecimal applied=CobroVentaCalculo.totalAplicado(pagos.stream().map(p->p.monto.getValue()).toList());
  BigDecimal received=BigDecimal.ZERO,change=BigDecimal.ZERO;
  for(var p:pagos){
   BigDecimal amount=p.monto.getValue()==null?BigDecimal.ZERO:p.monto.getValue();
   BigDecimal tendered=p.efectivo()&&p.recibido.getValue()!=null?p.recibido.getValue():amount;
   BigDecimal difference=p.efectivo()?CobroVentaCalculo.cambioNoNegativo(amount,tendered):BigDecimal.ZERO;
   received=received.add(tendered);change=change.add(difference);p.cambioValor.setText(moneda+" "+PosNumberFormatter.money(difference));
  }
  String text="Total aplicado: "+moneda+" "+PosNumberFormatter.money(applied);
  if(applied.compareTo(total)<0)text+=" · Pendiente de aplicar: "+moneda+" "+PosNumberFormatter.money(total.subtract(applied));
  else if(pagos.stream().anyMatch(PagoForm::efectivo))text+=" · Recibido: "+moneda+" "+PosNumberFormatter.money(received)+" · Cambio: "+moneda+" "+PosNumberFormatter.money(change);
  resumen.setText(text);
 }
 private class PagoForm {
  final Div row=new Div(),cambio=new Div();
  final ComboBox<MetodoPagoPosDto> metodo=new ComboBox<>("Forma de pago");
  final ComboBox<CuentaDineroOpcionDto> cuenta=new ComboBox<>("Cuenta destino");
  final BigDecimalField monto=numero("Monto aplicado"),recibido=numero("Monto recibido");
  final TextField referencia=new TextField("Referencia");
  final Span cambioValor=new Span();
  boolean editado,recibidoEditado;
  PagoForm(){
   row.addClassName("cc-debit-payment-row");cambio.addClassName("cc-debit-payment-change");
   Span label=new Span("Cambio");label.addClassName("cc-detail-label");cambio.add(label,cambioValor);
   metodo.setWidthFull();metodo.setItems(catalogo.metodos());metodo.setItemLabelGenerator(MetodoPagoPosDto::nombre);metodo.setRequiredIndicatorVisible(true);
   cuenta.setWidthFull();cuenta.setItems(catalogo.cuentas().stream().filter(c->c.tipo()==TipoCuentaDinero.BANK_ACCOUNT).toList());cuenta.setItemLabelGenerator(CuentaDineroOpcionDto::nombre);
   referencia.setWidthFull();referencia.setMaxLength(100);referencia.setValueChangeMode(ValueChangeMode.LAZY);
   var remove=new AppActionButton(ActionType.DELETE,ButtonSize.GRID_ACTION,"Eliminar forma de pago",e->{pagos.remove(this);rows.remove(row);actualizar();changed.run();});
   row.add(metodo,monto,recibido,cambio,cuenta,referencia,remove);
   metodo.addValueChangeListener(e->{visibilidad();actualizar();if(e.isFromClient())changed.run();});
   monto.addValueChangeListener(e->{if(e.isFromClient())editado=true;if(efectivo()&&!recibidoEditado){recibido.setValue(monto.getValue());if(recibido.getValue()!=null)PosNumberFormatter.showMoney(recibido);}actualizar();if(e.isFromClient())changed.run();});
   recibido.addValueChangeListener(e->{if(e.isFromClient())recibidoEditado=true;actualizar();if(e.isFromClient())changed.run();});
   cuenta.addValueChangeListener(e->{if(e.isFromClient())changed.run();});referencia.addValueChangeListener(e->{if(e.isFromClient())changed.run();});
   catalogo.metodos().stream().filter(MetodoPagoPosDto::predeterminado).findFirst().or(()->catalogo.metodos().stream().findFirst()).ifPresent(metodo::setValue);
   var banks=catalogo.cuentas().stream().filter(c->c.tipo()==TipoCuentaDinero.BANK_ACCOUNT).toList();if(banks.size()==1)cuenta.setValue(banks.getFirst());visibilidad();
  }
  boolean efectivo(){return metodo.getValue()!=null&&metodo.getValue().efectivo();}
  void visibilidad(){
   recibido.setVisible(efectivo());cambio.setVisible(efectivo());cuenta.setVisible(!efectivo());
   boolean required=metodo.getValue()!=null&&metodo.getValue().requiereReferencia();referencia.setVisible(!efectivo()||required);referencia.setRequiredIndicatorVisible(required);
   row.getElement().setAttribute("data-cash",String.valueOf(efectivo()));
   referencia.getStyle().set("grid-column",efectivo()&&required?"1 / -1":"auto");
  }
  AplicacionCobroInput input(){return new AplicacionCobroInput(metodo.getValue()==null?null:metodo.getValue().id(),monto.getValue(),efectivo()?recibido.getValue():null,efectivo()||cuenta.getValue()==null?null:cuenta.getValue().id(),referencia.getValue(),null);}
 }
 private static BigDecimalField numero(String label){
  var f=new BigDecimalField(label);f.setWidthFull();f.setValueChangeMode(ValueChangeMode.EAGER);
  f.addAttachListener(e->{if(f.getValue()!=null)PosNumberFormatter.showMoney(f);});
  f.addFocusListener(e->{if(f.getValue()!=null)f.getElement().executeJs("if(this.inputElement)this.inputElement.value=$0",f.getValue().toPlainString());});
  f.addBlurListener(e->PosNumberFormatter.showMoney(f));return f;
 }
}
