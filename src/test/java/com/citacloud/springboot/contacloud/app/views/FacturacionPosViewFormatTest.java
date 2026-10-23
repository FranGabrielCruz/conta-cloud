package com.citacloud.springboot.contacloud.app.views;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacturacionPosViewFormatTest {
    @Test void muestraImportesGrandesCompletosConDosDecimales() {
        assertEquals("12.00", FacturacionPosView.dinero(new BigDecimal("12")));
        assertEquals("1,534.00", FacturacionPosView.dinero(new BigDecimal("1534")));
        assertEquals("125,450.75", FacturacionPosView.dinero(new BigDecimal("125450.75")));
        assertEquals("1,179,999.99", FacturacionPosView.dinero(new BigDecimal("1179999.99")));
    }

    @Test void limpiarSoloSeHabilitaCuandoHayEstadoProvisional() {
        assertFalse(FacturacionPosView.tieneDatosVenta(0, false, BigDecimal.ZERO,
                BigDecimal.ZERO, "", null));
        assertTrue(FacturacionPosView.tieneDatosVenta(1, false, BigDecimal.ZERO,
                BigDecimal.ZERO, "", null));
        assertTrue(FacturacionPosView.tieneDatosVenta(0, true, BigDecimal.ZERO,
                BigDecimal.ZERO, "", null));
        assertTrue(FacturacionPosView.tieneDatosVenta(0, false, BigDecimal.ONE,
                BigDecimal.ZERO, "", null));
        assertTrue(FacturacionPosView.tieneDatosVenta(0, false, BigDecimal.ZERO,
                BigDecimal.ONE, "", null));
        assertTrue(FacturacionPosView.tieneDatosVenta(0, false, BigDecimal.ZERO,
                BigDecimal.ZERO, "Nota", null));
        assertTrue(FacturacionPosView.tieneDatosVenta(0, false, BigDecimal.ZERO,
                BigDecimal.ZERO, "", UUID.randomUUID()));
    }
}
