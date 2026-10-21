package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record LineaVentaInput(UUID lineaId,UUID productoId,BigDecimal cantidad,BigDecimal precioEsperado){
    public LineaVentaInput(UUID productoId,BigDecimal cantidad,BigDecimal precioEsperado){this(productoId,productoId,cantidad,precioEsperado);}
}
