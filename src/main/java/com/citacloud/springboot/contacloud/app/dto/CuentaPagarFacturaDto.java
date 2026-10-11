package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CuentaPagarFacturaDto(UUID facturaId,UUID proveedorId,String factura,String proveedor,
        String identificacionProveedor,String numeroProveedor,String numeroFiscal,LocalDate fecha,
        LocalDate vencimiento,UUID monedaId,String moneda,String simbolo,BigDecimal total,
        BigDecimal aplicado,BigDecimal saldo,String estado,String situacion,long dias) {}
