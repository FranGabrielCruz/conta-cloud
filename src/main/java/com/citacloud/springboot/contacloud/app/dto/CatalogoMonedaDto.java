package com.citacloud.springboot.contacloud.app.dto;

public record CatalogoMonedaDto(String codigoIso, String nombre, String simbolo, short decimales) {
    @Override public String toString() { return codigoIso + " - " + nombre; }
}
