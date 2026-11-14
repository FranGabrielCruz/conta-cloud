package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.BajaActivoDto.Estado;
import com.citacloud.springboot.contacloud.app.dto.BajaActivoDto.Fila;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.citacloud.springboot.contacloud.app.views.components.AppGrid;
import com.citacloud.springboot.contacloud.app.views.components.AppPagination;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BajasActivosViewTest {
    @Test
    void presentaFiltrosYUnaAccionDirectaPorFila() {
        var vista = new BajasActivosView(null, null, null);
        var filtros = vista.getChildren().filter(c -> c.hasClassName("cc-maintenance-filters")).findFirst().orElseThrow();
        assertThat(filtros.getChildren().toList()).hasSize(5);
        assertThat(filtros.getChildren().toList().getLast().hasClassName("cc-maintenance-search")).isTrue();
        var tabla = (AppGrid<?>) vista.getChildren().filter(AppGrid.class::isInstance).findFirst().orElseThrow();
        var pagina = (AppPagination) vista.getChildren().filter(AppPagination.class::isInstance).findFirst().orElseThrow();
        assertThat(tabla.getWidth()).isEqualTo("100%");
        assertThat(tabla.isAllRowsVisible()).isTrue();
        assertThat(pagina.currentRequest().size()).isEqualTo(10);
        assertThat(tabla.getColumns()).extracting(c -> c.getHeaderText())
            .containsExactly("Código", "Activo", "Fecha efectiva", "Tipo", "Valor en libros", "Estado", "Acciones");
        @SuppressWarnings("unchecked")
        var accion = (ComponentRenderer<AppActionButton, Fila>) tabla.getColumns().getLast().getRenderer();
        var ver = accion.createComponent(new Fila(UUID.randomUUID(), "BAJ-000001", "Equipo",
            LocalDate.of(2026, 11, 14), "Venta", BigDecimal.TEN, "USD", Estado.CONFIRMADA));
        assertThat(ver.getAriaLabel()).contains("Ver baja");
        assertThat(ver.getText()).isEmpty();
    }
}
