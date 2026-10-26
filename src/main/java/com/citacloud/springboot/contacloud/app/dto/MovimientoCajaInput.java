package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoMovimientoFinanciero;
import java.math.BigDecimal;
import java.util.UUID;
public record MovimientoCajaInput(UUID sesionId,TipoMovimientoFinanciero tipo,UUID conceptoId,
    BigDecimal importe,String referencia,String observacion,UUID idempotencia){}
