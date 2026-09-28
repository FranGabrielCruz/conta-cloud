package com.citacloud.springboot.contacloud.app.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PeriodoFiscalDto(
    UUID id,
    String nombre,
    LocalDate fechaInicial,
    LocalDate fechaFinal,
    String estado,
    OffsetDateTime fechaCierre,
    UUID usuarioCierreId,
    OffsetDateTime fechaReapertura,
    UUID usuarioReaperturaId
) {}
