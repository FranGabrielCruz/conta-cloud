package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.models.EstadoOrdenCompra;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OrdenesCompraViewTest {
    @Test void representaLaOpcionVaciaDelFiltroSinLanzarExcepcion(){
        assertThat(OrdenesCompraView.state(null)).isEqualTo("Todos");
        assertThat(OrdenesCompraView.state(EstadoOrdenCompra.DRAFT)).isEqualTo("Borrador");
    }
}
