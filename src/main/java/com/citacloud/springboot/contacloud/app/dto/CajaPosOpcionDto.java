package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record CajaPosOpcionDto(UUID id,String nombre,UUID sucursalId,String sucursal,String monedaCodigo){
    @Override public String toString(){return sucursal+" · "+nombre+" · "+monedaCodigo;}
}
