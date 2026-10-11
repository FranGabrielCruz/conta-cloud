package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AntiguedadCuentaPagarDto(UUID proveedorId,String proveedor,UUID monedaId,String moneda,
        String simbolo,BigDecimal porVencer,BigDecimal venceHoy,BigDecimal dias1a30,
        BigDecimal dias31a60,BigDecimal dias61a90,BigDecimal mas90,BigDecimal total) {}
