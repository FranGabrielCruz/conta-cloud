package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.*;

public record CotizacionCatalogosDto(List<SucursalOpcion> sucursales,List<MonedaOpcion> monedas,
        List<ImpuestoOpcion> impuestos,UUID monedaBaseId,int diasVigenciaPredeterminados) {
    public record ClienteOpcion(UUID id,String codigo,String nombre,String identificacion,UUID monedaId) {}
    public record SucursalOpcion(UUID id,String nombre) {}
    public record MonedaOpcion(UUID id,String codigo,String nombre,String simbolo) {}
    public record ImpuestoOpcion(UUID id,String nombre,BigDecimal tasa) {}
    public record ProductoOpcion(UUID id,String codigo,String nombre,String descripcion,UUID unidadId,String unidad,
            String abreviatura,BigDecimal precioVenta,UUID monedaId,UUID impuestoVentaId,String impuestoVentaNombre,
            BigDecimal impuestoVentaTasa) {}
}
