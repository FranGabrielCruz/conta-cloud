package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record PermisoDto(UUID id,String codigo,String nombre,String descripcion,String modulo,String recurso,String accion) {}
