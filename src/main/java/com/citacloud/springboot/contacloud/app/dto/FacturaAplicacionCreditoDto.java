package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.UUID;
public record FacturaAplicacionCreditoDto(UUID facturaId,String numeroInterno,String numeroProveedor,String numeroFiscal,
        LocalDate fecha,LocalDate vencimiento,UUID monedaId,String moneda,BigDecimal total,BigDecimal aplicado,
        BigDecimal creditosAplicados,BigDecimal saldo) {
    public FacturaAplicacionCreditoDto(UUID facturaId,String numero,LocalDate fecha,UUID monedaId,String moneda,
            BigDecimal total,BigDecimal aplicado,BigDecimal creditosAplicados,BigDecimal saldo) {
        this(facturaId,numero,numero,null,fecha,fecha,monedaId,moneda,total,aplicado,creditosAplicados,saldo);
    }
    public String numero(){return numeroProveedor;}
}
