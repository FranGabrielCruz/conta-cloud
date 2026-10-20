package com.citacloud.springboot.contacloud.app.dto;import java.math.BigDecimal;import java.time.OffsetDateTime;import java.util.UUID;
public record VentaGuardadaResumenDto(UUID id,String numero,OffsetDateTime fechaHora,String referencia,String cliente,int items,BigDecimal total,String moneda){}
