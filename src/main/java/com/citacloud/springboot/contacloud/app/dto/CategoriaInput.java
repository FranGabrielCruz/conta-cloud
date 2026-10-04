package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaInput(
    @NotBlank @Size(max=120) String nombre,
    @Size(max=500) String descripcion,
    boolean activo,
    Long version) {}
