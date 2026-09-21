package com.citacloud.springboot.contacloud.app.dto;
import java.util.Set;
import java.util.UUID;
public record RolInputDto(String nombre,String descripcion,boolean activo,Set<UUID> permisoIds) {}
