package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record FacturaRecepcionOpcionDto(
        UUID id, UUID proveedorId, UUID ordenCompraId, String numero, String etiqueta) {
}
