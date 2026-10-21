package com.citacloud.springboot.contacloud.app.dto;import java.math.BigDecimal;import java.util.*;
public record VentaGuardadaRecuperadaDto(UUID id,String numero,String referencia,String observacion,ClientePosDto cliente,String moneda,BigDecimal descuento,BigDecimal recargo,DescuentoVentaInput detalleDescuento,RecargoVentaInput detalleRecargo,List<Producto> productos){
 public VentaGuardadaRecuperadaDto(UUID id,String numero,String referencia,String observacion,ClientePosDto cliente,String moneda,BigDecimal descuento,BigDecimal recargo,List<Producto> productos){this(id,numero,referencia,observacion,cliente,moneda,descuento,recargo,null,null,productos);}
 public record Producto(UUID lineaId,UUID id,String codigo,String nombre,String unidad,BigDecimal precio,BigDecimal tasaImpuesto,BigDecimal existencia,boolean controlaExistencia,boolean permiteNegativo,boolean permiteDescuentos,boolean permiteRecargos,BigDecimal cantidad){
  public Producto(UUID id,String codigo,String nombre,String unidad,BigDecimal precio,BigDecimal tasaImpuesto,BigDecimal existencia,boolean controlaExistencia,boolean permiteNegativo,BigDecimal cantidad){this(id,id,codigo,nombre,unidad,precio,tasaImpuesto,existencia,controlaExistencia,permiteNegativo,true,true,cantidad);}
 }
}
