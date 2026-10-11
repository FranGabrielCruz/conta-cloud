package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record ContactoClienteDto(UUID id,String nombre,String cargo,String telefono,String correo,boolean principal,
    boolean activo,long version) {}
