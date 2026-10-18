package com.citacloud.springboot.contacloud.app.services;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class VentaCalculoTest {
    @Test void calculaLineaConImpuestoConfigurado(){var r=VentaCalculo.linea(new BigDecimal("100.00"),new BigDecimal("2"),new BigDecimal("18"));
        assertEquals(new BigDecimal("200.0000"),r.subtotal());assertEquals(new BigDecimal("36.0000"),r.impuesto());assertEquals(new BigDecimal("236.0000"),r.total());}
    @Test void calculaCambioYEvitaEfectivoInsuficiente(){assertEquals(new BigDecimal("2.0000"),VentaCalculo.cambio(new BigDecimal("118"),new BigDecimal("120")));
        assertThrows(IllegalArgumentException.class,()->VentaCalculo.cambio(new BigDecimal("118"),new BigDecimal("100")));}
}
