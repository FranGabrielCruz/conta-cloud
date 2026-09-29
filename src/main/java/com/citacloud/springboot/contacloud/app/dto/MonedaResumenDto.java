package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record MonedaResumenDto(UUID id, String codigo, String nombre, boolean activa, boolean base) {
    public String etiqueta() { return codigo + " – " + nombre; }
}
