package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
public record ResumenTurnoCajaDto(BigDecimal fondoInicial,BigDecimal entradasEfectivo,BigDecimal salidasEfectivo,
    BigDecimal efectivoEsperado,BigDecimal entradasTarjeta,BigDecimal entradasTransferencia,
    BigDecimal otrasEntradas,BigDecimal totalEntradas){}
