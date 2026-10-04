package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record CategoriaDto(UUID id,String nombre,String descripcion,boolean activo,long productosServicios,long version) {}
