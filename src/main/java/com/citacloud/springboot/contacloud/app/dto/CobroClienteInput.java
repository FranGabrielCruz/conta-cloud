package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public record CobroClienteInput(UUID clienteId,UUID monedaId,UUID sucursalId,LocalDate fecha,BigDecimal recibido,
        String referencia,String observacion,UUID cajaId,UUID sesionCajaId,UUID idempotencia,
        List<AplicacionFactura> aplicaciones,List<AplicacionCobroInput> medios) {
    public record AplicacionFactura(UUID cuentaCobrarId,UUID facturaId,BigDecimal monto) {}
}
