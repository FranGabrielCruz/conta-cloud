package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Movimiento;
import java.math.*;
import java.util.*;

/** Conversión de una propuesta cuadrada a moneda base, con residuo en la mayor partida de cada lado. */
final class ConversionContable {
 private ConversionContable() {}
 static List<Movimiento> convertir(List<Movimiento> origen,BigDecimal tasa) {
  if(tasa==null||tasa.signum()<=0)throw new ReglaNegocioException("La tasa contable debe ser mayor que cero.");
  var debitos=lado(origen,tasa,true);var creditos=lado(origen,tasa,false);
  List<Movimiento> resultado=new ArrayList<>();
  for(int i=0;i<origen.size();i++) {
   var l=origen.get(i);
   if(debitos.get(i).signum()>0||creditos.get(i).signum()>0)
    resultado.add(new Movimiento(l.cuenta(),l.descripcion(),debitos.get(i),creditos.get(i),l.centroCostoId()));
  }
  return List.copyOf(resultado);
 }
 private static List<BigDecimal> lado(List<Movimiento> origen,BigDecimal tasa,boolean debito) {
  List<BigDecimal> valores=new ArrayList<>();BigDecimal total=BigDecimal.ZERO,suma=BigDecimal.ZERO,mayor=BigDecimal.ZERO;int ajustar=-1;
  for(int i=0;i<origen.size();i++) {
   BigDecimal importe=debito?origen.get(i).debito():origen.get(i).credito();
   if(importe.compareTo(mayor)>0){mayor=importe;ajustar=i;}
   total=total.add(importe);var convertido=importe.multiply(tasa).setScale(2,RoundingMode.HALF_UP);
   suma=suma.add(convertido);valores.add(convertido);
  }
  if(ajustar>=0) {
   var corregido=valores.get(ajustar).add(total.multiply(tasa).setScale(2,RoundingMode.HALF_UP).subtract(suma));
   if(corregido.signum()<0)throw new ReglaNegocioException("No fue posible distribuir el redondeo de la conversión contable.");
   valores.set(ajustar,corregido);
  }
  return valores;
 }
}

