package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Naturaleza;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Tipo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public final class BalanzaDto {
    private BalanzaDto() {}

    public enum Mostrar {
        TODAS("Todas las cuentas"), MOVIMIENTOS("Con movimientos"), SALDO("Con saldo"),
        MOVIMIENTOS_O_SALDO("Con movimientos o saldo");

        private final String etiqueta;
        Mostrar(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public record Filtro(@DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
                         @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
                         Integer level, Mostrar display, String search) {}

    public record Importes(String moneda, BigDecimal inicialDebito, BigDecimal inicialCredito,
                           BigDecimal debito, BigDecimal credito, BigDecimal finalDebito, BigDecimal finalCredito) {
        public static Importes cuenta(String moneda, BigDecimal inicial, BigDecimal debe, BigDecimal haber) {
            var cierre=inicial.add(debe).subtract(haber);
            return new Importes(moneda,positivo(inicial),positivo(inicial.negate()),debe,haber,
                positivo(cierre),positivo(cierre.negate()));
        }
        private static BigDecimal positivo(BigDecimal valor) { return valor.max(BigDecimal.ZERO); }
        public BigDecimal diferenciaInicial() { return inicialDebito.subtract(inicialCredito); }
        public BigDecimal diferenciaMovimientos() { return debito.subtract(credito); }
        public BigDecimal diferenciaFinal() { return finalDebito.subtract(finalCredito); }
        public List<BigDecimal> columnas() { return List.of(inicialDebito,inicialCredito,debito,credito,finalDebito,finalCredito); }
    }

    public record Cuenta(UUID id, UUID padreId, String padre, String codigo, String nombre,
                         Tipo tipo, Naturaleza naturaleza, boolean activa, int nivel, List<Importes> importes) {}
    public record Totales(List<Importes> filtrados,List<Importes> generales) {}
    public record Inicial(YearMonth mes,int niveles) {}
    public record Resultado(Filtro filtro,List<Cuenta> cuentas,Totales totales,long total,int pagina,int tamano) {}
    public record Reporte(Resultado resultado,DepreciacionDto.Empresa emisor,ZonedDateTime generado) {}
}
