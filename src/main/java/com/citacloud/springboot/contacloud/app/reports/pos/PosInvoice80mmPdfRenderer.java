// Rendering core mirrored from contaCloudPrint; see docs/REDISENO-POS-80MM.md.
package com.citacloud.springboot.contacloud.app.reports.pos;

import com.citacloud.springboot.contacloud.app.reports.pos.*;
import com.citacloud.springboot.contacloud.app.reports.pos.PosInvoiceDocument;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.slf4j.LoggerFactory;

/** Native 80 mm ticket. Plain text only; no HTML, scripts, URLs, images or external resources. */
public final class PosInvoice80mmPdfRenderer implements DocumentRenderer<PosInvoiceDocument> {
    public static final float WIDTH = 80 * 72 / 25.4f;
    public static final float MARGIN = 3.5f * 72 / 25.4f;
    private static final float INNER = WIDTH - 2 * MARGIN;
    private static final float MAX_HEIGHT = 2800;
    public record Options(boolean hideZeroDiscount, boolean hideZeroSurcharge) { }
    private final Options options;
    public PosInvoice80mmPdfRenderer() { this(new Options(false, false)); }
    public PosInvoice80mmPdfRenderer(Options options) { this.options = Objects.requireNonNull(options); }
    private record Text(String value, float x, float size, boolean bold) { }
    private record Row(List<Text> text, float height, boolean rule) { }
    @Override public byte[] render(PosInvoiceDocument document) {
        var log = LoggerFactory.getLogger(PosInvoice80mmPdfRenderer.class); log.info("event=PdfRenderStarted");
        try (PDDocument pdf = new PDDocument(); InputStream resource = PosInvoice80mmPdfRenderer.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            if (resource == null) { throw new IOException(); }
            PDType0Font font = PDType0Font.load(pdf, resource);
            Layout layout = new Layout(font);
            layout.center(document.issuer().name(), 12, true);
            if (present(document.issuer().identification())) { layout.center(("DO".equalsIgnoreCase(document.issuer().countryCode()) ? "RNC: " : "ID: ") + InvoiceFormats.rnc(document.issuer().identification(), document.issuer().countryCode()), 8.5f, false); }
            if (present(document.issuer().address())) { layout.center(document.issuer().address(), 8.5f, false); }
            if (present(document.issuer().phone())) { layout.center("Tel.: " + InvoiceFormats.phone(document.issuer().phone(), document.issuer().countryCode()), 8.5f, false); }
            layout.gap();
            if (document.testDocument() && !document.documentType().equals("DOCUMENTO DE PRUEBA")) { layout.center("DOCUMENTO DE PRUEBA", 10, true); }
            layout.center(document.documentType(), 10, true); layout.rule();
            layout.metadata("Factura", document.invoiceNumber());
            if (present(document.ncf())) { layout.metadata("NCF", document.ncf()); }
            layout.metadata("Fecha", InvoiceFormats.date(document.dateTime()));
            layout.metadata("Cliente", document.customer().name());
            if (present(document.customer().identification())) { layout.metadata("RNC/Cédula", document.customer().identification()); }
            if (present(document.paymentCondition())) { layout.metadata("Condición", document.paymentCondition()); }
            if (present(document.cashRegister())) { layout.metadata("Caja", document.cashRegister()); }
            if (present(document.cashier())) { layout.metadata("Cajero", document.cashier()); }
            layout.rule();
            float amountWidth = 58;
            for (var item : document.items()) { amountWidth = Math.max(amountWidth, layout.measure(InvoiceFormats.amount(item.amount()), 8.5f)); }
            layout.item("CANT.", "DESCRIPCIÓN", "IMPORTE", amountWidth, true);
            layout.rule();
            for (var item : document.items()) { layout.item(InvoiceFormats.quantity(item.quantity()), item.description(), InvoiceFormats.amount(item.amount()), amountWidth, false); }
            layout.rule(); layout.pair("Subtotal", InvoiceFormats.amount(document.subtotal()), 8.5f, false);
            if (document.discount() != null && (!options.hideZeroDiscount() || document.discount().signum() != 0)) { layout.pair("Descuento", InvoiceFormats.amount(document.discount()), 8.5f, false); }
            if (document.surcharge() != null && (!options.hideZeroSurcharge() || document.surcharge().signum() != 0)) { layout.pair("Recargo", InvoiceFormats.amount(document.surcharge()), 8.5f, false); }
            for (var tax : document.taxes()) { layout.pair(tax.label().equals("Impuestos") ? tax.label() : "Impuestos (" + tax.label() + ")", InvoiceFormats.amount(tax.amount()), 8.5f, false); }
            layout.rule(); layout.total("TOTAL " + document.currency(), InvoiceFormats.amount(document.total()));
            layout.rule();
            if (document.payments().size() > 1) { layout.left("PAGO", 8.5f, true); }
            for (var payment : document.payments()) { layout.pair(payment.label(), InvoiceFormats.amount(payment.amount()), 8.5f, false); }
            if (document.received() != null) { layout.pair("Recibido", InvoiceFormats.amount(document.received()), 8.5f, false); }
            if (document.change() != null) { layout.pair("Cambio", InvoiceFormats.amount(document.change()), 8.5f, false); }
            layout.rule(); layout.gap();
            for (String footer : document.footer()) { layout.center(footer, 8.5f, false); }
            if (layout.rows.size() > 12000) { throw new IOException(); }
            List<Row> pageRows = new ArrayList<>(); float height = 2 * MARGIN;
            for (Row row : layout.rows) {
                if (height + row.height() > MAX_HEIGHT) { page(pdf, font, pageRows, height); pageRows.clear(); height = 2 * MARGIN; }
                pageRows.add(row); height += row.height();
            }
            if (!pageRows.isEmpty()) { page(pdf, font, pageRows, height); }
            pdf.getDocumentInformation().setProducer("POS PDF");
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(layout.rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            COSArray identifiers = new COSArray(); identifiers.add(new COSString(Arrays.copyOf(hash, 16))); identifiers.add(new COSString(Arrays.copyOf(hash, 16))); pdf.getDocument().setDocumentID(identifiers);
            ByteArrayOutputStream output = new ByteArrayOutputStream(); pdf.save(output);
            log.info("event=PdfRenderSucceeded pages={}", pdf.getNumberOfPages()); return output.toByteArray();
        } catch (IOException | RuntimeException | java.security.NoSuchAlgorithmException failure) {
            log.warn("event=PdfRenderFailed"); throw new InvoicePrintFailure(InvoicePrintFailure.Kind.DOCUMENT_GENERATION_FAILED);
        }
    }
    private static void page(PDDocument pdf, PDType0Font font, List<Row> rows, float height) throws IOException {
        PDPage page = new PDPage(new PDRectangle(WIDTH, height)); pdf.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
            float y = height - MARGIN;
            for (Row row : rows) {
                if (row.rule()) { stream.setLineWidth(0.4f); stream.moveTo(MARGIN, y - 4); stream.lineTo(WIDTH - MARGIN, y - 4); stream.stroke(); }
                for (Text text : row.text()) {
                    stream.beginText(); stream.setFont(font, text.size()); stream.setRenderingMode(text.bold() ? RenderingMode.FILL_STROKE : RenderingMode.FILL);
                    stream.setLineWidth(0.18f); stream.newLineAtOffset(MARGIN + text.x(), y - text.size()); stream.showText(text.value()); stream.endText();
                }
                y -= row.height();
            }
        }
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static final class Layout {
        private final PDType0Font font;
        private final List<Row> rows = new ArrayList<>();
        Layout(PDType0Font font) { this.font = font; }
        float measure(String text, float size) throws IOException { return font.getStringWidth(text) * size / 1000; }
        void gap() { rows.add(new Row(List.of(), 3, false)); }
        void rule() { rows.add(new Row(List.of(), 7, true)); }
        void left(String text, float size, boolean bold) throws IOException { for (String line : wrap(text, INNER, size)) { rows.add(new Row(List.of(new Text(line, 0, size, bold)), size + 3, false)); } }
        void center(String text, float size, boolean bold) throws IOException {
            for (String line : wrap(text, INNER, size)) { rows.add(new Row(List.of(new Text(line, (INNER - measure(line, size)) / 2, size, bold)), size + 2, false)); }
        }
        void metadata(String label, String value) throws IOException {
            float size = 8.5f;
            float labelWidth = measure("RNC/Cédula:", size) + 7;
            List<String> lines = wrap(value, INNER - labelWidth, size);
            for (int i = 0; i < lines.size(); i++) {
                List<Text> text = new ArrayList<>();
                if (i == 0) { text.add(new Text(label + ":", 0, size, false)); }
                text.add(new Text(lines.get(i), labelWidth, size, false));
                rows.add(new Row(text, size + 2, false));
            }
        }
        void total(String label, String value) throws IOException {
            float size = 11;
            while (size > 8 && measure(label + ":  " + value, size) > INNER) { size -= 0.5f; }
            if (measure(label + ":  " + value, size) > INNER) { throw new IOException(); }
            rows.add(new Row(List.of(new Text(label + ":", 0, size, true), new Text(value, INNER - measure(value, size), size, true)), size + 3, false));
        }
        void pair(String label, String value, float size, boolean bold) throws IOException {
            float rightWidth = Math.min(INNER * 0.66f, Math.max(65, measure(value, size)));
            List<String> left = wrap(label + ":", INNER - rightWidth - 8, size); List<String> right = wrap(value, rightWidth, size);
            for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
                List<Text> text = new ArrayList<>();
                if (i < left.size()) { text.add(new Text(left.get(i), 0, size, bold)); }
                if (i < right.size()) { text.add(new Text(right.get(i), INNER - measure(right.get(i), size), size, bold)); }
                rows.add(new Row(text, size + 2, false));
            }
        }
        void item(String quantity, String description, String amount, float amountWidth, boolean bold) throws IOException {
            float size = bold ? 7.5f : 8.5f;
            if (amountWidth > INNER * 0.55f) { throw new IOException(); }
            List<String> qty = wrap(quantity, 24, size); List<String> desc = wrap(description, INNER - 24 - amountWidth - 12, size);
            List<String> money = wrap(amount, amountWidth, size);
            for (int i = 0; i < Math.max(qty.size(), Math.max(desc.size(), money.size())); i++) {
                List<Text> text = new ArrayList<>();
                if (i < qty.size()) { text.add(new Text(qty.get(i), 0, size, bold)); }
                if (i < desc.size()) { text.add(new Text(desc.get(i), 30, size, bold)); }
                if (i < money.size()) { text.add(new Text(money.get(i), INNER - measure(money.get(i), size), size, bold)); }
                rows.add(new Row(text, size + 2, false));
            }
        }
        List<String> wrap(String text, float width, float size) throws IOException {
            List<String> result = new ArrayList<>();
            for (String paragraph : text.split("\\n", -1)) {
                String remaining = paragraph.strip();
                if (remaining.isEmpty()) { result.add(""); continue; }
                while (!remaining.isEmpty()) {
                    int end = 0; int lastSpace = -1;
                    while (end < remaining.length()) {
                        int next = end + Character.charCount(remaining.codePointAt(end));
                        if (measure(remaining.substring(0, next), size) > width) { break; }
                        if (remaining.charAt(end) == ' ') { lastSpace = end; } end = next;
                    }
                    if (end == 0) { throw new IOException(); }
                    if (end < remaining.length() && lastSpace > 0) { end = lastSpace; }
                    result.add(remaining.substring(0, end)); remaining = remaining.substring(end).stripLeading();
                }
            }
            return result;
        }
    }
}
