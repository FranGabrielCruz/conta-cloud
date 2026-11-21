package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegracionesCobrosPagosInventarioTest {
 private final CuentaFinancieraContableService recursos=mock(CuentaFinancieraContableService.class);
 private final UUID banco=UUID.randomUUID(),caja=UUID.randomUUID(),cuentaBanco=UUID.randomUUID(),cuentaCaja=UUID.randomUUID();
 private EventoContable.RecursoImporte banco(String importe){return new EventoContable.RecursoImporte(TipoCuentaDinero.BANK_ACCOUNT,banco,n(importe));}
 private EventoContable.RecursoImporte caja(String importe){return new EventoContable.RecursoImporte(TipoCuentaDinero.CASH_REGISTER,caja,n(importe));}
 @Test void cxcResuelveBancoYCajaSeleccionadosYCobraContraAR() {
  when(recursos.paraOperacion(TipoCuentaDinero.BANK_ACCOUNT,banco)).thenReturn(cuentaBanco);
  when(recursos.paraOperacion(TipoCuentaDinero.CASH_REGISTER,caja)).thenReturn(cuentaCaja);
  var l=new AccountsReceivableAccountingHandler(recursos).proponer(e(Origen.CXC,"RECIBO","CONFIRMACION",
   new EventoContable.Cobro(List.of(banco("60"),caja("40")),n("100"),n("100"))));
  assertThat(l).hasSize(3);assertThat(l.getFirst().cuentaId()).isEqualTo(cuentaBanco);assertThat(l.get(1).cuentaId()).isEqualTo(cuentaCaja);
  assertThat(l.getLast().clave()).isEqualTo("ACCOUNTS_RECEIVABLE_DEFAULT");assertThat(l.getLast().credito()).isEqualByComparingTo("100");cuadra(l);
 }
 @Test void cxcAnticipoYAplicacionPosteriorTienenEfectosDiferentes() {
  when(recursos.paraOperacion(any(),any())).thenReturn(cuentaBanco);var h=new AccountsReceivableAccountingHandler(recursos);
  var cobro=h.proponer(e(Origen.CXC,"RECIBO","CONFIRMACION",new EventoContable.Cobro(List.of(banco("100")),n("100"),n("70"))));
  assertThat(cobro.getLast().clave()).isEqualTo("CUSTOMER_ADVANCES");assertThat(cobro.getLast().credito()).isEqualByComparingTo("30");cuadra(cobro);
  var aplicacion=h.proponer(e(Origen.CXC,"APLICACION_RECIBO","APLICACION",new EventoContable.AplicacionAnticipo(n("30"))));
  assertThat(aplicacion).extracting(LineaConfigurada::clave).containsExactly("CUSTOMER_ADVANCES","ACCOUNTS_RECEIVABLE_DEFAULT");cuadra(aplicacion);
 }
 @Test void cxpPagaContraObligacionYAnticipoSinCuentaGlobalFinanciera() {
  when(recursos.paraOperacion(TipoCuentaDinero.BANK_ACCOUNT,banco)).thenReturn(cuentaBanco);var h=new AccountsPayableAccountingHandler(recursos);
  var pago=h.proponer(e(Origen.CXP,"PAGO_PROVEEDOR","CONFIRMACION",new EventoContable.PagoProveedor(banco("100"),n("80"))));
  assertThat(pago).extracting(LineaConfigurada::clave).containsExactly("ACCOUNTS_PAYABLE_DEFAULT","SUPPLIER_ADVANCES",null);
  assertThat(pago.getLast().cuentaId()).isEqualTo(cuentaBanco);cuadra(pago);
  var aplicacion=h.proponer(e(Origen.CXP,"APLICACION_PAGO","APLICACION",new EventoContable.AplicacionAnticipo(n("20"))));
  assertThat(aplicacion).extracting(LineaConfigurada::clave).containsExactly("ACCOUNTS_PAYABLE_DEFAULT","SUPPLIER_ADVANCES");cuadra(aplicacion);
 }
 @Test void recursoInvalidoFallaSoloCuandoSeUtiliza() {
  when(recursos.paraOperacion(TipoCuentaDinero.BANK_ACCOUNT,banco)).thenThrow(new ReglaNegocioException("Configura la cuenta del recurso seleccionado"));
  var h=new AccountsPayableAccountingHandler(recursos);
  assertThatThrownBy(()->h.proponer(e(Origen.CXP,"PAGO_PROVEEDOR","CONFIRMACION",new EventoContable.PagoProveedor(banco("100"),n("100"))))).hasMessageContaining("recurso seleccionado");
  assertThat(h.proponer(e(Origen.CXP,"APLICACION_PAGO","APLICACION",new EventoContable.AplicacionAnticipo(n("20"))))).hasSize(2);
 }
 @Test void medioSinRecursoAsociadoProduceErrorControladoAlContabilizar() {
  var medio=new EventoContable.RecursoImporte(TipoCuentaDinero.BANK_ACCOUNT,null,n("100"));
  assertThatThrownBy(()->new AccountsReceivableAccountingHandler(recursos).proponer(e(Origen.CXC,"RECIBO","CONFIRMACION",new EventoContable.Cobro(List.of(medio),n("100"),n("100")))))
   .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("caja o cuenta bancaria");
  verifyNoInteractions(recursos);
 }
 @Test void inventarioSoloRegistraEfectosPropiosYSeparaAjusteMixto() {
  var h=new InventoryAccountingHandler();var l=h.proponer(e(Origen.INVENTARIO,"AJUSTE_INVENTARIO","CONFIRMACION",new EventoContable.Inventario(n("100"),n("30"))));
  assertThat(l).extracting(LineaConfigurada::clave).containsExactly("INVENTORY_DEFAULT","INVENTORY_GAIN","INVENTORY_LOSS","INVENTORY_DEFAULT");cuadra(l);
  assertThat(h.eventos()).doesNotContain("FACTURA:EMISION","FACTURA_PROVEEDOR:REGISTRO","PURCHASE_RECEIPT:CONFIRMACION");
  verifyNoInteractions(recursos);
 }
 @Test void costoVentaTieneUnSoloPropietarioYUtilizaCuentasCompartidasSinDuplicarIngreso() {
  var h=new IntegracionesContablesOperativas().ventasAccountingHandler();
  var l=h.proponer(e(Origen.VENTAS,"FACTURA","COSTO",new EventoContable.CostoVenta(n("60"),List.of())));
  assertThat(l).extracting(LineaConfigurada::clave).containsExactly("COGS_DEFAULT","INVENTORY_DEFAULT");cuadra(l);
 }
 private static BigDecimal n(String n){return new BigDecimal(n);}
 private static EventoContable e(Origen origen,String tipo,String evento,EventoContable.Datos datos) {
  return new EventoContable(new Fuente(origen,tipo,UUID.randomUUID(),"TEST-001",evento),1,LocalDate.of(2026,11,21),UUID.randomUUID(),"Prueba","trace",datos);
 }
 private static void cuadra(List<LineaConfigurada> l){assertThat(l.stream().map(x->x.debito().subtract(x.credito())).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0");}
}

