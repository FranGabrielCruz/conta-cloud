package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.UUID;
public record LineaVistaPreviaDescuentoInput(UUID lineaId,UUID productoId,String productoNombre,BigDecimal cantidad,BigDecimal precioEsperado,BigDecimal tasaImpuestoEsperada,boolean permiteDescuentos){
 public LineaVistaPreviaDescuentoInput(UUID productoId,BigDecimal cantidad,BigDecimal precioEsperado,BigDecimal tasaImpuestoEsperada){this(productoId,productoId,null,cantidad,precioEsperado,tasaImpuestoEsperada,true);}
}
