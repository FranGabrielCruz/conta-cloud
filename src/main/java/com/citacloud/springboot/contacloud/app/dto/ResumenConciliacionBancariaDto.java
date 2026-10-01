package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
public record ResumenConciliacionBancariaDto(BigDecimal saldoInicialBanco,BigDecimal creditosBanco,
    BigDecimal debitosBanco,BigDecimal netoBanco,BigDecimal saldoFinalBanco,
    BigDecimal saldoConciliado,BigDecimal diferencia,long pendientesContaCloud,
    long pendientesBanco,long conciliados,boolean estadoCuentaConsistente,boolean cuadrada) {}
