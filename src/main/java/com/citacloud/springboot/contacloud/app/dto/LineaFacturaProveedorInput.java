package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineaFacturaProveedorInput(UUID productoId,String descripcion,BigDecimal cantidad,
        BigDecimal precioUnitario,BigDecimal descuento,UUID impuestoId) {}
