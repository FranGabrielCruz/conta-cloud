package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TasaCambioDto(
    UUID id,
    UUID monedaOrigenId,
    String monedaOrigenCodigo,
    String monedaOrigenNombre,
    UUID monedaDestinoId,
    String monedaDestinoCodigo,
    String monedaDestinoNombre,
    BigDecimal tasa,
    LocalDate fecha,
    boolean activo) {}
