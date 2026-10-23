package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record DevolucionVentaResultadoDto(UUID id,String numero,String notaCreditoNumero,BigDecimal total,String moneda){}
