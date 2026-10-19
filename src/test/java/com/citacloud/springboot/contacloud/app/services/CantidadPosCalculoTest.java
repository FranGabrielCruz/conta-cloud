package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CantidadPosCalculoTest {
    @Test void incrementaConElPasoConfigurado() {
        assertEquals(new BigDecimal("3"), CantidadPosCalculo.incrementar(new BigDecimal("2"), BigDecimal.ONE));
        assertEquals(new BigDecimal("2.25"), CantidadPosCalculo.incrementar(new BigDecimal("1.25"), BigDecimal.ONE));
    }

    @Test void disminuyeSinLlegarACero() {
        assertEquals(new BigDecimal("1"), CantidadPosCalculo.disminuir(BigDecimal.ONE, BigDecimal.ONE));
        assertEquals(new BigDecimal("2"), CantidadPosCalculo.disminuir(new BigDecimal("3"), BigDecimal.ONE));
    }

    @Test void exigeCantidadMinimaDeUnoYPermiteFraccionesMayores() {
        assertTrue(CantidadPosCalculo.valida(new BigDecimal("1.50")));
        assertFalse(CantidadPosCalculo.valida(new BigDecimal("0.50")));
        assertFalse(CantidadPosCalculo.valida(BigDecimal.ZERO));
        assertFalse(CantidadPosCalculo.valida(null));
    }
}
