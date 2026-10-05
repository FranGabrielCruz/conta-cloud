package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.UUID;
public record LineaNotaCreditoProveedorDto(UUID id,UUID lineaFacturaId,UUID productoId,String codigo,String descripcion,
        String unidad,BigDecimal cantidadFacturada,BigDecimal cantidad,BigDecimal precio,BigDecimal descuento,
        UUID impuestoId,String impuestoNombre,BigDecimal impuesto,BigDecimal subtotal,BigDecimal total) {}
