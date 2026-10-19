package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CobroVentaCalculoTest {
    @Test void calculaCobroSimpleExacto(){
        BigDecimal aplicado=CobroVentaCalculo.totalAplicado(List.of(new BigDecimal("1000")));
        assertEquals(new BigDecimal("1000.0000"),aplicado);
        assertEquals(new BigDecimal("0.0000"),CobroVentaCalculo.pendiente(new BigDecimal("1000"),aplicado));
        assertFalse(CobroVentaCalculo.mixto(1));
    }
    @Test void calculaCambioSoloSobreLaPorcionEfectiva(){
        assertEquals(new BigDecimal("500.0000"),CobroVentaCalculo.cambio(new BigDecimal("2000"),new BigDecimal("2500")));
    }
    @Test void cambioVisibleEsCeroSinRecibidoOSiEsInsuficiente(){
        assertEquals(new BigDecimal("0.0000"), CobroVentaCalculo.cambioNoNegativo(new BigDecimal("14.16"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.0000"), CobroVentaCalculo.cambioNoNegativo(new BigDecimal("14.16"), new BigDecimal("10")));
    }
    @Test void cambioVisibleEsCeroParaPagoExacto(){
        assertEquals(new BigDecimal("0.0000"), CobroVentaCalculo.cambioNoNegativo(new BigDecimal("14.16"), new BigDecimal("14.16")));
    }
    @Test void cambioVisibleCalculaExcedenteEfectivo(){
        assertEquals(new BigDecimal("5.8400"), CobroVentaCalculo.cambioNoNegativo(new BigDecimal("14.16"), new BigDecimal("20")));
        assertEquals(new BigDecimal("74549.2500"), CobroVentaCalculo.cambioNoNegativo(new BigDecimal("125450.75"), new BigDecimal("200000")));
    }
    @Test void calculaCobroMixtoConTresAplicaciones(){
        BigDecimal aplicado=CobroVentaCalculo.totalAplicado(List.of(new BigDecimal("2000"),new BigDecimal("5000"),new BigDecimal("3000")));
        assertEquals(new BigDecimal("10000.0000"),aplicado);
        assertEquals(new BigDecimal("0.0000"),CobroVentaCalculo.pendiente(new BigDecimal("10000"),aplicado));
        assertTrue(CobroVentaCalculo.mixto(3));
    }
    @Test void conservaPendienteCuandoElCobroEstaIncompleto(){
        assertEquals(new BigDecimal("3000.0000"),CobroVentaCalculo.pendiente(new BigDecimal("5000"),new BigDecimal("2000")));
    }
    @Test void admiteElMismoTipoEnAplicacionesSeparadas(){
        assertEquals(new BigDecimal("5000.0000"),CobroVentaCalculo.totalAplicado(List.of(new BigDecimal("2000"),new BigDecimal("3000"))));
        assertTrue(CobroVentaCalculo.mixto(2));
    }
    @Test void redondeaConPrecisionFinanciera(){
        assertEquals(new BigDecimal("28.3200"),CobroVentaCalculo.totalAplicado(List.of(new BigDecimal("28.31996"))));
    }
}
