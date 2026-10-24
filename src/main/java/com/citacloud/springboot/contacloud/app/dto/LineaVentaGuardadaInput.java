package com.citacloud.springboot.contacloud.app.dto;import java.math.BigDecimal;import java.util.UUID;
public record LineaVentaGuardadaInput(UUID lineaId,UUID productoId,BigDecimal cantidad,BigDecimal precio,BigDecimal tasaImpuesto,UUID cotizacionLineaOrigenId,BigDecimal descuentoCotizado){
 public LineaVentaGuardadaInput(UUID productoId,BigDecimal cantidad,BigDecimal precio,BigDecimal tasaImpuesto){this(productoId,productoId,cantidad,precio,tasaImpuesto,null,BigDecimal.ZERO);}
 public LineaVentaGuardadaInput(UUID lineaId,UUID productoId,BigDecimal cantidad,BigDecimal precio,BigDecimal tasaImpuesto){this(lineaId,productoId,cantidad,precio,tasaImpuesto,null,BigDecimal.ZERO);}
 public LineaVentaGuardadaInput(UUID lineaId,UUID productoId,BigDecimal cantidad,BigDecimal precio,BigDecimal tasaImpuesto,UUID cotizacionLineaOrigenId){this(lineaId,productoId,cantidad,precio,tasaImpuesto,cotizacionLineaOrigenId,BigDecimal.ZERO);}
}
