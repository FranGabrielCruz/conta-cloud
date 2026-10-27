package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoDenominacionCaja;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTO deliberadamente ciego: no expone esperado, movimientos ni diferencia. */
public record PreparacionCierreCajaDto(UUID sesionId, String turno, String caja, String sucursal,
        String monedaCodigo, String monedaSimbolo, OffsetDateTime apertura, String usuario,
        List<Denominacion> denominaciones) {
    public record Denominacion(UUID id, BigDecimal valor, TipoDenominacionCaja tipo, int orden) {}
}
