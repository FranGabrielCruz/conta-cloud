package com.citacloud.springboot.contacloud.app.dto;
import java.util.Set;
import java.util.UUID;
public record RolDto(UUID id,String nombre,String descripcion,long usuarios,boolean activo,boolean protegido,Set<UUID> permisoIds) {}
