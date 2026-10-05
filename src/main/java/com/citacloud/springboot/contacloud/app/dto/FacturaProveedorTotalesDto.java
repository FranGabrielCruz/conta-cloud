package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;

public record FacturaProveedorTotalesDto(
        BigDecimal subtotal, BigDecimal descuento, BigDecimal impuesto, BigDecimal total) {
}
