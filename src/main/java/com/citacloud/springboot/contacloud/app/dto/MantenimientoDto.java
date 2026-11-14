package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MantenimientoDto {
    private MantenimientoDto() {}

    public enum Estado {
        PROGRAMADO("Programado"), EN_PROCESO("En proceso"), COMPLETADO("Completado"), CANCELADO("Cancelado");
        private final String etiqueta;
        Estado(String etiqueta) { this.etiqueta=etiqueta; }
        public String etiqueta() { return etiqueta; }
        public boolean pendiente() { return this==PROGRAMADO || this==EN_PROCESO; }
    }

    public enum Prioridad {
        BAJA("Baja"), MEDIA("Media"), ALTA("Alta");
        private final String etiqueta;
        Prioridad(String etiqueta) { this.etiqueta=etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public record Tipo(String codigo,String nombre) {}
    public record Activo(UUID id,UUID sucursal,String codigo,String nombre,String marca,String modelo,String serie,
                         String condicion,boolean baja,String estadoPatrimonial) {
        public String etiqueta() { return codigo+" — "+nombre; }
    }
    public record Catalogos(List<Tipo> tipos,List<ActivoFijoDto.Moneda> monedas,List<ActivoFijoDto.Opcion> responsables,
                            List<ActivoFijoDto.Opcion> proveedores) {}
    public record Entrada(UUID activo,String tipo,Prioridad prioridad,LocalDate programada,LocalDate proxima,
                          UUID responsable,UUID proveedor,UUID moneda,BigDecimal estimado,String referencia,
                          String descripcion,String observacion) {}
    public record Completar(LocalDate realizada,BigDecimal costo,UUID proveedor,String trabajo,LocalDate proxima,String observacion) {}
    public record Filtro(LocalDate desde,LocalDate hasta,String tipo,Estado estado,boolean vencido,String buscar) {}
    public record Fila(UUID id,String codigo,String activoCodigo,String activoNombre,String tipo,LocalDate fecha,
                       BigDecimal costo,String moneda,Estado estado,boolean vencido,String descripcion) {}
    public record Saldo(String moneda,BigDecimal costo) {}
    public record Indicadores(long programados,long proximos,long vencidos,List<Saldo> costos) {}
    public record Snapshot(String activoCodigo,String activoNombre,String marca,String modelo,String serie,
                           String responsable,String proveedor,String moneda,String tipo,DepreciacionDto.Empresa empresa,
                           String estadoPatrimonial) {}
    public record Detalle(UUID id,UUID sucursal,String codigo,long version,Estado estado,Entrada datos,Activo activo,
                          String tipo,String moneda,String responsable,String proveedor,boolean fueraServicio,
                          String condicionAnterior,OffsetDateTime inicio,OffsetDateTime cierre,LocalDate realizada,
                          BigDecimal real,String trabajo,String motivo,Snapshot snapshot) {
        public BigDecimal variacion() { return real==null?null:real.subtract(datos.estimado()); }
    }
}
