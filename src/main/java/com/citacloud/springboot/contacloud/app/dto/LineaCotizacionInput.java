package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoDescuentoCotizacion;
import java.math.BigDecimal;
import java.util.UUID;

public record LineaCotizacionInput(UUID productoId,String descripcion,BigDecimal cantidad,BigDecimal precioUnitario,
        TipoDescuentoCotizacion tipoDescuento,BigDecimal valorDescuento,UUID impuestoId) {}
