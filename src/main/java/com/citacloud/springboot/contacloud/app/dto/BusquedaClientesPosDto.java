package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;

public record BusquedaClientesPosDto(List<ClientePosDto> clientes, boolean hayMasResultados) {
    public BusquedaClientesPosDto {
        clientes = clientes == null ? List.of() : List.copyOf(clientes);
    }
}
