package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComprasAccountingHandlerTest {
 private final CuentaFinancieraContableService recursos=mock(CuentaFinancieraContableService.class);
 private final ComprasAccountingHandler handler=new ComprasAccountingHandler(recursos);
 @Test void compraMixtaSeparaInventarioGastoImpuestoYDescuentosNetos() {
  var l=handler.proponer(evento(List.of(r(true,"80"),r(false,"20")),"18","118",null,null));
  assertThat(l).extracting(LineaConfigurada::clave).containsExactly("INVENTORY_DEFAULT","PURCHASE_EXPENSE","RECOVERABLE_TAX","ACCOUNTS_PAYABLE_DEFAULT");
  assertThat(l.get(0).debito()).isEqualByComparingTo("80");
  assertThat(l.get(1).debito()).isEqualByComparingTo("20");cuadra(l);
  verifyNoInteractions(recursos);
 }
 @Test void gastoSinImpuestosNoNecesitaMapeosOpcionales() {
  var l=handler.proponer(evento(List.of(r(false,"100")),"0","100",null,null));
  assertThat(l).extracting(LineaConfigurada::clave).containsExactly("PURCHASE_EXPENSE","ACCOUNTS_PAYABLE_DEFAULT");cuadra(l);
 }
 @Test void inventarioNuncaSeRegistraComoCostoDeVentaNiGasto() {
  var l=handler.proponer(evento(List.of(r(true,"100")),"0","100",null,null));
  assertThat(l).extracting(LineaConfigurada::clave).containsExactly("INVENTORY_DEFAULT","ACCOUNTS_PAYABLE_DEFAULT");cuadra(l);
 }
 @Test void compraAlContadoDejaLaLiquidacionAlEventoDePagoCxP() {
  var caja=UUID.randomUUID();
  var l=handler.proponer(evento(List.of(r(false,"100")),"18","118",TipoCuentaDinero.CASH_REGISTER,caja));
  assertThat(l.getLast().clave()).isEqualTo("ACCOUNTS_PAYABLE_DEFAULT");
  assertThat(l.getLast().credito()).isEqualByComparingTo("118");verifyNoInteractions(recursos);cuadra(l);
 }
 @Test void redondeoAgrupadoConservaImpuestoYTotalConfirmado() {
  var l=handler.proponer(evento(List.of(r(true,"33.335"),r(false,"66.665")),"18.0000","118.0000",null,null));
  assertThat(l.getFirst().debito()).isEqualByComparingTo("33.34");
  assertThat(l.get(1).debito()).isEqualByComparingTo("66.66");cuadra(l);
 }
 @Test void rechazaTotalesInconsistentesYLineasNegativas() {
  assertThatThrownBy(()->handler.proponer(evento(List.of(r(true,"100")),"18","117",null,null))).hasMessageContaining("no coinciden");
  assertThatThrownBy(()->handler.proponer(evento(List.of(r(false,"-1")),"0","-1",null,null))).hasMessageContaining("no son válidos");
 }
 static EventoContable.RenglonCompra r(boolean inventariable,String neto) {return new EventoContable.RenglonCompra(inventariable,new BigDecimal(neto));}
 static EventoContable evento(List<EventoContable.RenglonCompra> lineas,String tax,String total,TipoCuentaDinero tipo,UUID recurso) {
  return new EventoContable(new Fuente(Origen.COMPRAS,"FACTURA_PROVEEDOR",UUID.randomUUID(),"FP-001","REGISTRO"),1,
   LocalDate.of(2026,11,21),UUID.randomUUID(),"Compra","trace-compra",
   new EventoContable.Compra(lineas,new BigDecimal(tax),new BigDecimal(total),tipo,recurso));
 }
 private static void cuadra(List<LineaConfigurada> l) {
  assertThat(l.stream().map(x->x.debito().subtract(x.credito())).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0");
 }
}

