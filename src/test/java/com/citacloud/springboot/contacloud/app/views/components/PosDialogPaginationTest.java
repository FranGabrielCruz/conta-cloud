package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.select.Select;
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

    @Test
    void paginacionFijaNoRenderizaSelectorYConservaCincoAlCambiarPagina() {
        var cambios = new ArrayList<AppPagination.PageRequest>();
        var paginacion = PosDialogPagination.fixed(cambios::add);
        paginacion.setTotal(11);

        assertThat(paginacion.getChildren().anyMatch(Select.class::isInstance)).isFalse();

        Button siguiente = paginacion.getChildren().flatMap(component -> component.getChildren())
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "Página siguiente".equals(button.getElement().getAttribute("aria-label")))
                .findFirst().orElseThrow();
        siguiente.click();

        assertThat(paginacion.currentRequest()).isEqualTo(new AppPagination.PageRequest(1, 5));
        assertThat(cambios).containsExactly(new AppPagination.PageRequest(1, 5));
    }
}
