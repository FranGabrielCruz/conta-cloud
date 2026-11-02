// Rendering core mirrored from contaCloudPrint; see docs/DEVOLUCION-POS-80MM.md.
package com.citacloud.springboot.contacloud.app.reports.pos;

import com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats;
import com.citacloud.springboot.contacloud.app.reports.pos.PosInvoiceDocument;
import java.io.IOException;
import java.util.Objects;

/** Invoice composition; geometry and PDF serialization are shared with other POS documents. */
public final class PosInvoice80mmPdfRenderer extends PosTicket80mmPdfRenderer<PosInvoiceDocument> {
    public record Options(boolean hideZeroDiscount, boolean hideZeroSurcharge) { }
    private final Options options;
    public PosInvoice80mmPdfRenderer() { this(new Options(false, false)); }
    public PosInvoice80mmPdfRenderer(Options options) { this.options = Objects.requireNonNull(options); }
    @Override protected void compose(Layout layout, PosInvoiceDocument document) throws IOException {
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
    }
}
