package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CajaInput(
    @NotBlank @Size(max = 120) String nombre,
    @NotNull UUID sucursalId,
    @NotNull UUID monedaId,
    @Size(max = 500) String descripcion,
    boolean activa) {}

