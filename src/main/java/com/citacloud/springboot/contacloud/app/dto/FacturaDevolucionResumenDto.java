package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
public record FacturaDevolucionResumenDto(UUID id,String numero,OffsetDateTime fechaHora,String cliente,
        BigDecimal total,String moneda,String estado){}
