package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.LineaConfigurada;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AccountsReceivableAccountingHandler implements IntegracionContableHandler {
 private final CuentaFinancieraContableService recursos;
 public AccountsReceivableAccountingHandler(CuentaFinancieraContableService recursos){this.recursos=recursos;}
 public Modulo modulo(){return Modulo.CXC;}
 public Set<String> eventos(){return Set.of("RECIBO:CONFIRMACION","APLICACION_RECIBO:APLICACION");}
 public List<LineaConfigurada> proponer(EventoContable evento) {
  if(evento.datos() instanceof EventoContable.AplicacionAnticipo a) {
   var n=importe(a.importe());
   return List.of(new LineaConfigurada("CUSTOMER_ADVANCES","Aplicación de anticipo de cliente",n,BigDecimal.ZERO),
    new LineaConfigurada("ACCOUNTS_RECEIVABLE_DEFAULT","Aplicación a cuenta por cobrar",BigDecimal.ZERO,n));
  }
  if(!(evento.datos() instanceof EventoContable.Cobro c))throw new ReglaNegocioException("El evento de cobro no es válido.");
  var total=importe(c.recibido());var aplicado=importe(c.aplicado());
  if(aplicado.signum()<0||aplicado.compareTo(total)>0||c.recursos().isEmpty())throw new ReglaNegocioException("Los importes del cobro no son válidos.");
  var suma=c.recursos().stream().map(EventoContable.RecursoImporte::importe).map(AccountsReceivableAccountingHandler::importe).reduce(BigDecimal.ZERO,BigDecimal::add);
  if(suma.compareTo(total)!=0)throw new ReglaNegocioException("Los recursos del cobro no coinciden con el importe recibido.");
  List<LineaConfigurada> l=new ArrayList<>();
  for(var recurso:c.recursos()) {
   if(recurso.tipo()==null||recurso.id()==null)throw new ReglaNegocioException("Selecciona la caja o cuenta bancaria del medio de cobro para generar su asiento automático.");
   var n=importe(recurso.importe());if(n.signum()<=0)throw new ReglaNegocioException("El importe del recurso debe ser mayor que cero.");
   l.add(new LineaConfigurada(null,"Entrada al recurso financiero",n,BigDecimal.ZERO,null,recursos.paraOperacion(recurso.tipo(),recurso.id())));
  }
  if(aplicado.signum()>0)l.add(new LineaConfigurada("ACCOUNTS_RECEIVABLE_DEFAULT","Cobro aplicado al cliente",BigDecimal.ZERO,aplicado));
  if(total.compareTo(aplicado)>0)l.add(new LineaConfigurada("CUSTOMER_ADVANCES","Anticipo disponible del cliente",BigDecimal.ZERO,total.subtract(aplicado)));
  return List.copyOf(l);
 }
 static BigDecimal importe(BigDecimal n) {
  if(n==null||n.signum()<0)throw new ReglaNegocioException("El importe contable no es válido.");
  return n.setScale(2,RoundingMode.HALF_UP);
 }
}

