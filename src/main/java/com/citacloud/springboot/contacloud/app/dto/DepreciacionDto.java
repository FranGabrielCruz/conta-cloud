package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class DepreciacionDto {
    private DepreciacionDto() {}

    public record Empresa(String nombre,String rnc,String direccion,String telefono,String pais,String logo) {}
    public record Candidato(UUID id,UUID sucursal,String codigo,String nombre,String moneda,BigDecimal costo,
                            BigDecimal residual,Integer vida,String metodo,LocalDate inicio,
                            BigDecimal anterior,BigDecimal ajustes, boolean movimientoPosterior,BigDecimal anteriorActual) {}
    public record Linea(UUID activo,UUID sucursal,String codigo,String nombre,String moneda,BigDecimal costo,
                        BigDecimal base,BigDecimal anterior,BigDecimal importe,BigDecimal libros) {
        public BigDecimal acumulada() { return anterior.add(importe); }
    }
    public record Total(String moneda,long activos,BigDecimal costo,BigDecimal anterior,BigDecimal importe,
                        BigDecimal acumulada,BigDecimal libros) {}
    public record VistaPrevia(YearMonth periodo,LocalDate fecha,List<Linea> lineas,List<Total> totales,
                             List<String> incidencias,String revision,UUID solicitud) {}
    public record Proceso(UUID id,long numero,YearMonth periodo,LocalDate fecha,OffsetDateTime procesadoEn,
                          UUID usuario,String nombreUsuario,UUID solicitud,Empresa empresa) {
        public String codigo() { return String.format(java.util.Locale.ROOT,"DEP-%06d",numero); }
    }
    public record Fila(UUID id,YearMonth periodo,LocalDate fecha,long activos,String moneda,BigDecimal importe) {}
    public record Detalle(Proceso proceso,List<Linea> lineas,List<Total> totales) {}
    public record Indicadores(YearMonth ultimo,long activos,List<Saldo> acumulados) {}
    public record Saldo(String moneda,BigDecimal importe) {}
}
