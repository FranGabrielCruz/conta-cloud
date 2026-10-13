package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.TransferenciaInventarioReporteData;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TransferenciaInventarioPdfServiceTest {
    private final TransferenciaInventarioPdfService service = new TransferenciaInventarioPdfService();

    @Test void generaDocumentoRecibidoConIdentidadTrazabilidadYControlFisico() throws Exception {
        byte[] pdf = service.generar(data(3, true), ZoneId.of("America/Santo_Domingo"));
        assertThat(pdf).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        assertThat(text(pdf)).contains("TRANSFERENCIA DE INVENTARIO", "TRF-000126", "RECIBIDA",
            "Almacén Principal", "Sucursal Santiago", "Juan Pérez", "María López",
            "Entregado por", "Transportado por", "Recibido por").doesNotContainIgnoringCase("ContaCloud");
    }

    @Test void generaMultipaginaYRepiteLaIdentidadDelDocumento() throws Exception {
        byte[] pdf = service.generar(data(75, false), ZoneId.of("America/Santo_Domingo"));
        Path directory = Path.of("target", "pdf-preview"); Files.createDirectories(directory);
        Files.write(directory.resolve("transferencia-inventario-preview.pdf"), pdf);
        PdfReader reader = new PdfReader(pdf); assertThat(reader.getNumberOfPages()).isGreaterThan(1);
        PdfTextExtractor extractor = new PdfTextExtractor(reader); List<String> pages = new ArrayList<>();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) pages.add(extractor.getTextFromPage(page));
        reader.close();
        assertThat(pages).allMatch(page -> page.contains("Página"));
        assertThat(pages.subList(1, pages.size())).allMatch(page -> page.contains("TRANSFERENCIA DE INVENTARIO") && page.contains("TRF-000126"));
    }

    @Test void formateaRncTelefonoYEstado() {
        Map<String,Object> parameters = TransferenciaInventarioPdfService.parameters(data(1, true), ZoneId.of("America/Santo_Domingo"));
        assertThat(parameters).containsEntry("companyTaxIdentification", "124-54367-8")
            .containsEntry("companyPhone", "809-555-1234").containsEntry("status", "RECIBIDA")
            .containsEntry("hasReceipt", true);
    }

    private static TransferenciaInventarioReporteData data(int count, boolean received) {
        var lines = IntStream.rangeClosed(1, count).mapToObj(i -> new TransferenciaInventarioReporteData.Linea(
            i, "PRD-%06d".formatted(i), "Producto con nombre largo para validar el ajuste " + i,
            "Descripción documental del producto " + i, "Unidad", "1,000.00",
            received ? "999.00" : "—", received ? "-1.00" : "—", received ? "Conteo físico" : "—")).toList();
        return new TransferenciaInventarioReporteData("EMPRESA DEMO, SRL", "Empresa Demo Dominicana, SRL",
            "124543678", "Av. Principal #123, Santo Domingo", "8095551234", "correo@empresa.com", null,
            "TRF-000126", received ? "Recibida" : "En tránsito", LocalDate.of(2026,10,13), "Con tránsito",
            "Reposición Santiago", "Mercancía para reposición de inventario.", "Almacén Principal",
            "Sucursal Principal", "Almacén Principal", "Sucursal Santiago",
            OffsetDateTime.parse("2026-10-13T14:35:00-04:00"), "Juan Pérez",
            received ? OffsetDateTime.parse("2026-10-13T17:20:00-04:00") : null,
            received ? "María López" : null, "Usuario Administrador",
            OffsetDateTime.parse("2026-10-13T17:22:00-04:00"), lines);
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf); PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder result = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) result.append(extractor.getTextFromPage(page)).append('\n');
        reader.close(); return result.toString();
    }
}
