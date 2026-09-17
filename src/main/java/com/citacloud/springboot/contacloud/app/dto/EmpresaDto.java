package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record EmpresaDto(
    UUID id,
    @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String codigo,
    @NotBlank @Size(max = 150) String nombre,
    @Size(max = 30) String identificacionFiscal,
    @Pattern(regexp = "[A-Z]{2}") String paisCodigo,
    @NotBlank @Size(max = 60) String zonaHoraria,
    boolean activo) {}
