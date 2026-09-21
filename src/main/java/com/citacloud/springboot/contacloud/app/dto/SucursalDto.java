package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SucursalDto(UUID id,
    @NotBlank @Size(max = 150) String nombre,
    @Size(max = 255) String direccion,
    @Size(max = 40) String telefono,
    boolean principal,
    boolean activo) {}
