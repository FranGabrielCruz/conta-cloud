package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PosChargeButtonTest {
    @Test
    void mantieneIconoYTextoEnUnSoloContenedorCentrable() {
        PosChargeButton button = new PosChargeButton(event -> { });

        assertTrue(button.hasClassName("cc-pos-charge"));
        assertEquals("Cobrar venta, atajo F9 o doble Enter", button.getElement().getAttribute("aria-label"));
        assertEquals("Cobrar venta (F9 o doble Enter)", button.getTooltip().getText());
        assertEquals(1, button.getChildren().count());
        var contenido = button.getChildren().findFirst().orElseThrow();
        assertTrue(contenido.hasClassName("cc-pos-charge-content"));
        assertTrue(contenido.getElement().getTextRecursively().contains("COBRAR F9"));
        assertEquals(2, contenido.getChildren().count());
    }
}
