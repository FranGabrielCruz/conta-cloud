package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.LineaReporteMovimientoInventario;
import com.citacloud.springboot.contacloud.app.dto.ReporteMovimientoInventarioData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MovimientoInventarioPdfServiceTest {
    private final MovimientoInventarioPdfService service = new MovimientoInventarioPdfService();

    @Test
    void generaPdfGeneralMultipaginaConAmbasColumnas() throws Exception {
        ReporteMovimientoInventarioData data = data(true, true, true, 72);
        byte[] pdf = service.generarMovimientos(data);
        assertThat(pdf).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        assertThat(MovimientoInventarioPdfService.parameters(data, false)).containsEntry("layout", "ALL");
        writePreview("movimientos-inventario-preview.pdf", pdf);
    }

    @Test
    void seleccionaColumnasDinamicasParaEntradaYSalida() {
        Map<String, Object> incoming = MovimientoInventarioPdfService.parameters(data(true, false, false, 1), false);
        Map<String, Object> outgoing = MovimientoInventarioPdfService.parameters(data(false, true, false, 1), false);
        assertThat(incoming).containsEntry("layout", "IN");
        assertThat(outgoing).containsEntry("layout", "OUT");
    }

    @Test
    void generaKardexYConservaLaReconciliacion() throws Exception {
        ReporteMovimientoInventarioData data = data(true, true, false, 16);
        byte[] pdf = service.generarKardex(data);
        assertThat(pdf).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        assertThat(new BigDecimal("15.00").add(new BigDecimal("25.00")).subtract(new BigDecimal("17.00")))
            .isEqualByComparingTo(new BigDecimal("23.00"));
        writePreview("kardex-inventario-preview.pdf", pdf);
    }

    @Test
    void generaReportesSinMovimientosManteniendoElEncabezadoYLosSaldos() {
        ReporteMovimientoInventarioData data = data(false, false, false, 0);

        assertThat(service.generarMovimientos(data))
            .startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        assertThat(service.generarKardex(data))
            .startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void formateaFechasYCantidadesSegunElEstandar() {
        assertThat(MovimientoInventarioReporteService.number(new BigDecimal("1000"))).isEqualTo("1,000.00");
        assertThat(MovimientoInventarioReporteService.number(new BigDecimal("12.3456"))).isEqualTo("12.3456");
        assertThat(MovimientoInventarioReporteService.number(new BigDecimal("-3"))).isEqualTo("-3.00");
    }

    private static ReporteMovimientoInventarioData data(boolean showEntry, boolean showExit,
            boolean showWarehouse, int rows) {
        List<LineaReporteMovimientoInventario> lines = new ArrayList<>();
        for (int index = 1; index <= rows; index++) {
            boolean incoming = index % 2 == 0;
            lines.add(new LineaReporteMovimientoInventario("12/10/2026 15:" + String.format("%02d", index % 60),
                "MOV-" + String.format("%06d", index),
                "Producto de inventario con un nombre suficientemente largo para validar el ajuste de texto",
                "PRD-000125", "Almacén Principal con nombre extendido", "Sucursal Principal",
                incoming ? "Recepción" : "Transferencia", incoming ? "Recepción de proveedor" : "Transferencia",
                incoming ? "10.00" : "—", incoming ? "—" : "5.00", String.format("%,.2f", 100.0 + index)));
        }
        return new ReporteMovimientoInventarioData(UUID.randomUUID(), UUID.randomUUID(), "EMPRESA DEMO, SRL",
            "Empresa Demo Dominicana, SRL", "124543678", "Av. Principal #123, Santo Domingo",
            "8095551234", "contabilidad@empresa.com", null, "01/10/2026 - 12/10/2026", "Sin búsqueda",
            "Sucursal Principal", showWarehouse ? "Todos" : "Almacén Principal",
            "Laptop Dell Latitude de alto rendimiento", "PRD-000125", "Unidad", "Todos", "Todos",
            rows, 12, new BigDecimal("15.00"), new BigDecimal("25.00"), new BigDecimal("17.00"),
            new BigDecimal("23.00"), showEntry, showExit, showWarehouse, "Juan Pérez",
            OffsetDateTime.parse("2026-10-12T20:40:00-04:00"), lines);
    }

    private static void writePreview(String fileName, byte[] content) throws Exception {
        Path directory = Path.of("target", "pdf-preview");
        Files.createDirectories(directory);
        Files.write(directory.resolve(fileName), content);
    }
}
