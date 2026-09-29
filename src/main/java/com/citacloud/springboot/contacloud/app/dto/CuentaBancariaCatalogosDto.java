package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.UUID;

public record CuentaBancariaCatalogosDto(List<MonedaOpcion> monedas, UUID monedaBaseId) {
    public record MonedaOpcion(UUID id, String codigo, String nombre) {}
}
