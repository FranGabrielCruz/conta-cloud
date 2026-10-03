package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineaOrdenCompraDto(UUID id,int numero,String descripcion,BigDecimal cantidad,BigDecimal precioUnitario,
        BigDecimal descuento,UUID impuestoId,String impuestoNombre,BigDecimal tasaImpuesto,
        BigDecimal subtotalBruto,BigDecimal baseImponible,BigDecimal impuesto,BigDecimal total) {}
