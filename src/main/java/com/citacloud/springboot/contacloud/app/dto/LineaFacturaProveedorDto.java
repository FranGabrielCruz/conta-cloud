package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineaFacturaProveedorDto(UUID productoId,String codigo,String descripcion,String unidad,
        BigDecimal cantidad,BigDecimal precioUnitario,BigDecimal descuento,UUID impuestoId,
        String impuestoNombre,BigDecimal tasaImpuesto,BigDecimal impuesto,BigDecimal subtotal,BigDecimal total) {}
