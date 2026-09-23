package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record UsuarioEmpresaClaveDto(UUID usuarioId, String usuario, String nombreCompleto,
                                     boolean activo) {}
