package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.*;
public record TransferenciaInventarioCatalogosDto(List<Sucursal> sucursales,List<Almacen> almacenes,List<Producto> productos){
 public record Sucursal(UUID id,String nombre){}
 public record Almacen(UUID id,UUID sucursalId,String nombre,String sucursal){}
 public record Producto(UUID id,String codigo,String nombre,UUID unidadId,String unidad,BigDecimal existencia,boolean permiteNegativa){}
}
