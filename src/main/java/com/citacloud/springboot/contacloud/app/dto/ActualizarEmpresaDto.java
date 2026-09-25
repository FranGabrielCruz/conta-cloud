package com.citacloud.springboot.contacloud.app.dto;

public record ActualizarEmpresaDto(String nombreComercial, String razonSocial,
                                   String identificacionFiscal, String telefono,
                                   String correo, String direccion,
                                   boolean limiteUsuariosHabilitado,
                                   Integer limiteUsuarios) {}
