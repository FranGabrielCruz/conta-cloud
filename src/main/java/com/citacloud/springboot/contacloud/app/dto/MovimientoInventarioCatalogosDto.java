package com.citacloud.springboot.contacloud.app.dto;
import java.util.List;import java.util.UUID;
public record MovimientoInventarioCatalogosDto(List<Opcion> sucursales,List<AlmacenOpcion> almacenes,List<ProductoOpcion> productos){
 public record Opcion(UUID id,String nombre){@Override public String toString(){return nombre;}}
 public record AlmacenOpcion(UUID id,UUID sucursalId,String nombre){@Override public String toString(){return nombre;}}
 public record ProductoOpcion(UUID id,String codigo,String nombre,String unidad){@Override public String toString(){return codigo+" · "+nombre;}}
}
