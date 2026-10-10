package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
public record CreditosProveedorDto(BigDecimal notasCredito,BigDecimal pagosDisponibles){public BigDecimal total(){return notasCredito.add(pagosDisponibles);}}
