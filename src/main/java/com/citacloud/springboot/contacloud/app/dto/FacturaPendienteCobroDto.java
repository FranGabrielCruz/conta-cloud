package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FacturaPendienteCobroDto(UUID cuentaCobrarId,UUID facturaId,String numero,LocalDate fecha,
        LocalDate vencimiento,UUID monedaId,String moneda,BigDecimal saldo) {}
