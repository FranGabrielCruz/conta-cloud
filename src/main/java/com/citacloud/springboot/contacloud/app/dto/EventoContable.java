package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Evento interno, sin endpoint público. El ámbito se obtiene siempre de la sesión. */
public record EventoContable(Fuente fuente,long version,LocalDate fecha,UUID moneda,String concepto,String traceId,Datos datos) {
 public sealed interface Datos permits Compra,Venta,Transferencia,Depreciacion,Cobro,PagoProveedor,AplicacionAnticipo,Inventario,CostoVenta {}
 public record RecursoImporte(TipoCuentaDinero tipo,UUID id,BigDecimal importe) {}
 public record Cobro(List<RecursoImporte> recursos,BigDecimal recibido,BigDecimal aplicado) implements Datos {
  public Cobro { recursos=List.copyOf(recursos); }
 }
 public record PagoProveedor(RecursoImporte recurso,BigDecimal aplicado) implements Datos {}
 public record AplicacionAnticipo(BigDecimal importe) implements Datos {}
 public record Valoracion(UUID producto,BigDecimal cantidad,BigDecimal costoUnitario,UUID moneda,BigDecimal tasa,BigDecimal importeBase) {}
 public record Inventario(BigDecimal entrada,BigDecimal salida,List<Valoracion> valoraciones) implements Datos {
  public Inventario { valoraciones=List.copyOf(valoraciones); }
  public Inventario(BigDecimal entrada,BigDecimal salida){this(entrada,salida,List.of());}
 }
 public record CostoVenta(BigDecimal importe,List<Valoracion> valoraciones) implements Datos {
  public CostoVenta { valoraciones=List.copyOf(valoraciones); }
 }
 public record RenglonCompra(boolean inventariable,BigDecimal neto) {}
 public record Compra(List<RenglonCompra> lineas,BigDecimal impuesto,BigDecimal total,
                      TipoCuentaDinero tipoPago,UUID fuentePago) implements Datos {
  public Compra { lineas=List.copyOf(lineas); }
 }
 public record Venta(BigDecimal total,BigDecimal impuesto) implements Datos {}
 public record Transferencia(TipoCuentaDinero tipoOrigen,UUID origen,TipoCuentaDinero tipoDestino,UUID destino,
                             UUID monedaDestino,BigDecimal importe) implements Datos {}
 public record Depreciacion(BigDecimal importe) implements Datos {}
}

