package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MonedaDto(UUID id,
    @NotBlank @Pattern(regexp = "[A-Z0-9-]{3,12}") String codigoIso,
    @NotBlank @Size(max = 80) String nombre,
    @NotBlank @Size(max = 10) String simbolo,
    short decimales,
    boolean monedaBase,
    boolean activo) {}
