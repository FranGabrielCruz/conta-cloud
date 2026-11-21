package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CierreContableDto {
    private CierreContableDto() {}
    public enum Estado { ABIERTO, CERRADO, BLOQUEADO }
    public enum Tipo {
        PERIODO("Cierre de período"), EJERCICIO("Cierre de ejercicio");
        private final String etiqueta;
        Tipo(String etiqueta) { this.etiqueta=etiqueta; }
        public String etiqueta() { return etiqueta; }
    }
    public record Periodo(UUID id,String nombre,LocalDate desde,LocalDate hasta,Estado estado,OffsetDateTime cerradoEn) {}
    public record Filtro(UUID periodo,Estado estado,String buscar) {}
    public record Lista(List<Periodo> periodos,long total) {}
    public record Cuenta(UUID id,String codigo,String nombre) {
        public String etiqueta() { return codigo+" — "+nombre; }
    }
    public record Resultado(BigDecimal ingresos,BigDecimal costos,BigDecimal gastos) {
        public BigDecimal neto() { return ingresos.subtract(costos).subtract(gastos); }
        public String tipo() { return switch(neto().signum()) {case 1->"Utilidad";case -1->"Pérdida";default->"Sin resultado";}; }
    }
    public record Validacion(String nombre,boolean correcta,String detalle,String valor) {
        public Validacion(String nombre,boolean correcta,String detalle) { this(nombre,correcta,detalle,null); }
    }
    public record PeriodosAnteriores(long existentes,long pendientes) {}
    public enum EstadoCuentaResultado { NO_CONFIGURADA, INVALIDA, VALIDA }
    public record Evaluacion(Tipo tipo,LocalDate inicioEjercicio,String moneda,Cuenta cuentaResultado,
                             Resultado resultado,List<Validacion> validaciones,LocalDate inicioFiscal,Resultado resultadoComplementario,
                             PeriodosAnteriores periodosAnteriores,EstadoCuentaResultado estadoCuentaResultado) {
        public Evaluacion(Tipo tipo,LocalDate inicioEjercicio,String moneda,Cuenta cuentaResultado,Resultado resultado,
                          List<Validacion> validaciones,LocalDate inicioFiscal,Resultado resultadoComplementario) {
            this(tipo,inicioEjercicio,moneda,cuentaResultado,resultado,validaciones,inicioFiscal,resultadoComplementario,null,
                cuentaResultado==null?EstadoCuentaResultado.NO_CONFIGURADA:EstadoCuentaResultado.VALIDA);
        }
        public Evaluacion(Tipo tipo,LocalDate inicioEjercicio,String moneda,Cuenta cuentaResultado,Resultado resultado,
                          List<Validacion> validaciones) {
            this(tipo,inicioEjercicio,moneda,cuentaResultado,resultado,validaciones,inicioEjercicio,null);
        }
        public boolean permiteCerrar() { return validaciones.stream().allMatch(Validacion::correcta); }
        public Resultado resultadoPeriodo() { return tipo==Tipo.PERIODO?resultado:resultadoComplementario; }
        public Resultado resultadoEjercicio() { return tipo==Tipo.EJERCICIO?resultado:resultadoComplementario; }
    }
    public record Resumen(String periodo,LocalDate desde,LocalDate hasta,Evaluacion evaluacion,String usuario,
                          String observacion,DepreciacionDto.Empresa emisor,String zonaHoraria) {}
    public record Reapertura(String motivo,String usuario,OffsetDateTime fecha,UUID reversaId,String reversaNumero) {}
    public record Cierre(UUID id,UUID periodoId,OffsetDateTime fecha,UUID asientoId,String asientoNumero,
                         Resumen resumen,Reapertura reapertura) {}
    public record Detalle(Periodo periodo,Evaluacion evaluacion,List<Cierre> historial,boolean reabrible) {}
    public record Solicitud(String observacion) {}
    public record SolicitudReapertura(UUID cierreId,String motivo) {}
}
