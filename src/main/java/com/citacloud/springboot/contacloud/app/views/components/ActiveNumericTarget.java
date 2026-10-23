package com.citacloud.springboot.contacloud.app.views.components;

/**
 * Motor efímero de edición del keypad. No conoce productos, pagos ni reglas de
 * negocio: únicamente dirige cada entrada al target que fue activado.
 */
public final class ActiveNumericTarget {
    private NumericTarget target;
    private String buffer = "";
    private boolean editingStarted;

    public void activate(NumericTarget next) {
        if (next == target) return;
        finish();
        if (next == null || !next.isValid()) return;
        target = next;
        buffer = normalize(next.currentRawValue());
        editingStarted = false;
    }

    public boolean input(String key) {
        if (!validTarget()) return false;
        if (key == null || key.isBlank() || "-".equals(key)) return false;
        if ("Backspace".equals(key)) return backspace();
        if (".".equals(key) || ",".equals(key)) return decimal();
        if (key.length() != 1 || !Character.isDigit(key.charAt(0))) return false;

        String candidate = editingStarted ? buffer + key : key;
        if (digitCount(candidate) > target.maxDigits()) return false;
        buffer = candidate;
        editingStarted = true;
        target.preview(buffer);
        return true;
    }

    public void finish() {
        if (target == null) return;
        NumericTarget current = target;
        String raw = buffer;
        boolean changed = editingStarted;
        clearState();
        if (!current.isValid()) return;
        if (!changed || raw.isBlank() || ".".equals(raw)) current.restore();
        else current.commit(trimTrailingDecimal(raw));
    }

    public void clear() {
        if (target != null && target.isValid()) target.restore();
        clearState();
    }

    public void clearIfOwner(Object owner) {
        if (target != null && java.util.Objects.equals(target.owner(), owner)) clearState();
    }

    public void finishIfOwner(Object owner) {
        if (target != null && java.util.Objects.equals(target.owner(), owner)) finish();
    }

    public boolean hasTarget() {
        return validTarget();
    }

    public boolean decimalEnabled() {
        return validTarget() && target.decimalAllowed();
    }

    public boolean editingStarted() {
        return editingStarted;
    }

    String buffer() {
        return buffer;
    }

    private boolean decimal() {
        if (!target.decimalAllowed() || target.scale() <= 0) return false;
        String source = editingStarted ? buffer : "0";
        if (source.contains(".")) return false;
        buffer = source + ".";
        editingStarted = true;
        target.preview(buffer);
        return true;
    }

    private boolean backspace() {
        String source = editingStarted ? buffer : normalize(target.currentRawValue());
        buffer = source.isEmpty() ? "" : source.substring(0, source.length() - 1);
        editingStarted = true;
        target.preview(buffer);
        return true;
    }

    private boolean validTarget() {
        if (target == null) return false;
        if (target.isValid()) return true;
        clearState();
        return false;
    }

    private void clearState() {
        target = null;
        buffer = "";
        editingStarted = false;
    }

    private static int digitCount(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (Character.isDigit(value.charAt(i))) count++;
        return count;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().replace(',', '.');
    }

    private static String trimTrailingDecimal(String value) {
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }
}
