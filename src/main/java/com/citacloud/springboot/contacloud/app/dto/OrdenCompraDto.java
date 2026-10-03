package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoOrdenCompra;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrdenCompraDto(UUID id,String numero,UUID proveedorId,String proveedorNombre,String proveedorIdentificacion,
        UUID sucursalId,String sucursalNombre,LocalDate fecha,LocalDate fechaEntrega,UUID monedaId,String monedaCodigo,
        UUID condicionPagoId,String condicionPagoNombre,String referencia,String notas,BigDecimal subtotal,
        BigDecimal descuento,BigDecimal impuesto,BigDecimal total,EstadoOrdenCompra estado,long version,
        String motivoAnulacion,OffsetDateTime emitidaEn,boolean pdfDisponible,List<LineaOrdenCompraDto> lineas) {}
