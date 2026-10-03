package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;

public record PurchaseOrderReportLine(Integer numero,String codigo,String descripcion,String unidad,
        BigDecimal cantidad,BigDecimal precioUnitario,BigDecimal descuento,BigDecimal impuesto,BigDecimal total) {
    public Integer getNumero(){return numero;} public String getCodigo(){return codigo;} public String getDescripcion(){return descripcion;}
    public String getUnidad(){return unidad;} public BigDecimal getCantidad(){return cantidad;} public BigDecimal getPrecioUnitario(){return precioUnitario;}
    public BigDecimal getDescuento(){return descuento;} public BigDecimal getImpuesto(){return impuesto;} public BigDecimal getTotal(){return total;}
}
