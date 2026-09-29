package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TasaResueltaDto(UUID tasaRegistradaId, BigDecimal tasa, LocalDate fecha, boolean derivada) {}
