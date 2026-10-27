package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.UUID;

public record ConfirmarCierreCajaInput(UUID sesionId, UUID idempotencia, List<Cantidad> cantidades,
        String observacion) {
    public record Cantidad(UUID denominacionId, Integer cantidad) {}
}
