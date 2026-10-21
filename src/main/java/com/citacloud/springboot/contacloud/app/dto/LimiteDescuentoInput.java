package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.UUID;
public record LimiteDescuentoInput(UUID rolId,BigDecimal porcentajeMaximo,BigDecimal montoMaximo,boolean activo,Long version){}
