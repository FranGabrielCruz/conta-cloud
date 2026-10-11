package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CuentaPagarProveedorDto(UUID proveedorId,String proveedor,String identificacion,
        UUID monedaId,String moneda,String simbolo,BigDecimal porVencer,BigDecimal venceHoy,
        BigDecimal vencido,BigDecimal pendiente,BigDecimal creditoDisponible) {}
