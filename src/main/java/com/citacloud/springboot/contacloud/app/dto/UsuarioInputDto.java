package com.citacloud.springboot.contacloud.app.dto;
import java.util.Set;
import java.util.UUID;
public record UsuarioInputDto(String usuario,String nombre,String apellido,String correo,String telefono,
                              UUID rolId,boolean todasSucursales,Set<UUID> sucursalIds,boolean activo) {}
