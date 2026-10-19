package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DoubleSpaceDetectorTest {
    @Test void detectaDosEspaciosDentroDeLaVentana() {
        DoubleSpaceDetector detector = new DoubleSpaceDetector(450);
        assertFalse(detector.register(1_000, true));
        assertTrue(detector.register(1_400, true));
    }

    @Test void noDetectaFueraDeLaVentana() {
        DoubleSpaceDetector detector = new DoubleSpaceDetector(450);
        assertFalse(detector.register(1_000, true));
        assertFalse(detector.register(1_451, true));
    }

    @Test void eventoNoElegibleReiniciaLaSecuencia() {
        DoubleSpaceDetector detector = new DoubleSpaceDetector();
        detector.register(1_000, true);
        assertFalse(detector.register(1_100, false));
        assertFalse(detector.register(1_200, true));
    }

    @Test void reiniciaDespuesDeDetectar() {
        DoubleSpaceDetector detector = new DoubleSpaceDetector();
        detector.register(1_000, true);
        assertTrue(detector.register(1_100, true));
        assertFalse(detector.register(1_200, true));
    }
}
