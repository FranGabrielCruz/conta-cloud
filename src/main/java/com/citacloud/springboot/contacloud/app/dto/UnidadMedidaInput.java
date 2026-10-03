package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnidadMedidaInput(
    @NotBlank @Size(max=80) String nombre,
    @NotBlank @Size(max=20) String abreviatura,
    @Size(max=500) String descripcion,
    boolean activo,
    Long version) {}
