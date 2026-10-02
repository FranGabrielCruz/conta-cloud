package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record MovimientoTurnoCajaDto(UUID id,OffsetDateTime fechaHora,TipoMovimientoFinanciero tipo,
    MedioPagoMovimiento medioPago,String concepto,BigDecimal monto,EstadoMovimientoFinanciero estado){}
