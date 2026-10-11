package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.UUID;

public record CuentasPagarCatalogosDto(List<Opcion> proveedores,List<Opcion> monedas,
        UUID monedaBaseId,List<Opcion> sucursales) {
    public record Opcion(UUID id,String nombre) {}
}
