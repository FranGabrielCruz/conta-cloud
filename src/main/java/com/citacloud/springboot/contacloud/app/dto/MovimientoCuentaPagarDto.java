package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MovimientoCuentaPagarDto(OffsetDateTime fecha,String tipo,String documento,
        BigDecimal cargo,BigDecimal aplicacion,BigDecimal saldo,UUID documentoId) {}
