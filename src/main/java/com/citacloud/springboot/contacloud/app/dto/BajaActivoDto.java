package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class BajaActivoDto {
    private BajaActivoDto() {}

    public enum Estado {
        BORRADOR("Borrador"), CONFIRMADA("Confirmada");
        private final String etiqueta;
        Estado(String etiqueta) { this.etiqueta=etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public record Tipo(String codigo,String nombre) {}
    public record Factura(UUID id,String codigo) {}
    public record Entrada(UUID activo,String tipo,LocalDate fecha,String motivo,BigDecimal obtenido,
                          String referencia,UUID factura,String observacion) {}
    public record Filtro(LocalDate desde,LocalDate hasta,String tipo,Estado estado,String buscar) {}
    public record Activo(UUID id,UUID sucursal,UUID monedaId,String moneda,String codigo,String nombre,String categoria,
                         String marca,String modelo,String serie,String estado,LocalDate adquisicion,LocalDate inicio,
                         LocalDate ultima,BigDecimal costo,BigDecimal depreciacion,BigDecimal ajustes,
                         boolean retirado,boolean movimientosPosteriores,boolean mantenimientoPendiente,long version) {
        public String etiqueta() { return codigo+" — "+nombre; }
        public BigDecimal libros() { return costo.subtract(depreciacion).subtract(ajustes); }
    }
    public record Resultado(BigDecimal ganancia,BigDecimal perdida) {}
    public record Snapshot(Activo activo,String tipo,String factura,DepreciacionDto.Empresa empresa,String confirmadoPor) {}
    public record Detalle(UUID id,UUID sucursal,String codigo,Estado estado,long version,Entrada datos,String tipo,String factura,
                          Activo activo,Snapshot snapshot,OffsetDateTime confirmadoEn,UUID confirmadoPor,UUID solicitud) {}
    public record Fila(UUID id,String codigo,String activo,LocalDate fecha,String tipo,BigDecimal libros,String moneda,Estado estado) {}
    public record Total(String moneda,long cantidad,BigDecimal costo,BigDecimal libros) {}
    public record Revision(Detalle detalle,Activo activo,Resultado resultado) {}
}
