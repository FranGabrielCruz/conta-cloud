package com.citacloud.springboot.contacloud.app.views;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class SaldosCuentasViewTest {
    @Test void formateaImportesConSimboloYSignoExplicito(){
        assertThat(SaldosCuentasView.moneyWithSymbol(new BigDecimal("18316.40"),"RD$","DOP"))
            .isEqualTo("RD$ 18,316.40");
        assertThat(SaldosCuentasView.moneyWithSymbol(new BigDecimal("-18112.00"),"RD$","DOP"))
            .isEqualTo("-RD$ 18,112.00");
        assertThat(SaldosCuentasView.moneyWithSymbol(BigDecimal.ZERO,"US$","USD"))
            .isEqualTo("US$ 0.00");
        assertThat(SaldosCuentasView.moneyWithSymbol(new BigDecimal("125450850250.35"),"RD$","DOP"))
            .isEqualTo("RD$ 125,450,850,250.35");
    }
}
