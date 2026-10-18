package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DoubleEnterDetectorTest {
    @Test void detectaDosEnterDentroDeLaVentana() {
        DoubleEnterDetector detector = new DoubleEnterDetector(450);
        assertFalse(detector.register(1_000, true));
        assertTrue(detector.register(1_400, true));
    }

    @Test void expiraLaSecuenciaFueraDeLaVentana() {
        DoubleEnterDetector detector = new DoubleEnterDetector(450);
        assertFalse(detector.register(1_000, true));
        assertFalse(detector.register(1_451, true));
    }

    @Test void reiniciaDespuesDeDetectar() {
        DoubleEnterDetector detector = new DoubleEnterDetector();
        detector.register(1_000, true);
        assertTrue(detector.register(1_100, true));
        assertFalse(detector.register(1_200, true));
    }

    @Test void eventosNoElegiblesReinicianAutocompleteEdicionYTextarea() {
        DoubleEnterDetector detector = new DoubleEnterDetector();
        detector.register(1_000, true);
        assertFalse(detector.register(1_100, false));
        assertFalse(detector.register(1_200, true));
    }

    @Test void resetExplicitoDescartaElPrimerEnter() {
        DoubleEnterDetector detector = new DoubleEnterDetector();
        detector.register(1_000, true);
        detector.reset();
        assertFalse(detector.register(1_100, true));
    }
}
