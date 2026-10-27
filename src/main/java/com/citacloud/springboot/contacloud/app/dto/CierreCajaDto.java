package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.MedioPagoMovimiento;
import com.citacloud.springboot.contacloud.app.models.ResultadoRecuentoCaja;
import com.citacloud.springboot.contacloud.app.models.TipoDenominacionCaja;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CierreCajaDto(UUID id, String numero, UUID sesionId, String turno, String caja,
        String sucursal, String monedaCodigo, String monedaSimbolo, OffsetDateTime apertura,
        OffsetDateTime cierre, String abiertoPor, String cerradoPor, BigDecimal fondoInicial,
        BigDecimal ventasEfectivo, BigDecimal cobrosEfectivo, BigDecimal otrasEntradasEfectivo,
        BigDecimal valesCaja, BigDecimal devolucionesEfectivo, BigDecimal otrasSalidasEfectivo,
        BigDecimal totalEntradasEfectivo, BigDecimal totalSalidasEfectivo, BigDecimal esperado,
        BigDecimal contado, BigDecimal diferencia, ResultadoRecuentoCaja resultado,
        String observacion, List<Detalle> detalles, List<MedioPago> mediosPago) {
    public record Detalle(BigDecimal denominacion, TipoDenominacionCaja tipo, int cantidad,
            BigDecimal subtotal) {}
    public record MedioPago(MedioPagoMovimiento medio, BigDecimal importe) {}
}
