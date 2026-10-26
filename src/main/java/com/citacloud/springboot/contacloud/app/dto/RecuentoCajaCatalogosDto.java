package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.UUID;

public record RecuentoCajaCatalogosDto(List<SucursalOpcion> sucursales,List<CajaOpcion> cajas,List<UsuarioOpcion> usuarios) {
    public record SucursalOpcion(UUID id,String nombre) {}
    public record CajaOpcion(UUID id,UUID sucursalId,String nombre) {}
    public record UsuarioOpcion(UUID id,String nombre) {}
}
