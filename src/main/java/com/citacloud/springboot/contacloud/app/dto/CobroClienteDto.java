package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record CobroClienteDto(UUID id,String numero,UUID clienteId,String cliente,String identificacion,
        UUID monedaId,String moneda,UUID sucursalId,String sucursal,LocalDate fecha,OffsetDateTime confirmadoEn,BigDecimal recibido,
        BigDecimal aplicado,BigDecimal disponible,EstadoReciboCliente estado,String referencia,String observacion,
        String usuario,String motivoAnulacion,List<Aplicacion> aplicaciones,List<Medio> medios) {
    public record Aplicacion(UUID id,UUID facturaId,String factura,LocalDate fechaFactura,OffsetDateTime aplicadaEn,
            BigDecimal saldoAnterior,BigDecimal monto,BigDecimal saldoRestante,boolean revertida) {}
    public record Medio(UUID id,String medio,String destino,String referencia,BigDecimal monto) {}
}
