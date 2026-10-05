package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoCondicionPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FacturaProveedorRegistroPreviewDto(
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal impuesto,
        BigDecimal total,
        UUID condicionPagoId,
        String condicionPago,
        TipoCondicionPago tipoCondicionPago,
        int diasCredito,
        LocalDate vencimiento) {
}
