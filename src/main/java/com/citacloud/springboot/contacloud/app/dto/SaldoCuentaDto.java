package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import java.math.BigDecimal;
import java.util.UUID;

public record SaldoCuentaDto(UUID id,TipoCuentaDinero tipo,String cuenta,UUID monedaId,String moneda,String simbolo,
        UUID sucursalId,String sucursal,boolean activo,BigDecimal saldoInicial,BigDecimal entradas,
        BigDecimal salidas,BigDecimal saldoActual) {}
