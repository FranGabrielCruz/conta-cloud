package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoDescuentoCotizacion;
import java.math.BigDecimal;
import java.util.UUID;

public record LineaCotizacionDto(UUID id,int numero,UUID productoId,String productoCodigo,String descripcion,
        UUID unidadMedidaId,String unidadMedida,String unidadMedidaAbreviatura,BigDecimal cantidad,BigDecimal precioUnitario,
        TipoDescuentoCotizacion tipoDescuento,BigDecimal valorDescuento,BigDecimal descuento,UUID impuestoId,
        String impuestoNombre,BigDecimal tasaImpuesto,BigDecimal subtotalBruto,BigDecimal baseImponible,
        BigDecimal impuesto,BigDecimal total) {}
