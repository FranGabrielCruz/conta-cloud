package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.UUID;

public record ExistenciaInventarioCatalogosDto(List<Opcion> sucursales, List<AlmacenOpcion> almacenes,
        List<Opcion> categorias) {
    public record Opcion(UUID id, String nombre) {@Override public String toString(){return nombre;}}
    public record AlmacenOpcion(UUID id, UUID sucursalId, String nombre) {@Override public String toString(){return nombre;}}
}
