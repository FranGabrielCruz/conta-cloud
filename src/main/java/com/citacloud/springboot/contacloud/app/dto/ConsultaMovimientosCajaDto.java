package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoSesionCaja;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** One read snapshot; detail filters never change session totals. */
public record ConsultaMovimientosCajaDto(Resumen resumen, Page<MovimientoCajaDto> movimientos) {
    public record Resumen(UUID sesionId, UUID sucursalId, UUID cajaId, String caja, String sucursal,
            String cajero, OffsetDateTime apertura, String moneda, EstadoSesionCaja estado,
            BigDecimal fondoInicial, BigDecimal entradas, BigDecimal salidas, BigDecimal efectivoEsperado,
            List<FormaPago> formasPago, BigDecimal totalOperaciones) {
        public Resumen { formasPago = List.copyOf(formasPago); }
    }
    public record FormaPago(String codigo, String nombre, BigDecimal importe) {}
}
