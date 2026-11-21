package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.LineaConfigurada;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AccountsPayableAccountingHandler implements IntegracionContableHandler {
 private final CuentaFinancieraContableService recursos;
 public AccountsPayableAccountingHandler(CuentaFinancieraContableService recursos){this.recursos=recursos;}
 public Modulo modulo(){return Modulo.CXP;}
 public Set<String> eventos(){return Set.of("PAGO_PROVEEDOR:CONFIRMACION","APLICACION_PAGO:APLICACION");}
 public List<LineaConfigurada> proponer(EventoContable evento) {
  if(evento.datos() instanceof EventoContable.AplicacionAnticipo a) {
   var n=AccountsReceivableAccountingHandler.importe(a.importe());
   return List.of(new LineaConfigurada("ACCOUNTS_PAYABLE_DEFAULT","Aplicación a obligación del proveedor",n,BigDecimal.ZERO),
    new LineaConfigurada("SUPPLIER_ADVANCES","Aplicación de anticipo al proveedor",BigDecimal.ZERO,n));
  }
  if(!(evento.datos() instanceof EventoContable.PagoProveedor p))throw new ReglaNegocioException("El evento de pago no es válido.");
  if(p.recurso()==null||p.recurso().tipo()==null||p.recurso().id()==null)throw new ReglaNegocioException("Selecciona la caja o cuenta bancaria del pago para generar su asiento automático.");
  var total=AccountsReceivableAccountingHandler.importe(p.recurso().importe());
  var aplicado=AccountsReceivableAccountingHandler.importe(p.aplicado());
  if(total.signum()<=0||aplicado.compareTo(total)>0)throw new ReglaNegocioException("Los importes del pago no son válidos.");
  var cuenta=recursos.paraOperacion(p.recurso().tipo(),p.recurso().id());
  List<LineaConfigurada> l=new ArrayList<>();
  if(aplicado.signum()>0)l.add(new LineaConfigurada("ACCOUNTS_PAYABLE_DEFAULT","Pago aplicado al proveedor",aplicado,BigDecimal.ZERO));
  if(total.compareTo(aplicado)>0)l.add(new LineaConfigurada("SUPPLIER_ADVANCES","Anticipo disponible al proveedor",total.subtract(aplicado),BigDecimal.ZERO));
  l.add(new LineaConfigurada(null,"Salida del recurso financiero",BigDecimal.ZERO,total,null,cuenta));
  return List.copyOf(l);
 }
}

