package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductoCatalogosDto(List<CategoriaOpcion> categorias,List<UnidadOpcion> unidades,
    List<MonedaOpcion> monedas,List<ImpuestoOpcion> impuestos,UUID monedaBaseId) {
    public record CategoriaOpcion(UUID id,String nombre) {}
    public record UnidadOpcion(UUID id,String nombre,String abreviatura) {}
    public record MonedaOpcion(UUID id,String codigo,String nombre) {}
    public record ImpuestoOpcion(UUID id,String nombre,BigDecimal tasa) {}
}
