package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record UnidadMedidaDto(UUID id,String nombre,String abreviatura,String descripcion,boolean activo,long version) {}
