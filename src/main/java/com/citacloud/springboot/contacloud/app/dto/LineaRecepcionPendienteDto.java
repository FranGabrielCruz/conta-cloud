package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineaRecepcionPendienteDto(
        UUID lineaOrdenId,
        UUID productoId,
        BigDecimal ordenada,
        BigDecimal recibidaAntes,
        BigDecimal pendiente) {
}
