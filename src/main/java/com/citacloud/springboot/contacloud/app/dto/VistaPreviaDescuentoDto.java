package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.*;
public record VistaPreviaDescuentoDto(BigDecimal baseElegible,BigDecimal montoDescuento,BigDecimal importePosterior,BigDecimal porcentajeEfectivo,boolean requiereAutorizacion,BigDecimal porcentajeMaximoUsuario,BigDecimal montoMaximoUsuario,String motivoNombre,boolean motivoOtro,List<Asignacion> asignaciones){
 public record Asignacion(UUID lineaId,UUID productoId,BigDecimal subtotalBruto,BigDecimal descuento,BigDecimal baseImponible,BigDecimal impuesto,BigDecimal total){
  public Asignacion(UUID productoId,BigDecimal subtotalBruto,BigDecimal descuento,BigDecimal baseImponible,BigDecimal impuesto,BigDecimal total){this(productoId,productoId,subtotalBruto,descuento,baseImponible,impuesto,total);}
 }
}
