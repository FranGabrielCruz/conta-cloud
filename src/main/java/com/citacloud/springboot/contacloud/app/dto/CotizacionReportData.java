package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record CotizacionReportData(UUID tenantId,UUID empresaId,UUID cotizacionId,String numero,
        String empresaNombreComercial,String empresaRazonSocial,String empresaIdentificacion,String empresaDireccion,
        String empresaTelefono,String empresaCorreo,byte[] empresaLogo,String clienteNombre,String clienteIdentificacion,
        String clienteDireccion,String clienteTelefono,String clienteCorreo,LocalDate fecha,LocalDate validaHasta,
        String sucursal,String monedaCodigo,String referencia,String condiciones,BigDecimal subtotal,BigDecimal descuento,
        BigDecimal impuesto,BigDecimal total,String estado,String generadoPor,OffsetDateTime generadoEn,
        List<CotizacionReportLine> lineas) {}
