package com.citacloud.springboot.contacloud.app.dto;import java.math.BigDecimal;import java.util.UUID;
public record LineaVentaGuardadaInput(UUID lineaId,UUID productoId,BigDecimal cantidad,BigDecimal precio,BigDecimal tasaImpuesto){
 public LineaVentaGuardadaInput(UUID productoId,BigDecimal cantidad,BigDecimal precio,BigDecimal tasaImpuesto){this(productoId,productoId,cantidad,precio,tasaImpuesto);}
}
