package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;import java.math.BigDecimal;import java.time.OffsetDateTime;
public record DevolucionVentaImpresionResumenDto(UUID id,String numero,OffsetDateTime fecha,String cliente,BigDecimal total,String moneda){}
