package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;

public record SupplierPaymentReportLine(String factura,String numeroProveedor,String fechaFactura,
        String fechaAplicacion,BigDecimal saldoAnterior,BigDecimal importe,BigDecimal saldoRestante,String currencyPrefix) {
    public String getFactura(){return factura;}
    public String getNumeroProveedor(){return numeroProveedor;}
    public String getFechaFactura(){return fechaFactura;}
    public String getFechaAplicacion(){return fechaAplicacion;}
    public BigDecimal getImporte(){return importe;}
    public BigDecimal getSaldoAnterior(){return saldoAnterior;}
    public BigDecimal getSaldoRestante(){return saldoRestante;}
    public String getSaldoAnteriorTexto(){return money(saldoAnterior);}
    public String getImporteTexto(){return money(importe);}
    public String getSaldoRestanteTexto(){return money(saldoRestante);}
    private String money(BigDecimal value){return currencyPrefix+" "+String.format(java.util.Locale.US,"%,.2f",value);}
}
