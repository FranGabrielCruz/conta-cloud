package com.citacloud.springboot.contacloud.app.dto;
import jakarta.validation.constraints.*;
import java.util.*;
public record EditarAgenteImpresionInput(@NotBlank @Size(max=150) String nombre,@NotNull UUID sucursalId,
    @NotEmpty List<@NotNull UUID> cajaIds,boolean mostrarPdfAlImprimir,long version) {}
