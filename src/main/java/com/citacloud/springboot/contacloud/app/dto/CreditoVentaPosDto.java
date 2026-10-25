package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.ControlLimiteCredito;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreditoVentaPosDto(UUID clienteId,String clienteNombre,String identificacion,int diasCredito,
        LocalDate fechaFactura,LocalDate vencimiento,BigDecimal limiteCredito,BigDecimal saldoActual,
        BigDecimal totalFactura,BigDecimal saldoProyectado,ControlLimiteCredito controlLimite,
        boolean excedeLimite,boolean puedeAutorizarExceso) {
}
