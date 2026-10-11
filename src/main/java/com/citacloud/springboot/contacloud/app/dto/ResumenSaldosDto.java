package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ResumenSaldosDto(BigDecimal bancos,BigDecimal cajas,BigDecimal general,String moneda,String simbolo,
        boolean equivalente,boolean conversionAplicada,Map<String,BigDecimal> totalesPorMoneda,List<String> advertencias,
        OffsetDateTime actualizadoEn) {}
