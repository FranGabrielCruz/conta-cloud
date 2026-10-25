package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class RecibosCobrosParcialesTest {
    @Test void proponeElTotalDelCobroSinForzarElSaldoCompleto(){
        assertThat(RecibosCobrosDialog.suggestedApplication(new BigDecimal("400"),new BigDecimal("100"),BigDecimal.ZERO))
                .isEqualByComparingTo("100.0000");
        assertThat(RecibosCobrosDialog.suggestedApplication(new BigDecimal("400"),new BigDecimal("399.99"),BigDecimal.ZERO))
                .isEqualByComparingTo("399.9900");
        assertThat(RecibosCobrosDialog.suggestedApplication(new BigDecimal("400"),new BigDecimal("400"),BigDecimal.ZERO))
                .isEqualByComparingTo("400.0000");
    }

    @Test void nuncaProponeMasQueElSaldoNiMasQueElRestanteDelRecibo(){
        assertThat(RecibosCobrosDialog.suggestedApplication(new BigDecimal("400"),new BigDecimal("500"),BigDecimal.ZERO))
                .isEqualByComparingTo("400.0000");
        assertThat(RecibosCobrosDialog.suggestedApplication(new BigDecimal("400"),new BigDecimal("100"),new BigDecimal("60")))
                .isEqualByComparingTo("40.0000");
    }
}
