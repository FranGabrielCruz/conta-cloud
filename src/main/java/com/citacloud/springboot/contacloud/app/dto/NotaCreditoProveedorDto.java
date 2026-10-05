package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
public record NotaCreditoProveedorDto(UUID id,String numero,LocalDate fecha,UUID proveedorId,String proveedor,
        String identificacionProveedor,String numeroProveedor,String numeroFiscal,UUID monedaId,String moneda,
        BigDecimal tasaCambio,MotivoNotaCreditoProveedor motivo,String otroMotivo,UUID facturaRelacionadaId,
        String facturaRelacionada,LocalDate fechaFactura,BigDecimal totalFactura,BigDecimal saldoFactura,String observacion,
        BigDecimal subtotal,BigDecimal descuento,BigDecimal impuesto,BigDecimal total,BigDecimal aplicado,
        BigDecimal disponible,EstadoNotaCreditoProveedor estado,long version,String motivoAnulacion,
        List<LineaNotaCreditoProveedorDto> lineas,List<AplicacionNotaCreditoProveedorDto> aplicaciones) {}
