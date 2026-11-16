package com.citacloud.springboot.contacloud.app.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public final class CuentaContableDto {
    private CuentaContableDto() {}

    public enum Tipo {
        AGRUPADORA("Agrupadora"), MOVIMIENTO("Movimiento");
        private final String etiqueta;
        Tipo(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public enum Clasificacion {
        ACTIVO("Activo"), PASIVO("Pasivo"), PATRIMONIO("Patrimonio"), INGRESOS("Ingresos"), COSTOS("Costos"), GASTOS("Gastos");
        private final String etiqueta;
        Clasificacion(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
        public Naturaleza sugerida() {
            return switch (this) {
                case ACTIVO, COSTOS, GASTOS -> Naturaleza.DEUDORA;
                case PASIVO, PATRIMONIO, INGRESOS -> Naturaleza.ACREEDORA;
            };
        }
    }

    public enum Naturaleza {
        DEUDORA("Deudora"), ACREEDORA("Acreedora");
        private final String etiqueta;
        Naturaleza(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public record Filtro(Tipo tipo, Naturaleza naturaleza, Boolean activo, String buscar) {
        public boolean restringido() {
            return tipo != null || naturaleza != null || activo != null || buscar != null && !buscar.isBlank();
        }
    }
    public record Cuenta(UUID id, UUID padre, String codigo, String nombre, Tipo tipo, Clasificacion clasificacion,
                         Naturaleza naturaleza, boolean activo, String descripcion, long version, boolean hijos, boolean estructural,
                         CentroCostoDto.Uso usoCentroCosto) {
        public Cuenta(UUID id,UUID padre,String codigo,String nombre,Tipo tipo,Clasificacion clasificacion,
                      Naturaleza naturaleza,boolean activo,String descripcion,long version,boolean hijos,boolean estructural) {
            this(id,padre,codigo,nombre,tipo,clasificacion,naturaleza,activo,descripcion,version,hijos,estructural,
                tipo==Tipo.MOVIMIENTO?CentroCostoDto.Uso.OPCIONAL:CentroCostoDto.Uso.NO_PERMITIDO);
        }
        public String etiqueta() { return codigo + " — " + nombre; }
        public boolean permiteMovimientos() { return tipo == Tipo.MOVIMIENTO; }
    }
    public record Entrada(UUID padre, String nombre, Tipo tipo, Naturaleza naturaleza, String descripcion,CentroCostoDto.Uso usoCentroCosto) {
        public Entrada(UUID padre,String nombre,Tipo tipo,Naturaleza naturaleza,String descripcion) {
            this(padre,nombre,tipo,naturaleza,descripcion,null);
        }
    }
    public record Uso(String modulo, String concepto, boolean historico, boolean activo) {}
    public record Detalle(Cuenta cuenta, Cuenta padre, int nivel, List<Cuenta> hijos, List<Uso> usos) {
        public boolean movimientos() { return usos.stream().anyMatch(Uso::historico); }
        public boolean estructuraProtegida() { return cuenta.estructural() || movimientos() || usos.stream().anyMatch(Uso::activo); }
    }
    public record Linea(Cuenta cuenta, int nivel) {}
    public record Reporte(DepreciacionDto.Empresa empresa, ZonedDateTime generado, Filtro filtro, List<Linea> cuentas) {}
}
