package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.models.TipoAlmacen;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlmacenesViewTest {
    @Test
    void representaLaSeleccionVaciaDelFiltroDeTipo() {
        assertThat(AlmacenesView.etiquetaTipo(null)).isEmpty();
        assertThat(AlmacenesView.etiquetaTipo(TipoAlmacen.GENERAL)).isEqualTo("General");
    }
}
