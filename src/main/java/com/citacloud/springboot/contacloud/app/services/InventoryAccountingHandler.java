package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.LineaConfigurada;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

/** Solamente ajustes y diferencias propias. No admite facturas, recepciones ni ventas. */
@Component
public class InventoryAccountingHandler implements IntegracionContableHandler {
 public Modulo modulo(){return Modulo.INVENTARIO;}
 public Set<String> eventos(){return Set.of("AJUSTE_INVENTARIO:CONFIRMACION","CONTEO_FISICO:CONFIRMACION");}
 public List<LineaConfigurada> proponer(EventoContable evento) {
  if(!(evento.datos() instanceof EventoContable.Inventario i))throw new ReglaNegocioException("El evento de inventario no es válido.");
  var entrada=AccountsReceivableAccountingHandler.importe(i.entrada());var salida=AccountsReceivableAccountingHandler.importe(i.salida());
  List<LineaConfigurada> l=new ArrayList<>();
  if(entrada.signum()>0) {
   l.add(new LineaConfigurada("INVENTORY_DEFAULT","Incremento propio del inventario",entrada,BigDecimal.ZERO));
   l.add(new LineaConfigurada("INVENTORY_GAIN","Contrapartida de ajuste positivo",BigDecimal.ZERO,entrada));
  }
  if(salida.signum()>0) {
   l.add(new LineaConfigurada("INVENTORY_LOSS","Contrapartida de ajuste negativo",salida,BigDecimal.ZERO));
   l.add(new LineaConfigurada("INVENTORY_DEFAULT","Disminución propia del inventario",BigDecimal.ZERO,salida));
  }
  if(l.isEmpty())throw new ReglaNegocioException("El evento no tiene un efecto contable de inventario.");
  return List.copyOf(l);
 }
}

