// Rendering core mirrored from contaCloudPrint; see docs/DEVOLUCION-POS-80MM.md.
package com.citacloud.springboot.contacloud.app.reports.pos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Confirmed snapshot, never a calculator. Monetary lines are supplied by the source. */
public record PosInvoiceDocument(Issuer issuer, String documentType, String ncf, String invoiceNumber,
        LocalDateTime dateTime, String cashRegister, String cashier, Customer customer, List<Item> items,
        BigDecimal subtotal, BigDecimal discount, List<AmountLine> taxes, BigDecimal total, String currency,
        List<AmountLine> payments, BigDecimal change, List<String> footer, boolean testDocument,
        String paymentCondition, BigDecimal surcharge, BigDecimal received) {
    /** Compatibility for documents that did not yet supply these optional snapshot fields. */
    public PosInvoiceDocument(Issuer issuer, String documentType, String ncf, String invoiceNumber,
            LocalDateTime dateTime, String cashRegister, String cashier, Customer customer, List<Item> items,
            BigDecimal subtotal, BigDecimal discount, List<AmountLine> taxes, BigDecimal total, String currency,
            List<AmountLine> payments, BigDecimal change, List<String> footer, boolean testDocument) {
        this(issuer, documentType, ncf, invoiceNumber, dateTime, cashRegister, cashier, customer, items,
                subtotal, discount, taxes, total, currency, payments, change, footer, testDocument, null, null, null);
    }
    public record Issuer(String name, String identification, String address, String phone, String countryCode) {
        public Issuer { required(name, 200); optional(identification, 60); optional(address, 500); optional(phone, 60); required(countryCode, 2); }
    }
    public record Customer(String name, String identification) {
        public Customer { required(name, 200); optional(identification, 60); }
    }
    public record Item(BigDecimal quantity, String description, BigDecimal amount) {
        public Item {
            money(quantity); if (quantity.signum() <= 0) { throw new IllegalArgumentException("Cantidad inválida"); }
            required(description, 1000); money(amount);
        }
    }
    public record AmountLine(String label, BigDecimal amount) {
        public AmountLine { required(label, 100); money(amount); }
    }
    public PosInvoiceDocument {
        Objects.requireNonNull(issuer); Objects.requireNonNull(customer); Objects.requireNonNull(dateTime);
        required(documentType, 100); required(invoiceNumber, 100); optional(ncf, 60); optional(cashRegister, 100); optional(cashier, 100);
        if (currency == null || !currency.matches("[A-Z]{3}")) { throw new IllegalArgumentException("Moneda inválida"); }
        items = List.copyOf(items); taxes = List.copyOf(taxes); payments = List.copyOf(payments); footer = List.copyOf(footer);
        if (items.isEmpty() || items.size() > 500 || taxes.size() > 20 || payments.size() > 20 || footer.size() > 10) { throw new IllegalArgumentException("Documento fuera de límites"); }
        money(subtotal); money(total); if (discount != null) { money(discount); } if (change != null) { money(change); }
        optional(paymentCondition, 100); if (surcharge != null) { money(surcharge); } if (received != null) { money(received); }
        footer.forEach(line -> required(line, 300));
    }
    private static void money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.precision() > 16 || Math.abs(value.scale()) > 6) { throw new IllegalArgumentException("Importe fuera de límites"); }
    }
    private static void required(String text, int max) { optional(text, max); if (text == null || text.isBlank()) { throw new IllegalArgumentException("Dato requerido"); } }
    private static void optional(String text, int max) {
        if (text != null && (text.length() > max || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n'))) { throw new IllegalArgumentException("Texto fuera de límites"); }
    }
}
