package com.citacloud.springboot.contacloud.app.services;
import java.util.*;
final class IntegracionesContablesTestSupport {
 static RegistroIntegracionesContables registro(CuentaFinancieraContableService recursos) {
  var c=new IntegracionesContablesOperativas();
  return new RegistroIntegracionesContables(List.of(c.ventasAccountingHandler(),c.activosAccountingHandler(),
   c.finanzasAccountingHandler(recursos),new ComprasAccountingHandler(recursos),new AccountsReceivableAccountingHandler(recursos),new AccountsPayableAccountingHandler(recursos),new InventoryAccountingHandler()));
 }
}

