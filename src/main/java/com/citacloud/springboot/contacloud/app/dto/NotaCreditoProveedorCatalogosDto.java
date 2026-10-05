package com.citacloud.springboot.contacloud.app.dto;
import java.util.*;
public record NotaCreditoProveedorCatalogosDto(List<Opcion> proveedores,List<Opcion> monedas,UUID monedaBaseId){
 public record Opcion(UUID id,String nombre){}
}
