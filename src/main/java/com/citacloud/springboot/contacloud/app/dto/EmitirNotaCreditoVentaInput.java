package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public record EmitirNotaCreditoVentaInput(UUID facturaId,LocalDate fecha,UUID motivoId,String descripcion,
        String observacion,List<Linea> lineas,UUID idempotencia) {
    public EmitirNotaCreditoVentaInput {lineas=lineas==null?List.of():Collections.unmodifiableList(new ArrayList<>(lineas));}
    public record Linea(UUID lineaFacturaId,BigDecimal cantidad) {}
}
