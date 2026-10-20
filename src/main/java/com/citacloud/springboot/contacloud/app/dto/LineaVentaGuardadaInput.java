package com.citacloud.springboot.contacloud.app.dto;import java.math.BigDecimal;import java.util.UUID;
public record LineaVentaGuardadaInput(UUID productoId,BigDecimal cantidad,BigDecimal precio,BigDecimal tasaImpuesto){}
