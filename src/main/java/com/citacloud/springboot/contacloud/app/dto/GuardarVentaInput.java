package com.citacloud.springboot.contacloud.app.dto;import java.math.BigDecimal;import java.util.*;
public record GuardarVentaInput(UUID ventaGuardadaId,String token,UUID almacenId,UUID clienteId,String referencia,String observacion,BigDecimal descuento,BigDecimal recargo,List<LineaVentaGuardadaInput> lineas){}
