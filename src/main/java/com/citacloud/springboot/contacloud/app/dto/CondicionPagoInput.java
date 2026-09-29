package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoCondicionPago;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CondicionPagoInput(
    @NotBlank @Size(max = 100) String nombre,
    @NotNull TipoCondicionPago tipo,
    @Min(0) @Max(3650) Integer dias,
    @Size(max = 500) String descripcion,
    boolean activo) {}
