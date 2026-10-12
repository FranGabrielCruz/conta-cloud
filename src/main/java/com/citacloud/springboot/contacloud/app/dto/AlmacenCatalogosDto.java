package com.citacloud.springboot.contacloud.app.dto;
import java.util.List;
import java.util.UUID;
public record AlmacenCatalogosDto(List<Opcion> sucursales,List<Opcion> responsables) {
    public record Opcion(UUID id,String nombre) { @Override public String toString(){return nombre;} }
}
