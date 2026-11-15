package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public final class LibroDiarioDto {
    private LibroDiarioDto() {}

    public record Filtro(LocalDate desde, LocalDate hasta, Tipo tipo, Origen origen, String buscar) {}
    public record Movimiento(String codigo, String cuenta, String descripcion, BigDecimal debito, BigDecimal credito) {}
    public record Asiento(UUID id, String numero, LocalDate fecha, Tipo tipo, Origen origen, Estado estado,
                          String concepto, String referencia, String documento, String moneda,
                          List<Movimiento> movimientos, Total total) {}
    public record TotalMoneda(String moneda, long asientos, Total total) {}
    public record Resultado(List<Asiento> asientos, List<TotalMoneda> totales, long totalAsientos, int pagina, int tamano) {}
    public record Reporte(Filtro filtro, List<Asiento> asientos, List<TotalMoneda> totales,
                          DepreciacionDto.Empresa emisor, ZonedDateTime generado) {}
}
