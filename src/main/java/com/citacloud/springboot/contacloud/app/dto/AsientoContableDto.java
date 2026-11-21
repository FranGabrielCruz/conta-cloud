package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AsientoContableDto {
    private AsientoContableDto() {}
    public enum Tipo {
        DIARIO("Diario general"), APERTURA("Apertura"), AJUSTE("Ajuste"), CIERRE("Cierre"),
        REVERSION("Reversión"), AUTOMATICO("Automático");
        private final String etiqueta;
        Tipo(String etiqueta) { this.etiqueta=etiqueta; }
        public String etiqueta() { return etiqueta; }
    }
    public enum Origen {
        MANUAL("Manual"), VENTAS("Ventas"), COMPRAS("Compras"), INVENTARIO("Inventario"), CXC("Cuentas por cobrar"),
        CXP("Cuentas por pagar"), ACTIVOS_FIJOS("Activos fijos"), FINANZAS("Finanzas"), SISTEMA("Sistema");
        private final String etiqueta;
        Origen(String etiqueta) { this.etiqueta=etiqueta; }
        public String etiqueta() { return etiqueta; }
    }
    public enum Estado {
        BORRADOR("Borrador"), CONTABILIZADO("Contabilizado"), REVERSADO("Reversado");
        private final String etiqueta;
        Estado(String etiqueta) { this.etiqueta=etiqueta; }
        public String etiqueta() { return etiqueta; }
    }
    public record Moneda(UUID id,String codigo) {}
    public record Cuenta(UUID id,String codigo,String nombre,boolean activa,boolean movimiento,CentroCostoDto.Uso usoCentroCosto) {
        public Cuenta(UUID id,String codigo,String nombre,boolean activa,boolean movimiento) {
            this(id,codigo,nombre,activa,movimiento,CentroCostoDto.Uso.OPCIONAL);
        }
        public String etiqueta() { return codigo+" — "+nombre; }
    }
    public record Movimiento(UUID cuenta,String descripcion,BigDecimal debito,BigDecimal credito,UUID centroCostoId) {
        public Movimiento(UUID cuenta,String descripcion,BigDecimal debito,BigDecimal credito) {
            this(cuenta,descripcion,debito,credito,null);
        }
    }
    public record Linea(Cuenta cuenta,String descripcion,BigDecimal debito,BigDecimal credito,CentroCostoDto.Referencia centroCosto) {
        public Linea(Cuenta cuenta,String descripcion,BigDecimal debito,BigDecimal credito) {
            this(cuenta,descripcion,debito,credito,null);
        }
    }
    public record Entrada(LocalDate fecha,Tipo tipo,UUID moneda,String concepto,String referencia,String observacion,List<Movimiento> lineas) {}
    public record Fuente(Origen origen,String tipo,UUID id,String numero,String evento) {}
    public record LineaConfigurada(String clave,String descripcion,BigDecimal debito,BigDecimal credito,UUID centroCostoId,UUID cuentaId) {
        public LineaConfigurada(String clave,String descripcion,BigDecimal debito,BigDecimal credito,UUID centroCostoId) {
            this(clave,descripcion,debito,credito,centroCostoId,null);
        }
        public LineaConfigurada(String clave,String descripcion,BigDecimal debito,BigDecimal credito) {
            this(clave,descripcion,debito,credito,null,null);
        }
    }
    public record Filtro(LocalDate desde,LocalDate hasta,Tipo tipo,Estado estado,Origen origen,String buscar) {}
    public record Total(BigDecimal debito,BigDecimal credito) {
        public BigDecimal diferencia() { return debito.subtract(credito); }
    }
    public record Fila(UUID id,String numero,LocalDate fecha,Tipo tipo,Origen origen,String concepto,String moneda,Estado estado,Total total) {}
    public record Detalle(Fila fila,Moneda moneda,String referencia,String observacion,List<Linea> lineas,Fuente fuente,
                          UUID reversaDe,String numeroOriginal,String numeroReversion,String motivo,long version,
                          OffsetDateTime creado,String creador,OffsetDateTime contabilizado,String contabilizador,
                          UUID contabilizacion,DepreciacionDto.Empresa emisor) {
        public boolean reversible() {
            return fila.estado()==Estado.CONTABILIZADO && fila.origen()==Origen.MANUAL && reversaDe==null;
        }
    }
    public record Reporte(Detalle detalle,DepreciacionDto.Empresa emisor) {}
}
