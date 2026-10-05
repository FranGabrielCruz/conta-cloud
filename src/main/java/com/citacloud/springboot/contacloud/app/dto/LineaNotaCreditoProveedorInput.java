package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.UUID;
public record LineaNotaCreditoProveedorInput(UUID lineaFacturaId,UUID productoId,String descripcion,BigDecimal cantidad,
        BigDecimal precioUnitario,BigDecimal descuento,UUID impuestoId) {}
