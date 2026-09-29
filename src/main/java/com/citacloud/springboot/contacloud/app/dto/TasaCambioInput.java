package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TasaCambioInput(
    @NotNull UUID monedaOrigenId,
    @NotNull UUID monedaDestinoId,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 8) BigDecimal tasa,
    @NotNull LocalDate fecha,
    boolean activo) {}
