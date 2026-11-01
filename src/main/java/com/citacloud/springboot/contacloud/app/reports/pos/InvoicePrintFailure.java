// Rendering core mirrored from contaCloudPrint; see docs/REDISENO-POS-80MM.md.
package com.citacloud.springboot.contacloud.app.reports.pos;

public final class InvoicePrintFailure extends RuntimeException {
    public enum Kind { DOCUMENT_GENERATION_FAILED, PRINT_FAILED, CANCELLED }
    private final Kind kind;
    private boolean unsupportedPaper;
    public InvoicePrintFailure(Kind kind) {
        this(kind, message(kind));
    }
    private InvoicePrintFailure(Kind kind, String safeMessage) { super(safeMessage); this.kind = kind; }
    private static String message(Kind kind) {
        return switch (kind) {
            case DOCUMENT_GENERATION_FAILED -> "No fue posible generar el documento para impresión.";
            case PRINT_FAILED -> "No fue posible enviar el documento a la impresora configurada. Verifica que el controlador admita papel de 80 mm.";
            case CANCELLED -> "Impresión cancelada.";
        };
    }
    public Kind kind() { return kind; }
    public boolean unsupportedPaper() { return unsupportedPaper; }
    public static InvoicePrintFailure paperNotSupported() {
        var failure = new InvoicePrintFailure(Kind.PRINT_FAILED); failure.unsupportedPaper = true; return failure;
    }
    public static InvoicePrintFailure printerUnavailable() { return new InvoicePrintFailure(Kind.PRINT_FAILED, "Impresora no disponible."); }
}
