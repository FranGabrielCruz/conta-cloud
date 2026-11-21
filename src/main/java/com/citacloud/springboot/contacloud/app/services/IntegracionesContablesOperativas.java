package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.LineaConfigurada;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import java.math.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.context.annotation.*;

/** Handlers sin acceso al libro: únicamente proponen movimientos. */
@Configuration
public class IntegracionesContablesOperativas {
 private record Handler(Modulo modulo,Set<String> eventos,Function<EventoContable,List<LineaConfigurada>> propuesta)
   implements IntegracionContableHandler {
  public List<LineaConfigurada> proponer(EventoContable evento) { return propuesta.apply(evento); }
 }
 @Bean IntegracionContableHandler ventasAccountingHandler() {
  return new Handler(Modulo.VENTAS,Set.of("FACTURA:EMISION","FACTURA:COSTO","DEVOLUCION:COSTO"),e->{
   if(e.datos() instanceof EventoContable.CostoVenta c) {
    var importe=money(c.importe());boolean devolver=e.fuente().tipo().equals("DEVOLUCION");
    return List.of(new LineaConfigurada("COGS_DEFAULT","Costo de mercancías",devolver?BigDecimal.ZERO:importe,devolver?importe:BigDecimal.ZERO),
     new LineaConfigurada("INVENTORY_DEFAULT","Inventario de mercancías",devolver?importe:BigDecimal.ZERO,devolver?BigDecimal.ZERO:importe));
   }
   var v=(EventoContable.Venta)e.datos();var total=money(v.total());var impuesto=money(v.impuesto());
   List<LineaConfigurada> l=new ArrayList<>();
   l.add(new LineaConfigurada("ACCOUNTS_RECEIVABLE_DEFAULT","Factura "+e.fuente().numero(),total,BigDecimal.ZERO));
   l.add(new LineaConfigurada("SALES_REVENUE_DEFAULT","Ingreso neto",BigDecimal.ZERO,total.subtract(impuesto)));
   if(impuesto.signum()>0)l.add(new LineaConfigurada("SALES_TAX_PAYABLE","Impuestos de venta",BigDecimal.ZERO,impuesto));
   return l;
  });
 }
 @Bean IntegracionContableHandler activosAccountingHandler() {
  return new Handler(Modulo.ACTIVOS_FIJOS,Set.of("DEPRECIACION:PROCESAMIENTO"),e->{
   var importe=money(((EventoContable.Depreciacion)e.datos()).importe());
   return List.of(new LineaConfigurada("DEPRECIATION_EXPENSE_DEFAULT","Gasto por depreciación",importe,BigDecimal.ZERO),
    new LineaConfigurada("ACCUMULATED_DEPRECIATION","Depreciación acumulada",BigDecimal.ZERO,importe));
  });
 }
 @Bean IntegracionContableHandler finanzasAccountingHandler(CuentaFinancieraContableService recursos) {
  return new Handler(Modulo.FINANZAS,Set.of("TRANSFERENCIA:REGISTRO"),e->{
   var t=(EventoContable.Transferencia)e.datos();
   if(!e.moneda().equals(t.monedaDestino()))throw new ReglaNegocioException("La transferencia automática entre monedas distintas requiere una conversión contable soportada.");
   var origen=recursos.paraOperacion(t.tipoOrigen(),t.origen());var destino=recursos.paraOperacion(t.tipoDestino(),t.destino());var importe=money(t.importe());
   return List.of(new LineaConfigurada(null,"Recurso financiero destino",importe,BigDecimal.ZERO,null,destino),
    new LineaConfigurada(null,"Recurso financiero origen",BigDecimal.ZERO,importe,null,origen));
  });
 }
 private static BigDecimal money(BigDecimal n) { return n.setScale(2,RoundingMode.HALF_UP); }
}

