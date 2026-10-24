package com.citacloud.springboot.contacloud.app.views.components;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PosNumberFormatterTest {
    @Test void formateaDineroConDosDecimalesYMiles() {
        assertThat(PosNumberFormatter.money(new BigDecimal("200"))).isEqualTo("200.00");
        assertThat(PosNumberFormatter.money(new BigDecimal("200.000000"))).isEqualTo("200.00");
        assertThat(PosNumberFormatter.money(new BigDecimal("1534.5"))).isEqualTo("1,534.50");
        assertThat(PosNumberFormatter.money(new BigDecimal("1000000"))).isEqualTo("1,000,000.00");
    }

    @Test void eliminaSoloCerosNoSignificativosDeCantidades() {
        assertThat(PosNumberFormatter.quantity(new BigDecimal("1"))).isEqualTo("1");
        assertThat(PosNumberFormatter.quantity(new BigDecimal("1.0000"))).isEqualTo("1");
        assertThat(PosNumberFormatter.quantity(new BigDecimal("1.5000"))).isEqualTo("1.5");
        assertThat(PosNumberFormatter.quantity(new BigDecimal("2.2500"))).isEqualTo("2.25");
        assertThat(PosNumberFormatter.quantity(new BigDecimal("0.7500"))).isEqualTo("0.75");
        assertThat(PosNumberFormatter.quantity(new BigDecimal("10.1250"))).isEqualTo("10.125");
    }

    @Test void eliminaCerosNoSignificativosDePorcentajes() {
        assertThat(PosNumberFormatter.percentage(new BigDecimal("5.0000"))).isEqualTo("5");
        assertThat(PosNumberFormatter.percentage(new BigDecimal("5.5000"))).isEqualTo("5.5");
        assertThat(PosNumberFormatter.percentage(new BigDecimal("5.2500"))).isEqualTo("5.25");
    }

    @Test void normalizaCamposSinCambiarElValorMatematicoDeCantidad() {
        BigDecimal original=new BigDecimal("1.5000");
        assertThat(PosNumberFormatter.quantityInput(original)).isEqualByComparingTo(original);
        assertThat(PosNumberFormatter.quantityInput(original).toPlainString()).isEqualTo("1.5");
    }
}
