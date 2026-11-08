package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
public record PreparacionNotaDebitoVentaDto(UUID facturaId,String factura,LocalDate fecha,String cliente,String moneda,BigDecimal total,BigDecimal saldo,String condicion,String estado,List<Opcion> motivos,List<Impuesto> impuestos,boolean clienteIdentificado,UUID sucursalId,UUID monedaId){
 public PreparacionNotaDebitoVentaDto(UUID facturaId,String factura,LocalDate fecha,String cliente,String moneda,BigDecimal total,BigDecimal saldo,String condicion,String estado,List<Opcion> motivos,List<Impuesto> impuestos){this(facturaId,factura,fecha,cliente,moneda,total,saldo,condicion,estado,motivos,impuestos,true,null,null);}
 public record Opcion(UUID id,String nombre){}
 public record Impuesto(UUID id,String nombre,BigDecimal tasa){}
}
