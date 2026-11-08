package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoCondicionPago;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
public record EmitirNotaDebitoVentaInput(UUID facturaId,LocalDate fecha,UUID motivoId,String descripcion,List<Linea> lineas,UUID idempotencia,
 TipoCondicionPago condicion,List<AplicacionCobroInput> pagos,UUID cajaId,UUID sesionCajaId){
 /** Compatibility for existing internal credit-note callers; new UI always supplies the condition. */
 public EmitirNotaDebitoVentaInput(UUID facturaId,LocalDate fecha,UUID motivoId,String descripcion,List<Linea> lineas,UUID idempotencia){this(facturaId,fecha,motivoId,descripcion,lineas,idempotencia,TipoCondicionPago.CREDIT,List.of(),null,null);}
 public record Linea(String descripcion,BigDecimal cantidad,BigDecimal precio,BigDecimal descuento,UUID impuestoId){}
}
