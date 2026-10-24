package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record LineaVentaInput(UUID lineaId,UUID productoId,BigDecimal cantidad,BigDecimal precioEsperado,
                             UUID cotizacionLineaOrigenId,BigDecimal descuentoCotizado){
    public LineaVentaInput(UUID productoId,BigDecimal cantidad,BigDecimal precioEsperado){this(productoId,productoId,cantidad,precioEsperado,null,BigDecimal.ZERO);}
    public LineaVentaInput(UUID lineaId,UUID productoId,BigDecimal cantidad,BigDecimal precioEsperado){this(lineaId,productoId,cantidad,precioEsperado,null,BigDecimal.ZERO);}
}
