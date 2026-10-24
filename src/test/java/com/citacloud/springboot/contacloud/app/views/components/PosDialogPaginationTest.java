package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class PosDialogPaginationTest {

    @Test
    void todaNuevaPaginacionDeDialogoPosIniciaEnCinco() {
        var cambios = new ArrayList<AppPagination.PageRequest>();
        var paginacion = new PosDialogPagination(cambios::add);

        assertThat(paginacion.currentRequest()).isEqualTo(new AppPagination.PageRequest(0, 5));
    }

    @Test
    void unaExcepcionLocalNoCambiaElPredeterminadoDelModulo() {
        var excepcion = new PosDialogPagination(request -> { }, 10, 5, 10);
        var siguienteDialogo = new PosDialogPagination(request -> { });

        assertThat(excepcion.currentRequest().size()).isEqualTo(10);
        assertThat(siguienteDialogo.currentRequest().size()).isEqualTo(5);
    }
}
