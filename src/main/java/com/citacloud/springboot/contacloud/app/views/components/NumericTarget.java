package com.citacloud.springboot.contacloud.app.views.components;

/** Campo numérico explícito que puede recibir entradas del keypad del POS. */
public interface NumericTarget {
    Object owner();
    String currentRawValue();
    boolean isValid();
    boolean decimalAllowed();
    int scale();
    int maxDigits();
    void preview(String rawValue);
    void commit(String rawValue);
    void restore();
}
