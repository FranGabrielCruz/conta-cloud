package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record CajaDto(UUID id, String nombre, UUID sucursalId, String sucursalNombre,
                      UUID monedaId, String monedaCodigo, String monedaNombre,
                      String descripcion, boolean activa) {}

