package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrdenCompraCatalogosDto(List<ProveedorOpcion> proveedores,List<SucursalOpcion> sucursales,
        List<MonedaOpcion> monedas,List<CondicionOpcion> condiciones,List<ImpuestoOpcion> impuestos,UUID monedaBaseId) {
    public record ProveedorOpcion(UUID id,String nombre,String identificacion,UUID condicionPagoId,UUID monedaId) {}
    public record SucursalOpcion(UUID id,String nombre) {}
    public record MonedaOpcion(UUID id,String codigo,String nombre) {}
    public record CondicionOpcion(UUID id,String nombre) {}
    public record ImpuestoOpcion(UUID id,String nombre,BigDecimal tasa) {}
}
