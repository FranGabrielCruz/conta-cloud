package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.UUID;

public record ComprasCatalogosDto(List<Opcion> proveedores,List<Opcion> sucursales,List<Opcion> monedas,
        List<Opcion> condicionesPago,List<Opcion> almacenes,List<Opcion> ordenes,UUID monedaBaseId) {
    public record Opcion(UUID id,String nombre) {}
}
