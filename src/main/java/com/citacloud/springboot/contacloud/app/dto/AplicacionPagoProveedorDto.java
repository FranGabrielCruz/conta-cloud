package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.time.OffsetDateTime;import java.util.UUID;
public record AplicacionPagoProveedorDto(UUID id,UUID facturaId,String factura,String numeroProveedor,OffsetDateTime fecha,BigDecimal monto){}
