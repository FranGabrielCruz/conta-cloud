package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConteoFisicoReporteData;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConteoFisicoPdfServiceTest {
    private static final byte[] LOGO = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    private final ConteoFisicoPdfService service = new ConteoFisicoPdfService();

    @Test
    void diferenciaHojaVisibleDeHojaCiegaSinRevelarTeorico() throws Exception {
        byte[] visible = service.generar(data(true, false, null, 3), false);
        byte[] ciego = service.generar(data(false, false, null, 3), false);
        String visibleText = text(visible);
        String blindText = text(ciego);

        assertThat(visibleText)
                .contains("HOJA DE CONTEO FÍSICO", "CON-000001", "INFORMACIÓN DEL CONTEO")
                .contains("DETALLE DEL CONTEO", "TEÓRICO", "FÍSICO", "9,876.50")
                .contains("OBSERVACIONES DEL CONTEO", "Realizado por:", "Firma:");
        assertThat(blindText)
                .contains("HOJA DE CONTEO FÍSICO", "FÍSICO")
                .doesNotContain("TEÓRICO", "9,876.50");
    }

    @Test
    void generaResultadoConfirmadoConValoresDefinitivos() throws Exception {
        byte[] result = service.generar(data(true, true, LOGO, 3), true);
        assertThat(text(result))
                .contains("CONTEO FÍSICO DE INVENTARIO", "CONFIRMADO")
                .contains("TEÓRICO", "FÍSICO", "DIFERENCIA", "9,877.50", "+1.00")
                .contains("Confirmado por: Administrador");
    }

    @Test
    void generaMultipaginaConTodosLosProductosEncabezadosYPaginaTotal() throws Exception {
        byte[] pdf = service.generar(data(true, false, LOGO, 80), false);
        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(4);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder all = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            String pageText = extractor.getTextFromPage(page);
            all.append(pageText).append('\n');
            assertThat(pageText).contains("HOJA DE CONTEO FÍSICO", "DETALLE DEL CONTEO", "Página " + page + " de");
        }
        assertThat(all.toString()).contains("Producto de inventario 080");
        reader.close();

        Path preview = Path.of("target", "pdf-preview");
        Files.createDirectories(preview);
        Files.write(preview.resolve("conteo-hoja-visible.pdf"), pdf);
        Files.write(preview.resolve("conteo-hoja-ciega.pdf"), service.generar(data(false, false, null, 32), false));
        Files.write(preview.resolve("conteo-resultado.pdf"), service.generar(data(true, true, LOGO, 32), true));
    }

    @Test
    void formateaIdentificacionTelefonoYAdmiteEmpresaSinLogo() {
        Map<String, Object> params = ConteoFisicoPdfService.params(data(true, false, null, 1));
        assertThat(params.get("companyTradeName")).isEqualTo("EMPRESA PRUEBA");
        assertThat(params.get("companyTaxIdentification")).isEqualTo("124-54367-8");
        assertThat(params.get("companyPhone")).isEqualTo("809-555-1234");
        assertThat(params.get("companyLogo")).isNull();

        Map<String, Object> withLogo = ConteoFisicoPdfService.params(data(true, false, LOGO, 1));
        assertThat(withLogo.get("companyLogo")).isInstanceOf(ByteArrayInputStream.class);
    }

    private static ConteoFisicoReporteData data(boolean visible, boolean result, byte[] logo, int count) {
        List<ConteoFisicoReporteData.Linea> lines = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            lines.add(new ConteoFisicoReporteData.Linea(
                    index,
                    "PRD-" + String.format("%06d", index),
                    "Producto de inventario " + String.format("%03d", index)
                            + (index == 2 ? " con un nombre suficientemente largo para validar el espacio disponible" : ""),
                    index % 2 == 0 ? "Unidad" : "Caja",
                    index == 1 ? "9,876.50" : index + ".00",
                    result ? (index == 1 ? "9,878.50" : index + ".00") : "",
                    result ? (index == 1 ? "9,877.50" : index + ".00") : "",
                    result ? (index == 1 ? "+1.00" : "0.00") : ""));
        }
        return new ConteoFisicoReporteData(
                "EMPRESA PRUEBA", "EMPRESA PRUEBA, SRL", "124543678",
                "Av. Principal #123, Santo Domingo", "8095551234", "conteo@empresa.test", logo,
                "CON-000001", "14/10/2026", "Almacén Principal", "Sucursal Principal",
                count > 40 ? "Completo" : "Parcial", visible ? "Visible" : "Ciego",
                result ? "Confirmado" : "En conteo", "REF-001",
                "Conteo mensual del almacén con observación suficiente para validar la distribución documental.",
                visible || result, result ? "Administrador" : null, result ? "14/10/2026 17:15" : null,
                "Juan Pérez", "14/10/2026 17:17", lines);
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder result = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            result.append(extractor.getTextFromPage(page)).append('\n');
        }
        reader.close();
        return result.toString();
    }
}
