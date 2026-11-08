package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.time.*;import java.util.*;
public record NotaDebitoVentaDocumento(UUID id,String numero,LocalDate fecha,LocalDateTime emitidaEn,String emitidaPor,
 UUID facturaId,String factura,LocalDate fechaFactura,String estadoFactura,String condicionPago,
 UUID sucursalId,UUID clienteId,String cliente,String identificacion,UUID monedaId,String moneda,
 UUID motivoId,String motivo,String descripcion,List<Linea> lineas,BigDecimal subtotal,BigDecimal descuento,BigDecimal impuesto,BigDecimal total,
 BigDecimal saldoAnterior,BigDecimal saldoPosterior,String empresa,String empresaLegal,String rnc,String direccionEmpresa,String telefono,String correoEmpresa,byte[] logoEmpresa,com.citacloud.springboot.contacloud.app.models.TipoCondicionPago condicionNota,BigDecimal pagado,BigDecimal saldoNota,BigDecimal recibido,BigDecimal cambio,List<Pago> pagos){
 public NotaDebitoVentaDocumento(UUID id,String numero,LocalDate fecha,LocalDateTime emitidaEn,String emitidaPor,
 UUID facturaId,String factura,LocalDate fechaFactura,String estadoFactura,String condicionPago,
 UUID sucursalId,UUID clienteId,String cliente,String identificacion,UUID monedaId,String moneda,
 UUID motivoId,String motivo,String descripcion,List<Linea> lineas,BigDecimal subtotal,BigDecimal descuento,BigDecimal impuesto,BigDecimal total,
 BigDecimal saldoAnterior,BigDecimal saldoPosterior,String empresa,String empresaLegal,String rnc,String direccionEmpresa,String telefono,String correoEmpresa,byte[] logoEmpresa){this(id,numero,fecha,emitidaEn,emitidaPor,facturaId,factura,fechaFactura,estadoFactura,condicionPago,sucursalId,clienteId,cliente,identificacion,monedaId,moneda,motivoId,motivo,descripcion,lineas,subtotal,descuento,impuesto,total,saldoAnterior,saldoPosterior,empresa,empresaLegal,rnc,direccionEmpresa,telefono,correoEmpresa,logoEmpresa,com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CREDIT,BigDecimal.ZERO,total,BigDecimal.ZERO,BigDecimal.ZERO,List.of());}
 public NotaDebitoVentaDocumento { condicionNota=condicionNota==null?com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CREDIT:condicionNota;pagado=pagado==null?BigDecimal.ZERO:pagado;saldoNota=saldoNota==null?total:saldoNota;recibido=recibido==null?BigDecimal.ZERO:recibido;cambio=cambio==null?BigDecimal.ZERO:cambio;pagos=pagos==null?List.of():List.copyOf(pagos); lineas=List.copyOf(lineas);logoEmpresa=logoEmpresa==null?null:logoEmpresa.clone(); }
 @Override public byte[] logoEmpresa(){return logoEmpresa==null?null:logoEmpresa.clone();}
 public record Pago(UUID id,UUID metodoPagoId,String metodo,String tipo,BigDecimal monto,BigDecimal recibido,BigDecimal cambio,String referencia,UUID cuentaId,UUID sesionId,String destino){}
 public record Linea(String descripcion,BigDecimal cantidad,BigDecimal precio,BigDecimal bruto,BigDecimal descuento,UUID impuestoId,String impuestoNombre,BigDecimal tasa,BigDecimal impuesto,BigDecimal total){}
}
