package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record ConciliacionBancariaInput(UUID cuentaBancariaId,LocalDate fechaInicial,LocalDate fechaFinal,
    BigDecimal saldoInicialBanco,BigDecimal saldoFinalBanco) {}
