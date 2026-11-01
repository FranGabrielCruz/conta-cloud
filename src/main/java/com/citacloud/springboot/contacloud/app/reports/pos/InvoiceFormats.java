// Rendering core mirrored from contaCloudPrint; see docs/REDISENO-POS-80MM.md.
package com.citacloud.springboot.contacloud.app.reports.pos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class InvoiceFormats {
    private InvoiceFormats() { }
    public static String fileName(String number) {
        if (number == null || number.contains("..") || number.contains("/") || number.contains("\\") || number.contains(":")) { throw new IllegalArgumentException("Nombre de documento inválido"); }
        String name = number.replaceAll("[^A-Za-z0-9_-]", "_");
        if (name.isBlank()) { name = "documento"; }
        if (name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])")) { name = "_" + name; }
        return name.substring(0, Math.min(80, name.length())) + ".pdf";
    }
    public static String amount(BigDecimal value) {
        var format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP); return format.format(value);
    }
    public static String quantity(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    public static String date(LocalDateTime value) { return value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)); }
    public static String rnc(String value, String country) {
        if (value == null) { return ""; }
        if ("DO".equals(country) && value.matches("[0-9]{9}")) { return value.substring(0,3) + "-" + value.substring(3,8) + "-" + value.substring(8); }
        return value;
    }
    public static String phone(String value, String country) {
        if (value == null) { return ""; }
        if ("DO".equals(country) && value.matches("(?:809|829|849)[0-9]{7}")) { return value.substring(0,3) + "-" + value.substring(3,6) + "-" + value.substring(6); }
        return value;
    }
}
