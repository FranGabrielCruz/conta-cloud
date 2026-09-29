package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoCondicionPago;
import java.util.UUID;

public record CondicionPagoDto(UUID id, String nombre, TipoCondicionPago tipo,
                               int dias, String descripcion, boolean activo) {}
