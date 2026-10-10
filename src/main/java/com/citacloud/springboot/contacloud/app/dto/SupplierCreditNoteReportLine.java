package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;

public record SupplierCreditNoteReportLine(Integer numero,String descripcion,BigDecimal cantidad,
        BigDecimal precioUnitario,BigDecimal impuesto,BigDecimal total) {
    public Integer getNumero(){return numero;} public String getDescripcion(){return descripcion;}
    public BigDecimal getCantidad(){return cantidad;}
    public BigDecimal getPrecioUnitario(){return precioUnitario;} public BigDecimal getImpuesto(){return impuesto;}
    public BigDecimal getTotal(){return total;}
}
