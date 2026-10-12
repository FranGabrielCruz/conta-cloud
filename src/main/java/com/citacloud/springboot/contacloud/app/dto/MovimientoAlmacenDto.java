package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record MovimientoAlmacenDto(UUID id,LocalDate fecha,String documento,String producto,String concepto,
                                   BigDecimal entrada,BigDecimal salida,BigDecimal saldoPosterior,
                                   String tipoReferencia,UUID referenciaId) {}
