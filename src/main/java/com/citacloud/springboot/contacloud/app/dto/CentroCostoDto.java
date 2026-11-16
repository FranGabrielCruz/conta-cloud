package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.UUID;

public final class CentroCostoDto {
    private CentroCostoDto() {}

    public enum Uso {
        NO_PERMITIDO("No permitido"), OPCIONAL("Opcional"), OBLIGATORIO("Obligatorio");
        private final String etiqueta;
        Uso(String etiqueta) { this.etiqueta=etiqueta; }
        public String etiqueta() { return etiqueta; }
    }
    public record Referencia(UUID id,String codigo,String nombre,boolean activo) {
        public String etiqueta() { return codigo+" — "+nombre; }
    }
    public record Centro(Referencia referencia,Referencia padre,String descripcion,long version) {}
    public record Entrada(String nombre,UUID padre,String descripcion) {}
    public record Filtro(Boolean activo,String buscar) {}
    public record Resultado(List<Centro> centros,long total) {}
}
