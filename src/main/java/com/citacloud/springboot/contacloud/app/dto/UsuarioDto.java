package com.citacloud.springboot.contacloud.app.dto;
import java.util.Set;
import java.util.UUID;
public record UsuarioDto(UUID accesoId,UUID usuarioId,String usuario,String nombre,String apellido,String correo,
                         String telefono,UUID rolId,String rolNombre,boolean todasSucursales,
                         Set<UUID> sucursalIds,String sucursalesResumen,boolean activo) {}
