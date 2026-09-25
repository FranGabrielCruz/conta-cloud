package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record EmpresaDetalleDto(UUID id, String codigo, String nombreComercial,
                                String razonSocial, String identificacionFiscal,
                                String telefono, String correo, String direccion,
                                boolean activo, boolean limiteUsuariosHabilitado,
                                Integer limiteUsuarios) {}
