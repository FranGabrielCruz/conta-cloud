package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ExistenciaInventarioDto(UUID productoId, UUID almacenId, UUID sucursalId,
        String producto, String codigo, String unidad, String categoria, String sucursal,
        String almacen, boolean productoActivo, boolean almacenActivo, BigDecimal existencia) {}
