package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class CarteraCobrarDto {
    private CarteraCobrarDto() {}

    public enum Estado {
        ACTIVA("Cartera activa"), TODOS("Todos"), PENDIENTE("Pendiente"), PARCIAL("Parcial"),
        VENCIDO("Vencido"), PAGADO("Pagado"), ANULADO("Anulado");

        private final String etiqueta;
        Estado(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public record Filtro(UUID cliente, Estado estado, LocalDate venceDesde, LocalDate venceHasta, String buscar) {}
    public record Cliente(UUID id, String nombre) {}
    public record Cuenta(UUID id, String documento, LocalDate fecha, LocalDate vencimiento, Cliente cliente,
            String identificacion, String moneda, String sucursal, String condicion, String referencia,
            BigDecimal total, BigDecimal deudaOriginal, BigDecimal aplicado, BigDecimal saldo, Estado estado) {}
    public record Aplicacion(String documento, String tipo, LocalDateTime fecha, BigDecimal importe, boolean revertida) {}
    public record Detalle(Cuenta cuenta, BigDecimal cobros, BigDecimal creditos, BigDecimal otrosAplicados,
            List<Aplicacion> aplicaciones) {}
    public record Moneda(String codigo, BigDecimal saldo, BigDecimal corriente, BigDecimal vencido, BigDecimal totalDocumentos) {}
    public record Indicadores(List<Moneda> monedas, long clientes) {}
    public record ClienteCartera(Cliente cliente, String moneda, BigDecimal corriente, BigDecimal vencido, BigDecimal saldo) {}
    public record Pantalla(Page<Cuenta> documentos, Page<ClienteCartera> clientes, Indicadores indicadores) {}
    public record Edad(Cliente cliente, String moneda, BigDecimal corriente, BigDecimal hasta30,
            BigDecimal hasta60, BigDecimal hasta90, BigDecimal mas90, BigDecimal total) {}
}
