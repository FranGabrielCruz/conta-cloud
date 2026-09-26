package com.citacloud.springboot.contacloud.app.views;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmpresasViewTest {
    @Test
    void cargaLosDatosEditablesComoValorYNoComoPlaceholder() {
        var nombre = EmpresasView.campoTexto("Nombre comercial", "Comercial ABC");
        var correo = EmpresasView.campoCorreo("Correo electrónico", "contacto@abc.com");

        assertThat(nombre.getValue()).isEqualTo("Comercial ABC");
        assertThat(nombre.getPlaceholder()).isNull();
        assertThat(correo.getValue()).isEqualTo("contacto@abc.com");
        assertThat(correo.getPlaceholder()).isNull();
    }
}
