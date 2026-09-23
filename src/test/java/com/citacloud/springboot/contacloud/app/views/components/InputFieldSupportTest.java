package com.citacloud.springboot.contacloud.app.views.components;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InputFieldSupportTest {
    @Test void formateaRncDominicanoAlSalirDelCampo() {
        assertThat(InputFieldSupport.formatearRnc("101850585")).isEqualTo("1-01-85058-5");
        assertThat(InputFieldSupport.formatearRnc("1-01-85058-5")).isEqualTo("1-01-85058-5");
    }

    @Test void formateaTelefonoDominicanoAlSalirDelCampo() {
        assertThat(InputFieldSupport.formatearTelefono("8095550101")).isEqualTo("809-555-0101");
        assertThat(InputFieldSupport.formatearTelefono("(809) 555-0101")).isEqualTo("809-555-0101");
    }

    @Test void conservaValoresIncompletosParaQueLaValidacionLosMuestre() {
        assertThat(InputFieldSupport.formatearRnc("123")).isEqualTo("123");
        assertThat(InputFieldSupport.formatearTelefono("809")).isEqualTo("809");
    }
}
