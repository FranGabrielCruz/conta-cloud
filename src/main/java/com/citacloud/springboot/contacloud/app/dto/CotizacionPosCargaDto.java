package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CotizacionPosCargaDto(UUID cotizacionId,String numero,ClientePosDto cliente,String monedaCodigo,
        List<Linea> lineas) {
    public record Linea(UUID cotizacionLineaId,ProductoPosDto producto,BigDecimal cantidad,
            BigDecimal descuentoUnitario) {}
}
