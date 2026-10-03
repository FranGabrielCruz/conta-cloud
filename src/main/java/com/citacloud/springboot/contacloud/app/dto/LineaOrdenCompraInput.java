package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineaOrdenCompraInput(UUID productoId,String descripcion,BigDecimal cantidad,BigDecimal precioUnitario,
                                    BigDecimal descuento,UUID impuestoId) {
    public LineaOrdenCompraInput(String descripcion,BigDecimal cantidad,BigDecimal precioUnitario,BigDecimal descuento,UUID impuestoId){
        this(null,descripcion,cantidad,precioUnitario,descuento,impuestoId);
    }
}
