package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;

public record SupplierCreditApplicationReportLine(String fecha,String factura,String numeroProveedor,
        BigDecimal importe) {
    public String getFecha(){return fecha;} public String getFactura(){return factura;}
    public String getNumeroProveedor(){return numeroProveedor;} public BigDecimal getImporte(){return importe;}
}
