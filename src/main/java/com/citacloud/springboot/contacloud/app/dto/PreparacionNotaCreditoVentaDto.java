package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public record PreparacionNotaCreditoVentaDto(UUID facturaId,String factura,LocalDate fechaFactura,String cliente,String identificacion,
        String moneda,BigDecimal totalFactura,BigDecimal acreditado,BigDecimal disponible,List<Linea> lineas,List<Motivo> motivos,String condicionPago) {
    public record Linea(UUID id,String descripcion,BigDecimal facturada,BigDecimal acreditada,BigDecimal disponible) {}
    public record Motivo(UUID id,String nombre) {}
}
