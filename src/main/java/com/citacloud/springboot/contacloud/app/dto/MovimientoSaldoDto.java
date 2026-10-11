package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record MovimientoSaldoDto(UUID id,LocalDate fecha,OffsetDateTime registradoEn,String documento,String concepto,
        TipoMovimientoFinanciero tipo,BigDecimal entrada,BigDecimal salida,BigDecimal saldo,
        EstadoMovimientoFinanciero estado,TipoOrigenMovimiento origen,UUID origenId) {}
