package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ExistenciaInventarioDetalleDto(UUID productoId, String producto, String codigo,
        String unidad, BigDecimal total, List<Distribucion> distribucion) {
    public record Distribucion(UUID almacenId, String sucursal, String almacen, BigDecimal existencia) {}
}
