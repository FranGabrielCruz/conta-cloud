package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ConfiguracionEmpresaDto(
    @NotBlank(message = "El nombre comercial es obligatorio") @Size(max = 150) String nombreComercial,
    @Size(max = 150) String razonSocial,
    @Size(max = 30) String rnc,
    @Size(max = 40) String telefono,
    @Email(message = "Introduzca un correo electrónico válido.") @Size(max = 180) String correo,
    UUID monedaBaseId,
    @Size(max = 255) String direccion) {}
