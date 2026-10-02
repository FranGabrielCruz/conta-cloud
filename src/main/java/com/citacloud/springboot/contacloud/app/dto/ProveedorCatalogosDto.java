package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.UUID;

public record ProveedorCatalogosDto(List<CondicionOpcion> condiciones,List<MonedaOpcion> monedas){
    public record CondicionOpcion(UUID id,String nombre){}
    public record MonedaOpcion(UUID id,String codigo,String nombre){}
}
