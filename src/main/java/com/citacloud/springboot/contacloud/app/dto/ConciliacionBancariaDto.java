package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.EstadoConciliacionBancaria;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record ConciliacionBancariaDto(UUID id,UUID cuentaBancariaId,String cuentaEtiqueta,UUID monedaId,
    String monedaCodigo,short decimales,LocalDate fechaInicial,LocalDate fechaFinal,
    BigDecimal saldoInicialBanco,BigDecimal saldoFinalBanco,EstadoConciliacionBancaria estado,
    OffsetDateTime creadoEn,OffsetDateTime finalizadoEn,UUID finalizadoPor,String finalizadoPorNombre,
    OffsetDateTime anuladoEn,UUID anuladoPor,String anuladoPorNombre,String motivoAnulacion) {}
