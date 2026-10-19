package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoFacturaVenta;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public record FacturaVentaDetalleDto(UUID id,String numero,OffsetDateTime fechaHora,String cliente,
        String identificacion,EstadoFacturaVenta estado,String moneda,String condicionPago,String vendedor,String nota,
        BigDecimal subtotal,BigDecimal descuento,BigDecimal recargo,BigDecimal impuestos,BigDecimal total,
        List<Linea> lineas,List<Pago> pagos) {
    public record Linea(String producto,String unidad,BigDecimal cantidad,BigDecimal precio,BigDecimal impuesto,BigDecimal total) {}
    public record Pago(String metodo,BigDecimal monto,BigDecimal recibido,BigDecimal cambio,String referencia) {}
}
