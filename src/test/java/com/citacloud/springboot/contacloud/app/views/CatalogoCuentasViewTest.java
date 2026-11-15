package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.*;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.citacloud.springboot.contacloud.app.views.components.AppPagination;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogoCuentasViewTest {
    @Test
    void presentaArbolDeAnchoCompletoYAccionVer() {
        var vista = new CatalogoCuentasView(null, null, null);
        var filtros = vista.getChildren().filter(c -> c.hasClassName("cc-chart-filters")).findFirst().orElseThrow();
        assertThat(filtros.getChildren().toList()).hasSize(4);
        assertThat(filtros.getChildren().toList().getLast().hasClassName("cc-maintenance-search")).isTrue();
        var grid = (TreeGrid<?>) vista.getChildren().filter(TreeGrid.class::isInstance).findFirst().orElseThrow();
        var pagina = (AppPagination) vista.getChildren().filter(AppPagination.class::isInstance).findFirst().orElseThrow();
        assertThat(grid.isAllRowsVisible()).isTrue();
        assertThat(grid.getWidth()).isEqualTo("100%");
        assertThat(pagina.currentRequest().size()).isEqualTo(10);
        assertThat(grid.getColumns()).extracting(c -> c.getHeaderText())
            .containsExactly("Código", "Cuenta", "Tipo", "Naturaleza", "Estado", "Acc.");
        @SuppressWarnings("unchecked")
        var render = (ComponentRenderer<AppActionButton, Cuenta>) grid.getColumns().getLast().getRenderer();
        var ver = render.createComponent(new Cuenta(UUID.randomUUID(), null, "1", "Activo", Tipo.AGRUPADORA,
            Clasificacion.ACTIVO, Naturaleza.DEUDORA, true, null, 0, true, true));
        assertThat(ver.getAriaLabel()).contains("Ver cuenta");
        assertThat(ver.getText()).isEmpty();
    }
}
