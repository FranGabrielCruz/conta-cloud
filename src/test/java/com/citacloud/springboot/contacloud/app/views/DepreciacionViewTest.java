package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Fila;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.citacloud.springboot.contacloud.app.views.components.AppGrid;
import com.citacloud.springboot.contacloud.app.views.components.AppPagination;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DepreciacionViewTest {
    @Test
    void historialAutoajustableConDiezFilasYUnaSolaAccion() {
        var vista=new DepreciacionesActivosView(null,null,null);
        var tabla=(AppGrid<?>)vista.getChildren().filter(AppGrid.class::isInstance).findFirst().orElseThrow();
        var pagina=(AppPagination)vista.getChildren().filter(AppPagination.class::isInstance).findFirst().orElseThrow();
        assertThat(tabla.isAllRowsVisible()).isTrue();
        assertThat(pagina.currentRequest().size()).isEqualTo(10);
        assertThat(tabla.getColumns()).extracting(c->c.getHeaderText())
            .containsExactly("Período","Procesado","Activos","Moneda","Dep. período","Estado","Acc.");
        assertThat(tabla.getColumns()).allSatisfy(c->assertThat(c.isAutoWidth()).isTrue());
        assertThat(tabla.getColumns()).extracting(c->c.getFlexGrow()).containsExactly(2,3,2,2,3,3,0);
        @SuppressWarnings("unchecked")
        var renderer=(ComponentRenderer<AppActionButton,Fila>)tabla.getColumns().getLast().getRenderer();
        var periodo=YearMonth.of(2026,10);
        var ver=renderer.createComponent(new Fila(UUID.randomUUID(),periodo,periodo.atEndOfMonth(),1,"DOP",BigDecimal.TEN));
        assertThat(ver.getAriaLabel()).contains("Ver depreciación");
        assertThat(ver.getText()).isEmpty();
    }
}
