package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.UUID;
public record ValeCajaInput(UUID sesionId,UUID conceptoId,BigDecimal importe,String beneficiario,String referencia,String observacion,UUID idempotencia){}
