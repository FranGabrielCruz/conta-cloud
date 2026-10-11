package com.citacloud.springboot.contacloud.app.repositories;

import java.math.BigDecimal;import java.time.*;import java.util.UUID;

public interface MovimientoSaldoProjection {
    UUID getId();LocalDate getFecha();OffsetDateTime getRegistradoEn();String getDocumento();String getConcepto();
    String getTipo();BigDecimal getMonto();BigDecimal getSaldo();String getEstado();String getOrigen();UUID getOrigenId();
}
