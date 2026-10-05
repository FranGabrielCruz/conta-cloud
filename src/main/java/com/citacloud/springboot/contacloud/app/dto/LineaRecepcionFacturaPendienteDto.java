package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineaRecepcionFacturaPendienteDto(
        UUID lineaFacturaId, UUID lineaOrdenId, UUID productoId,
        BigDecimal facturada, BigDecimal recibidaAntes, BigDecimal pendiente) {
}
