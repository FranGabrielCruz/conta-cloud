package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record FacturaVentaResultadoDto(UUID id,String numero,BigDecimal total,BigDecimal recibido,BigDecimal cambio){}
