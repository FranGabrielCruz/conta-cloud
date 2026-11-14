package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.MantenimientoDto.*;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.citacloud.springboot.contacloud.app.views.components.AppGrid;
import com.citacloud.springboot.contacloud.app.views.components.AppPagination;
import com.citacloud.springboot.contacloud.app.views.components.AppStatusBadge;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MantenimientoViewTest {
    @Test void tablaLlenaElAnchoYLaUnicaAccionEsVer() {
        var vista=new MantenimientoActivosView(null,null,null);
        var filtros=vista.getChildren().filter(c->c.hasClassName("cc-maintenance-filters")).findFirst().orElseThrow();
        var campos=filtros.getChildren().toList();
        assertThat(campos).hasSize(5);
        assertThat(campos.getLast().hasClassName("cc-maintenance-search")).isTrue();
        var tabla=(AppGrid<?>)vista.getChildren().filter(AppGrid.class::isInstance).findFirst().orElseThrow();
        var pagina=(AppPagination)vista.getChildren().filter(AppPagination.class::isInstance).findFirst().orElseThrow();
        assertThat(tabla.isAllRowsVisible()).isTrue();
        assertThat(pagina.currentRequest().size()).isEqualTo(10);
        assertThat(tabla.getColumns()).extracting(c->c.getHeaderText())
            .containsExactly("Código","Activo","Tipo","Fecha","Costo","Estado","Acc.");
        assertThat(tabla.getColumns()).extracting(c->c.getFlexGrow()).containsExactly(1,3,1,1,2,1,0);
        @SuppressWarnings("unchecked")
        var acciones=(ComponentRenderer<AppActionButton,Fila>)tabla.getColumns().getLast().getRenderer();
        @SuppressWarnings("unchecked")
        var estado=(ComponentRenderer<AppStatusBadge,Fila>)tabla.getColumns().get(5).getRenderer();
        var fila=new Fila(UUID.randomUUID(),"MAN-000001","AF-000001","Equipo","Preventivo",
            LocalDate.of(2026,11,14),BigDecimal.TEN,"DOP",Estado.PROGRAMADO,true,"Cambio de filtros");
        var ver=acciones.createComponent(fila);
        assertThat(ver.getAriaLabel()).contains("Ver mantenimiento");
        assertThat(ver.getText()).isEmpty();
        var badge=estado.createComponent(fila);
        assertThat(badge.getText()).isEqualTo("Vencido");
        assertThat(badge.getElement().getThemeList()).contains("error");
    }
}
