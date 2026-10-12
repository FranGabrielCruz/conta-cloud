package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record ExistenciaAlmacenDto(UUID productoId,String codigo,String producto,String categoria,String unidad,
                                   BigDecimal existencia,BigDecimal reservado,BigDecimal disponible) {}
