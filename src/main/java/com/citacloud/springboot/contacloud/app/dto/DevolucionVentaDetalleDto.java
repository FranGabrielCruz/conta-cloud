package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
public record DevolucionVentaDetalleDto(UUID facturaId,String facturaNumero,OffsetDateTime fechaFactura,String cliente,
        String identificacion,BigDecimal totalFactura,String estado,String moneda,String simbolo,String condicionPago,
        List<Linea> lineas,List<Motivo> motivos,List<Almacen> almacenes){
    public record Linea(UUID lineaFacturaId,UUID productoId,String codigo,String producto,String unidad,
            BigDecimal facturado,BigDecimal devuelto,BigDecimal disponible,BigDecimal precio,
            BigDecimal subtotalBruto,BigDecimal descuento,BigDecimal recargo,BigDecimal impuesto,
            BigDecimal total,boolean controlaExistencia){}
    public record Motivo(UUID id,String codigo,String nombre,boolean requiereObservacion){}
    public record Almacen(UUID id,String nombre){}
}
