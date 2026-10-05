package com.citacloud.springboot.contacloud.app.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecepcionCompraInput(UUID proveedorId, UUID ordenCompraId, UUID facturaProveedorId, UUID almacenId, LocalDate fecha,
        String referencia, String notas, String motivoDiferencia,
        List<LineaRecepcionCompraInput> lineas, Long version, UUID claveIdempotencia) {
}
