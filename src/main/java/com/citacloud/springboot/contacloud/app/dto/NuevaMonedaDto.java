package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NuevaMonedaDto(
    @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String codigo,
    @NotBlank @Size(max = 80) String nombre,
    @NotBlank @Size(max = 10) String simbolo,
    @Min(0) @Max(6) short decimales) {
}
