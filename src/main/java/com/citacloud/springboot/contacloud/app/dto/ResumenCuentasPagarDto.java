package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ResumenCuentasPagarDto(BigDecimal pendiente,BigDecimal vencido,BigDecimal venceHoy,
        BigDecimal porVencer,BigDecimal creditos,BigDecimal posicionNeta,String moneda,String simbolo,
        boolean consolidado,Map<String,BigDecimal> pendientesPorMoneda,
        Map<String,BigDecimal> vencidosPorMoneda,Map<String,BigDecimal> venceHoyPorMoneda,
        Map<String,BigDecimal> porVencerPorMoneda,Map<String,BigDecimal> creditosPorMoneda,
        Map<String,BigDecimal> posicionNetaPorMoneda,List<String> advertencias,OffsetDateTime actualizadoEn) {}
