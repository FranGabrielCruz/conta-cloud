package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Estado;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Origen;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Naturaleza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public final class LibroMayorDto {
    private LibroMayorDto() {}

    public record Filtro(UUID accountId,
                         @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
                         @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate, Tipo type, Origen origin, String search) {}
    public record Cuenta(UUID id, String codigo, String nombre, CuentaContableDto.Tipo tipo, Naturaleza naturaleza, boolean activo) {
        public String etiqueta() { return codigo + " — " + nombre; }
        public boolean agrupadora() { return tipo == CuentaContableDto.Tipo.AGRUPADORA; }
    }
    public record Saldo(BigDecimal importe, Lado lado) {
        public static Saldo de(BigDecimal neto) {
            return new Saldo(neto.abs(), switch (neto.signum()) {
                case -1 -> Lado.CR;
                case 1 -> Lado.DB;
                default -> Lado.CERO;
            });
        }
    }
    public enum Lado { DB, CR, CERO }
    public record Movimiento(UUID asientoId, String numero, LocalDate fecha, String concepto, String referencia,
                             String documento, String descripcion, Tipo tipo, Origen origen, Estado estado,
                             UUID cuentaId, String codigo, String cuenta, String moneda,
                             BigDecimal debito, BigDecimal credito, Saldo saldo) {}
    public record Resumen(String moneda, Saldo inicial, BigDecimal debitos, BigDecimal creditos,
                          Saldo variacion, Saldo cierre, long movimientos) {}
    public record Resultado(Cuenta cuenta, LocalDate desde, LocalDate hasta, List<Movimiento> movimientos,
                            List<Resumen> resumen, long total, int pagina, int tamano) {}
    public record Reporte(Filtro filtro, Resultado resultado, DepreciacionDto.Empresa emisor, ZonedDateTime generado) {}
}
