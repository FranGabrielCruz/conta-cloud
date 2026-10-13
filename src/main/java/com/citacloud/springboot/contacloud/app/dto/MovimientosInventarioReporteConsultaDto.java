package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;

public record MovimientosInventarioReporteConsultaDto(List<MovimientoInventarioDto> movimientos,
        ResumenMovimientosInventarioDto resumen) {
    public MovimientosInventarioReporteConsultaDto {
        movimientos = List.copyOf(movimientos);
    }
}
