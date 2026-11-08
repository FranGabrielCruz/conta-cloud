package com.citacloud.springboot.contacloud.app.views.components;
import com.citacloud.springboot.contacloud.app.dto.*;import com.citacloud.springboot.contacloud.app.models.*;import com.vaadin.flow.component.*;import com.vaadin.flow.component.select.Select;import java.util.*;import java.util.stream.Stream;import java.math.BigDecimal;import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class CobroContadoFormTest {
 Stream<Component> tree(Component c){return Stream.concat(Stream.of(c),c.getChildren().flatMap(this::tree));}
 Select<TipoCondicionPago> select(CobroContadoForm f){return f.campoCondicion();}
 @Test void consumidorFinalIniciaContadoYNoPermiteCambiarACredito(){var f=new CobroContadoForm();f.preparar(false,new CatalogosCobroContadoDto(List.of(),List.of()),"DOP");assertThat(f.condicion()).isEqualTo(TipoCondicionPago.CASH);assertThat(select(f).isReadOnly()).isTrue();assertThat(f.completo()).isFalse();}
 @Test void clientePuedeElegirCreditoSinEnviarPagosNiCaja(){var f=new CobroContadoForm();f.preparar(true,new CatalogosCobroContadoDto(List.of(),List.of()),"USD");assertThat(select(f).isReadOnly()).isFalse();select(f).setValue(TipoCondicionPago.CREDIT);assertThat(f.pagos()).isEmpty();assertThat(f.cajaId()).isNull();assertThat(f.completo()).isTrue();assertThat(f.isVisible()).isFalse();}
 @Test void contadoCompletoUsaCatalogoDinamicoYCajaDeLaMoneda(){
  UUID method=UUID.randomUUID(),cash=UUID.randomUUID(),currency=UUID.randomUUID();var f=new CobroContadoForm();
  var m=new MetodoPagoPosDto(method,"EF","Caja efectivo",TipoMetodoPagoVenta.CASH,false,false,true,true,false,null,true);
  f.preparar(false,new CatalogosCobroContadoDto(List.of(m),List.of(new CuentaDineroOpcionDto(cash,TipoCuentaDinero.CASH_REGISTER,"Caja prueba",currency,"DOP")),Map.of(cash,UUID.randomUUID())),"DOP");f.total(new BigDecimal("100"),"DOP");
  assertThat(f.completo()).isTrue();assertThat(f.cajaId()).isEqualTo(cash);assertThat(f.pagos()).hasSize(1);assertThat(f.pagos().getFirst().metodoPagoId()).isEqualTo(method);assertThat(f.pagos().getFirst().monto()).isEqualByComparingTo("100");
 }

 private MetodoPagoPosDto efectivo(){return new MetodoPagoPosDto(UUID.randomUUID(),"EF","Efectivo configurado",TipoMetodoPagoVenta.CASH,false,false,true,true,false,null,true);}
 private CuentaDineroOpcionDto caja(UUID id){return new CuentaDineroOpcionDto(id,TipoCuentaDinero.CASH_REGISTER,"Caja autorizada",UUID.randomUUID(),"USD");}
 private com.vaadin.flow.component.combobox.ComboBox<?> cajaCampo(CobroContadoForm f){return (com.vaadin.flow.component.combobox.ComboBox<?>)tree(f).filter(com.vaadin.flow.component.combobox.ComboBox.class::isInstance).map(com.vaadin.flow.component.combobox.ComboBox.class::cast).filter(c->"Caja".equals(c.getLabel())).findFirst().orElseThrow();}
 @Test void cajaUnicaEsContextoYNoSelectorEditable(){
  UUID cash=UUID.randomUUID(),session=UUID.randomUUID();var f=new CobroContadoForm();f.preparar(true,new CatalogosCobroContadoDto(List.of(efectivo()),List.of(caja(cash)),Map.of(cash,session)),"USD");f.total(new BigDecimal("100"),"USD");
  assertThat(cajaCampo(f).isVisible()).isFalse();assertThat(f.sesionCajaId()).isEqualTo(session);assertThat(tree(f).filter(c->c.hasClassName("cc-debit-cash-context")).map(c->c.getElement().getText())).anyMatch(t->t.contains("Turno abierto"));assertThat(f.completo()).isTrue();
 }
 @Test void variasCajasRequierenElegirYNoSeSeleccionaUnaArbitrariamente(){
  UUID one=UUID.randomUUID(),two=UUID.randomUUID();var first=caja(one);var second=caja(two);var f=new CobroContadoForm();f.preparar(true,new CatalogosCobroContadoDto(List.of(efectivo()),List.of(first,second),Map.of(one,UUID.randomUUID(),two,UUID.randomUUID())),"USD");f.total(new BigDecimal("100"),"USD");
  assertThat(cajaCampo(f).isVisible()).isTrue();assertThat(f.cajaId()).isNull();assertThat(f.completo()).isFalse();
  @SuppressWarnings("unchecked") var select=(com.vaadin.flow.component.combobox.ComboBox<CuentaDineroOpcionDto>)cajaCampo(f);select.setValue(second);assertThat(f.cajaId()).isEqualTo(two);assertThat(f.completo()).isTrue();
 }
 @Test void cajaSinSesionBloqueaEfectivoPeroNoCredito(){
  var f=new CobroContadoForm();f.preparar(true,new CatalogosCobroContadoDto(List.of(efectivo()),List.of(caja(UUID.randomUUID()))),"USD");f.total(new BigDecimal("100"),"USD");
  assertThat(f.completo()).isFalse();assertThat(cajaCampo(f).isVisible()).isFalse();assertThat(tree(f).filter(c->c.hasClassName("cc-debit-cash-unavailable")&&c.isVisible()).map(c->c.getElement().getText())).contains("No existe una sesión de caja abierta disponible para registrar este cobro.");
  select(f).setValue(TipoCondicionPago.CREDIT);assertThat(f.isVisible()).isFalse();assertThat(f.pagos()).isEmpty();assertThat(f.sesionCajaId()).isNull();assertThat(f.completo()).isTrue();
 }
 @Test void resumenActualizaRecibidoCambioYFormatoSinReducirPrecision(){
  UUID id=UUID.randomUUID();var f=new CobroContadoForm();f.preparar(true,new CatalogosCobroContadoDto(List.of(efectivo()),List.of(caja(id)),Map.of(id,UUID.randomUUID())),"USD");f.total(new BigDecimal("1000.1234"),"USD");
  var received=tree(f).filter(com.vaadin.flow.component.textfield.BigDecimalField.class::isInstance).map(com.vaadin.flow.component.textfield.BigDecimalField.class::cast).filter(x->"Monto recibido".equals(x.getLabel())).findFirst().orElseThrow();received.setValue(new BigDecimal("1500.1234"));
  assertThat(f.pagos().getFirst().monto()).isEqualByComparingTo("1000.1234");assertThat(tree(f).filter(c->c.hasClassName("cc-debit-payment-summary")).map(c->c.getElement().getText())).anyMatch(t->t.contains("USD 1,000.12")&&t.contains("Recibido: USD 1,500.12")&&t.contains("Cambio: USD 500.00"));
 }
 @Test void tarjetaNoMuestraCajaRecibidoNiCambio(){
  var card=new MetodoPagoPosDto(UUID.randomUUID(),"TC","Tarjeta",TipoMetodoPagoVenta.CREDIT_CARD,false,true,false,false,true,null,true);var account=new CuentaDineroOpcionDto(UUID.randomUUID(),TipoCuentaDinero.BANK_ACCOUNT,"Banco",UUID.randomUUID(),"USD");var f=new CobroContadoForm();f.preparar(true,new CatalogosCobroContadoDto(List.of(card),List.of(account)),"USD");f.total(new BigDecimal("100"),"USD");
  assertThat(cajaCampo(f).isVisible()).isFalse();assertThat(f.cajaId()).isNull();assertThat(f.completo()).isTrue();assertThat(tree(f).filter(c->c.hasClassName("cc-debit-payment-change"))).allMatch(c->!c.isVisible());assertThat(tree(f).filter(com.vaadin.flow.component.textfield.BigDecimalField.class::isInstance).map(com.vaadin.flow.component.textfield.BigDecimalField.class::cast).filter(c->"Monto recibido".equals(c.getLabel()))).allMatch(c->!c.isVisible());assertThat(tree(f).filter(c->c.hasClassName("cc-debit-payment-summary")).map(c->c.getElement().getText())).noneMatch(t->t.contains("Cambio"));
 }
}
