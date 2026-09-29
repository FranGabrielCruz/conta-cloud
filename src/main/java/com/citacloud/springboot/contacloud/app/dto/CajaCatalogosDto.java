package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.UUID;

public record CajaCatalogosDto(List<SucursalOpcion> sucursales, List<MonedaOpcion> monedas,
                               UUID monedaBaseId) {
    public record SucursalOpcion(UUID id, String nombre) {}
    public record MonedaOpcion(UUID id, String codigo, String nombre) {}
}

