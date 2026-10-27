package com.citacloud.springboot.contacloud.app.services;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValeCajaPdfTemplateTest {
    @Test void compilaComprobanteCompactoConSeccionesCondicionales() throws Exception {
        JasperReport reporte = compilar();
        Map<String, Object> parametros = parametrosBase();
        byte[] confirmado = generar(reporte, parametros);
        Path previews = Path.of("target", "pdf-preview");
        Files.createDirectories(previews);
        Files.write(previews.resolve("vale-caja-confirmado.pdf"), confirmado);
        assertThat(texto(confirmado)).contains("VALE DE CAJA", "VAL-000001", "CONFIRMADO", "DOP 400.00")
            .doesNotContain("OBSERVACIÓN", "ANULACIÓN", "ContaCloud");

        parametros.put("status", "ANULADO");
        parametros.put("voided", true);
        parametros.put("voidDateTime", "26/10/2026 18:30");
        parametros.put("voidUser", "admin");
        parametros.put("voidReason", "Vale emitido por error");
        byte[] pdfAnulado = generar(reporte, parametros);
        Files.write(previews.resolve("vale-caja-anulado.pdf"), pdfAnulado);
        String anulado = texto(pdfAnulado);
        assertThat(anulado).contains("ANULADO", "ANULACIÓN", "Vale emitido por error", "26/10/2026 18:30");
    }

    private JasperReport compilar() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/reports/cash-voucher/cash-voucher.jrxml")) {
            assertThat(in).isNotNull();
            return JasperCompileManager.compileReport(in);
        }
    }

    private static Map<String, Object> parametrosBase() {
        Map<String, Object> p = new HashMap<>();
        p.put("companyName", "Empresa Demo"); p.put("companyTaxId", "101-00000-1");
        p.put("companyAddress", "Santo Domingo"); p.put("companyPhone", "809-555-0100");
        p.put("companyEmail", "demo@example.com"); p.put("companyLogo", null); p.put("hasLogo", false);
        p.put("number", "VAL-000001"); p.put("status", "CONFIRMADO"); p.put("cash", "Caja 1");
        p.put("branch", "Sucursal Principal"); p.put("dateTime", "26/10/2026 18:00"); p.put("user", "admin");
        p.put("concept", "Compra menor"); p.put("beneficiary", "Proveedor Demo"); p.put("reference", null);
        p.put("hasReference", false); p.put("notes", null); p.put("hasObservation", false); p.put("voided", false);
        p.put("voidDateTime", null); p.put("voidUser", null); p.put("voidReason", null); p.put("totalText", "DOP 400.00");
        return p;
    }

    private static byte[] generar(JasperReport reporte, Map<String, Object> parametros) throws Exception {
        return JasperExportManager.exportReportToPdf(
            JasperFillManager.fillReport(reporte, parametros, new JREmptyDataSource()));
    }

    private static String texto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));
        reader.close();
        return text.toString();
    }
}
