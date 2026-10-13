package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;

public record KardexInventarioReporteConsultaDto(List<MovimientoInventarioDto> movimientos,
        ResumenKardexDto resumen) {
    public KardexInventarioReporteConsultaDto {
        movimientos = List.copyOf(movimientos);
    }
}
