package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.MotivoNotaCreditoProveedor;import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
public record NotaCreditoProveedorInput(UUID proveedorId,LocalDate fecha,String numeroProveedor,String numeroFiscal,
        UUID monedaId,BigDecimal tasaCambio,MotivoNotaCreditoProveedor motivo,String otroMotivo,UUID facturaRelacionadaId,
        String observacion,List<LineaNotaCreditoProveedorInput> lineas,Long version,UUID claveOperacion) {
    public NotaCreditoProveedorInput { if (claveOperacion == null) claveOperacion = UUID.randomUUID(); }
    public NotaCreditoProveedorInput(UUID proveedorId,LocalDate fecha,String numeroProveedor,String numeroFiscal,
            UUID monedaId,BigDecimal tasaCambio,MotivoNotaCreditoProveedor motivo,String otroMotivo,UUID facturaRelacionadaId,
            String observacion,List<LineaNotaCreditoProveedorInput> lineas,Long version) {
        this(proveedorId,fecha,numeroProveedor,numeroFiscal,monedaId,tasaCambio,motivo,otroMotivo,facturaRelacionadaId,
            observacion,lineas,version,null);
    }
}
