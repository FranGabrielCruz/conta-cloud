package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.UUID;
public record LineaFacturaParaCreditoDto(UUID lineaFacturaId,UUID productoId,String codigo,String descripcion,String unidad,
        BigDecimal cantidadFacturada,BigDecimal cantidadAcreditada,BigDecimal cantidadDisponible,BigDecimal precio,
        BigDecimal descuento,UUID impuestoId,String impuestoNombre,BigDecimal impuestoTasa) {}
