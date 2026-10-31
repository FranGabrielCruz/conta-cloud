package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CrearAgenteImpresionInput(@NotBlank @Size(max=150) String nombre,@NotNull UUID sucursalId){}
