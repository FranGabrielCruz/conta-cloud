package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record NotaCreditoVentaDocumento(UUID id,String numero,LocalDate fecha,LocalDateTime emitidaEn,String emitidaPor,
        String origen,String devolucion,String estado,UUID sucursalId,UUID clienteId,String cliente,String identificacion,
        String direccionCliente,String empresa,String rnc,String direccionEmpresa,String telefono,
        String factura,LocalDate fechaFactura,String ncfFactura,String ncf,String condicionPago,String moneda,
        String motivo,String descripcion,String observacion,List<Linea> lineas,BigDecimal subtotal,BigDecimal descuento,
        BigDecimal recargo,BigDecimal impuesto,BigDecimal total,BigDecimal saldoAnterior,BigDecimal aplicadoCxC,
        BigDecimal saldoPosterior,BigDecimal creditoCliente,String empresaLegal,String correoEmpresa,byte[] logoEmpresa) {
    public NotaCreditoVentaDocumento {lineas=List.copyOf(lineas);logoEmpresa=logoEmpresa==null?null:logoEmpresa.clone();}
    @Override public byte[] logoEmpresa(){return logoEmpresa==null?null:logoEmpresa.clone();}
    public NotaCreditoVentaDocumento(UUID id,String numero,LocalDate fecha,LocalDateTime emitidaEn,String emitidaPor,
        String origen,String devolucion,String estado,UUID sucursalId,UUID clienteId,String cliente,String identificacion,
        String direccionCliente,String empresa,String rnc,String direccionEmpresa,String telefono,
        String factura,LocalDate fechaFactura,String ncfFactura,String ncf,String condicionPago,String moneda,
        String motivo,String descripcion,String observacion,List<Linea> lineas,BigDecimal subtotal,BigDecimal descuento,
        BigDecimal recargo,BigDecimal impuesto,BigDecimal total,BigDecimal saldoAnterior,BigDecimal aplicadoCxC,
        BigDecimal saldoPosterior,BigDecimal creditoCliente){this(id,numero,fecha,emitidaEn,emitidaPor,origen,devolucion,estado,sucursalId,clienteId,cliente,identificacion,direccionCliente,empresa,rnc,direccionEmpresa,telefono,factura,fechaFactura,ncfFactura,ncf,condicionPago,moneda,motivo,descripcion,observacion,lineas,subtotal,descuento,recargo,impuesto,total,saldoAnterior,aplicadoCxC,saldoPosterior,creditoCliente,null,null,null);}
    public NotaCreditoVentaDocumento conEstado(String nuevo){return new NotaCreditoVentaDocumento(id,numero,fecha,emitidaEn,emitidaPor,origen,devolucion,nuevo,sucursalId,clienteId,cliente,identificacion,direccionCliente,empresa,rnc,direccionEmpresa,telefono,factura,fechaFactura,ncfFactura,ncf,condicionPago,moneda,motivo,descripcion,observacion,lineas,subtotal,descuento,recargo,impuesto,total,saldoAnterior,aplicadoCxC,saldoPosterior,creditoCliente,empresaLegal,correoEmpresa,logoEmpresa);}
    public record Linea(UUID lineaFacturaId,String codigo,String descripcion,String unidad,BigDecimal cantidad,
            BigDecimal precio,BigDecimal bruto,BigDecimal descuento,BigDecimal recargo,String impuestoNombre,
            BigDecimal tasa,BigDecimal impuesto,BigDecimal total) {}
}
