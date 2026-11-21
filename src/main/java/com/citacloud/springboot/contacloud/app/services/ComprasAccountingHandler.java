package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.EventoContable.Compra;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.LineaConfigurada;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Reconoce la factura; las recepciones físicas no vuelven a reconocer su valor. */
@Component
public class ComprasAccountingHandler implements IntegracionContableHandler {
 private final CuentaFinancieraContableService recursos;
 public ComprasAccountingHandler(CuentaFinancieraContableService recursos) { this.recursos=recursos; }
 public Modulo modulo() { return Modulo.COMPRAS; }
 public Set<String> eventos() { return Set.of("FACTURA_PROVEEDOR:REGISTRO"); }
 public List<LineaConfigurada> proponer(EventoContable evento) {
  if(!(evento.datos() instanceof Compra c)||c.lineas().isEmpty())
   throw new ReglaNegocioException("El evento de compras no contiene líneas confirmadas.");
  BigDecimal inventario=BigDecimal.ZERO,gasto=BigDecimal.ZERO;
  for(var l:c.lineas()) {
   if(l.neto()==null||l.neto().signum()<0)throw new ReglaNegocioException("Los importes de compra no son válidos.");
   if(l.inventariable())inventario=inventario.add(l.neto());else gasto=gasto.add(l.neto());
  }
  if(c.total()==null||c.impuesto()==null||c.impuesto().signum()<0||
     inventario.add(gasto).add(c.impuesto()).compareTo(c.total())!=0)
   throw new ReglaNegocioException("Los importes confirmados de la compra no coinciden.");
  // El libro actual usa dos decimales. El residuo de redondeo se asigna al último grupo de base,
  // sin alterar el impuesto ni la deuda total; nunca se inventa una línea de ajuste.
  var total=money(c.total());var impuesto=money(c.impuesto());var base=total.subtract(impuesto);
  var inv=gasto.signum()==0?base:money(inventario);var exp=base.subtract(inv);
  if(inv.signum()<0||exp.signum()<0)throw new ReglaNegocioException("No fue posible distribuir el redondeo de la compra.");
  List<LineaConfigurada> lineas=new ArrayList<>();
  if(inv.signum()>0)lineas.add(new LineaConfigurada("INVENTORY_DEFAULT","Inventario adquirido, neto de descuentos",inv,BigDecimal.ZERO));
  if(exp.signum()>0)lineas.add(new LineaConfigurada("PURCHASE_EXPENSE","Gastos de compra, netos de descuentos",exp,BigDecimal.ZERO));
  if(impuesto.signum()>0)lineas.add(new LineaConfigurada("RECOVERABLE_TAX","Impuesto recuperable de compra",impuesto,BigDecimal.ZERO));
  lineas.add(new LineaConfigurada("ACCOUNTS_PAYABLE_DEFAULT","Obligación con el proveedor",BigDecimal.ZERO,total));
  // La liquidación, incluso al contado, pertenece al evento de pago de CxP.
  return List.copyOf(lineas);
 }
 private static BigDecimal money(BigDecimal n) { return n.setScale(2,RoundingMode.HALF_UP); }
}

