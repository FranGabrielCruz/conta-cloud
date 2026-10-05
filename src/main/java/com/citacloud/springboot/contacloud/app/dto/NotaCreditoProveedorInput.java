package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.MotivoNotaCreditoProveedor;import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
public record NotaCreditoProveedorInput(UUID proveedorId,LocalDate fecha,String numeroProveedor,String numeroFiscal,
        UUID monedaId,BigDecimal tasaCambio,MotivoNotaCreditoProveedor motivo,String otroMotivo,UUID facturaRelacionadaId,
        String observacion,List<LineaNotaCreditoProveedorInput> lineas,Long version) {}
