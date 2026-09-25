package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteUsuariosValidatorTest {
    @Test
    void deshabilitadoEliminaElLimiteConfigurado() {
        assertThat(LimiteUsuariosValidator.normalizar(false, 25)).isNull();
    }

    @Test
    void habilitadoAceptaUnaCantidadValida() {
        assertThat(LimiteUsuariosValidator.normalizar(true, 25)).isEqualTo(25);
    }

    @Test
    void habilitadoRequiereAlMenosUnUsuario() {
        assertThatThrownBy(() -> LimiteUsuariosValidator.normalizar(true, 0))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("mayor o igual a 1");
    }

    @Test
    void limitaValoresExcesivos() {
        assertThatThrownBy(() -> LimiteUsuariosValidator.normalizar(true, 100001))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("100,000");
    }
}
