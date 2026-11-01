package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.reports.pos.PosInvoiceDocument;
import com.citacloud.springboot.contacloud.app.reports.pos.PosInvoiceDocument.*;
import com.citacloud.springboot.contacloud.app.reports.pos.PosInvoice80mmPdfRenderer;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PosTicketRedesignTest {
    private final PosInvoice80mmPdfRenderer renderer = new PosInvoice80mmPdfRenderer();
    private static BigDecimal amount(String value) { return new BigDecimal(value); }
    private PosInvoiceDocument fixture(int count, boolean full, String currency, String description, String company, String customer) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < count; i++) { items.add(new Item(amount(i % 2 == 0 ? "1.000000" : "1.5"), description + " " + (i + 1), amount("9999999.99"))); }
        return new PosInvoiceDocument(new Issuer(company, "124543678", full ? "Av. Principal No. 25\nSanto Domingo, R.D." : null,
                full ? "8095551234" : null, "DO"), "FACTURA DE CRÉDITO FISCAL", full ? "PRUEBA-NO-FISCAL" : null,
                "PRUEBA-000017", LocalDateTime.of(2026, 11, 1, 0, 29), full ? "Caja 02" : null, full ? "José Pérez" : null,
                new Customer(customer, full ? "001-0000000-0" : null), items, amount("12012"), amount("0"),
                List.of(new AmountLine("ITBIS", amount("2162.16")), new AmountLine("Otro", amount("2.50"))),
                amount("14174.16"), currency, full ? List.of(new AmountLine("Efectivo", amount("5000")), new AmountLine("Tarjeta", amount("9174.16"))) : List.of(),
                full ? amount("825.84") : null, List.of("Gracias por su compra", "Conserve este comprobante"), false,
                full ? "Contado" : "Crédito", amount("0"), full ? amount("15000") : null);
    }
    @ParameterizedTest @ValueSource(ints = {1, 5, 20, 50})
    void compactDynamicLayoutKeepsColumnsAndSuppliedAmounts(int count) throws Exception {
        var document = fixture(count, true, "USD", "Café Piña Reparación", "DEMO", "Consumidor Final");
        try (var pdf = Loader.loadPDF(renderer.render(document))) {
            String text = new PDFTextStripper().getText(pdf);
            for (String expected : List.of("RNC: 124-54367-8", "PRUEBA-NO-FISCAL", "Fecha:", "01/11/2026 00:29", "Condición:", "Contado", "CANT.", "DESCRIPCIÓN", "IMPORTE", "Recargo:", "Descuento:", "Impuestos (ITBIS):", "TOTAL USD:", "14,174.16", "Recibido:", "15,000.00", "825.84", "9,999,999.99")) { assertTrue(text.contains(expected), expected); }
            assertFalse(text.contains("DOCUMENTO DE PRUEBA")); assertFalse(text.contains("ContaCloud"));
            assertTrue(pdf.getPage(0).getMediaBox().getHeight() < 450 + count * 32, "No blank fixed-height tail");
            Geometry inspector = new Geometry(); inspector.getText(pdf); inspector.verify();
        }
    }
    @Test void missingHeaderFieldsAndZeroOptionsShrinkDocumentWithoutInventingValues() throws Exception {
        var document = fixture(1, false, "EUR", "Producto", "Empresa", "Consumidor Final");
        try (var pdf = Loader.loadPDF(new PosInvoice80mmPdfRenderer(new PosInvoice80mmPdfRenderer.Options(true, true)).render(document))) {
            String text = new PDFTextStripper().getText(pdf);
            for (String absent : List.of("NCF:", "Tel.:", "RNC/Cédula:", "Caja:", "Cajero:", "Descuento:", "Recargo:", "Recibido:", "Cambio:")) { assertFalse(text.contains(absent), absent); }
            assertTrue(text.contains("Crédito")); assertTrue(text.contains("TOTAL EUR:"));
            assertTrue(pdf.getPage(0).getMediaBox().getHeight() < 300);
        }
    }
    @ParameterizedTest @ValueSource(strings={"9.99","999.99","9999.99","99999.99","999999.99","9999999.99"})
    void moneyValuesKeepTheirRightEdgeAndTotalOnOneLine(String value) throws Exception {
        var original=fixture(1,false,"DOP","Piña","Empresa","Consumidor Final");
        var document=new PosInvoiceDocument(original.issuer(),original.documentType(),null,original.invoiceNumber(),original.dateTime(),null,null,
                original.customer(),List.of(new Item(BigDecimal.ONE,"Piña",amount(value))),original.subtotal(),original.discount(),original.taxes(),
                amount(value),original.currency(),original.payments(),null,original.footer(),false,"Crédito",original.surcharge(),null);
        try(var pdf=Loader.loadPDF(renderer.render(document))) {
            var rows=new HashMap<Integer,List<TextPosition>>();
            PDFTextStripper inspector=new PDFTextStripper(){
                @Override protected void processTextPosition(TextPosition p) {
                    rows.computeIfAbsent(Math.round(p.getYDirAdj()*10), key->new ArrayList<>()).add(p);
                    super.processTextPosition(p);
                }
            };
            inspector.getText(pdf);boolean found=false;
            for(var positions:rows.values()) {
                positions.sort(Comparator.comparing(TextPosition::getXDirAdj));
                String text=positions.stream().map(TextPosition::getUnicode).collect(java.util.stream.Collectors.joining());
                if(text.contains("TOTAL DOP:")) {
                    found=true;assertTrue(text.contains(com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats.amount(amount(value))));
                    var last=positions.getLast();
                    assertEquals(PosInvoice80mmPdfRenderer.WIDTH-PosInvoice80mmPdfRenderer.MARGIN,last.getXDirAdj()+last.getWidthDirAdj(),0.5);
                }
            }
            assertTrue(found);
        }
    }
    @Test void visualFixturesAndLongTextPreserveAllCharacters() throws Exception {
        Path directory = Path.of("target", "pos-redesign-review"); Files.createDirectories(directory);
        for (String scenario : List.of("1-producto", "5-productos", "20-productos", "descripcion-larga", "cliente-largo", "empresa-larga", "importe-grande", "multipago")) {
            int count = scenario.equals("5-productos") ? 5 : scenario.equals("20-productos") ? 20 : 1;
            String description = scenario.equals("descripcion-larga") ? "Hamburguesa especial con queso y tocineta, Café y Piña, Reparación e Información completas" : "Producto de demostración";
            String company = scenario.equals("empresa-larga") ? "Comercializadora de artículos y servicios profesionales con nombre comercial muy largo, SRL" : "EMPRESA DE DEMOSTRACIÓN";
            String customer = scenario.equals("cliente-largo") ? "José Pérez y asociados, cliente con nombre completo muy largo para comprobar el ajuste de texto" : "Consumidor Final";
            byte[] bytes = renderer.render(fixture(count, true, "DOP", description, company, customer));
            Files.write(directory.resolve(scenario + ".pdf"), bytes);
            try (var pdf = Loader.loadPDF(bytes)) {
                Geometry inspector = new Geometry(); inspector.getText(pdf); inspector.verify();
                var image = new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0, 180);
                javax.imageio.ImageIO.write(image, "png", directory.resolve(scenario + ".png").toFile());
                String text = new PDFTextStripper().getText(pdf).replaceAll("\\s", "");
                for (String word : description.split("\\s+")) { assertTrue(text.contains(word), "Missing description word: " + word); }
                assertTrue(text.contains(company.replaceAll("\\s", "")));
                assertTrue(text.contains(customer.replaceAll("\\s", "")));
            }
        }
    }
    private static final class Geometry extends PDFTextStripper {
        private final Map<String, List<TextPosition>> lines = new HashMap<>();
        Geometry() throws java.io.IOException { super(); }
        @Override protected void processTextPosition(TextPosition p) {
            float margin = PosInvoice80mmPdfRenderer.MARGIN;
            assertTrue(p.getXDirAdj() >= margin - 0.4);
            assertTrue(p.getXDirAdj() + p.getWidthDirAdj() <= PosInvoice80mmPdfRenderer.WIDTH - margin + 0.5);
            assertEquals(80, getCurrentPage().getMediaBox().getWidth() * 25.4 / 72, 0.001);
            assertTrue(p.getYDirAdj() <= p.getPageHeight() - margin + 0.5);
            lines.computeIfAbsent(getCurrentPageNo() + ":" + Math.round(p.getYDirAdj() * 10), key -> new ArrayList<>()).add(p);
            super.processTextPosition(p);
        }
        void verify() {
            for (var line : lines.values()) {
                line.sort(Comparator.comparing(TextPosition::getXDirAdj));
                for (int i = 1; i < line.size(); i++) {
                    var left = line.get(i - 1); var right = line.get(i);
                    assertTrue(left.getXDirAdj() + left.getWidthDirAdj() <= right.getXDirAdj() + 0.5, "Horizontal overlap");
                }
            }
        }
    }
}
