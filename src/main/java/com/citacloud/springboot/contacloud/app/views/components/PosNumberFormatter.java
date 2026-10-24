package com.citacloud.springboot.contacloud.app.views.components;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import com.vaadin.flow.component.textfield.BigDecimalField;

/** Formatos de presentación del POS. No debe utilizarse para cálculos ni persistencia. */
public final class PosNumberFormatter {
    private PosNumberFormatter() {}

    public static String money(BigDecimal value) {
        DecimalFormat format=new DecimalFormat("#,##0.00",DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value==null?BigDecimal.ZERO:value);
    }

    public static String quantity(BigDecimal value) {
        return variable(value);
    }

    public static String percentage(BigDecimal value) {
        return variable(value);
    }

    public static BigDecimal moneyInput(BigDecimal value) {
        return value==null?null:value.setScale(2,RoundingMode.HALF_UP);
    }

    public static BigDecimal quantityInput(BigDecimal value) {
        if(value==null)return null;
        if(value.signum()==0)return BigDecimal.ZERO;
        return value.stripTrailingZeros();
    }

    public static BigDecimal percentageInput(BigDecimal value) {
        return quantityInput(value);
    }

    /** Cambia solo el texto visible; no reemplaza el BigDecimal asociado al componente. */
    public static void showMoney(BigDecimalField field) {
        show(field, money(field.getValue()));
    }

    public static void showQuantity(BigDecimalField field) {
        show(field, quantity(field.getValue()));
    }

    public static void showPercentage(BigDecimalField field) {
        show(field, percentage(field.getValue()));
    }

    private static void show(BigDecimalField field, String formatted) {
        field.getElement().executeJs("""
                const input = this.inputElement || this.querySelector('input');
                if (input) input.value = $0;
                """, formatted);
    }

    private static String variable(BigDecimal value) {
        if(value==null||value.signum()==0)return "0";
        return value.stripTrailingZeros().toPlainString();
    }
}
