package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.BigDecimalField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PosNumericKeypadTest {
    @Test void defaultModeIsFixedAndDoesNotCreateContextTrigger() {
        PosNumericKeypad keypad = new PosNumericKeypad();
        BigDecimalField field = new BigDecimalField();

        Button trigger = keypad.bind(field, "QUANTITY",
                PosNumericKeypad.Options.quantity(false, null), value -> { });

        assertEquals(PosNumericKeypad.Mode.FIXED, keypad.mode());
        assertNull(trigger);
        assertFalse(keypad.isPopoverOpen());
    }

    @Test void popoverModeCreatesCompactTriggerClosedByDefault() {
        PosNumericKeypad keypad = new PosNumericKeypad(PosNumericKeypad.Mode.POPOVER);
        BigDecimalField field = new BigDecimalField();

        Button trigger = keypad.bind(field, "CASH_RECEIVED",
                PosNumericKeypad.Options.money(null), value -> { });

        assertEquals(PosNumericKeypad.Mode.POPOVER, keypad.mode());
        assertNotNull(trigger);
        assertTrue(trigger.isVisible());
        assertEquals("Abrir teclado numérico", trigger.getElement().getAttribute("aria-label"));
        assertFalse(keypad.isPopoverOpen());
    }

    @Test void readOnlyFieldDoesNotExposeContextTrigger() {
        PosNumericKeypad keypad = new PosNumericKeypad(PosNumericKeypad.Mode.POPOVER);
        BigDecimalField field = new BigDecimalField();
        field.setReadOnly(true);

        Button trigger = keypad.bind(field, "CALCULATED_TOTAL",
                PosNumericKeypad.Options.money(null), value -> { });

        assertFalse(trigger.isVisible());
    }
}
