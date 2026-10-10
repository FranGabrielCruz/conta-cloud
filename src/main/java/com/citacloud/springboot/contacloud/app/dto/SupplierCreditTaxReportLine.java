package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;

public record SupplierCreditTaxReportLine(String nombre,BigDecimal importe) {
    public String getNombre(){return nombre;} public BigDecimal getImporte(){return importe;}
}
