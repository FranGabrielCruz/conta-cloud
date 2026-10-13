package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
public record ResumenKardexDto(BigDecimal saldoInicial,BigDecimal entradas,BigDecimal salidas,BigDecimal saldoFinal) {}
