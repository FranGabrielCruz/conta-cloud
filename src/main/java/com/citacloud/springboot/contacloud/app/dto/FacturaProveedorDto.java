package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FacturaProveedorDto(UUID id,UUID proveedorId,String proveedor,UUID sucursalId,String sucursal,
        String numeroFactura,String numeroFiscal,LocalDate fecha,LocalDate vencimiento,UUID condicionPagoId,
        String condicionPago,UUID monedaId,String moneda,UUID ordenCompraId,String ordenCompra,
        String referencia,String notas,BigDecimal subtotal,BigDecimal descuento,BigDecimal impuesto,
        BigDecimal total,EstadoFacturaProveedor estado,long version,String motivoAnulacion,
        BigDecimal saldo,EstadoCuentaPagar estadoCuenta,List<LineaFacturaProveedorDto> lineas) {}
