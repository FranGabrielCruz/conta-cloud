package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record MovimientoFinancieroDto(UUID id, TipoMovimientoFinanciero tipoMovimiento, LocalDate fecha,
    TipoCuentaDinero tipoCuenta, UUID cuentaId, String cuentaNombre, UUID monedaId, String monedaCodigo,
    BigDecimal monto, String concepto, String referencia, String descripcion,
    EstadoMovimientoFinanciero estado, TipoOrigenMovimiento tipoOrigen, OffsetDateTime creadoEn,
    OffsetDateTime anuladoEn, String anuladoPorNombre, String motivoAnulacion,
    boolean editable, boolean anulable,MedioPagoMovimiento medioPago,UUID sesionCajaId) {}
