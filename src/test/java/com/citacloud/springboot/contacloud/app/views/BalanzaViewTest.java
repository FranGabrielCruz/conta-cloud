package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Cuenta;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Naturaleza;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.services.BalanzaService;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.QueryParameters;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BalanzaViewTest {
    @Test
    @SuppressWarnings("unchecked")
    void muestraSoloElOjoYNavegaConElPeriodoActivo() {
        var vista=new BalanzaView(mock(BalanzaService.class),mock(ModuleAuthorization.class));
        var grid=(Grid<Cuenta>)vista.getChildren().filter(Grid.class::isInstance).findFirst().orElseThrow();
        assertThat(grid.getColumns()).extracting(Grid.Column::getHeaderText)
            .containsExactly("Código","Cuenta","Débito","Crédito","Débito","Crédito","Débito","Crédito","Acc.");
        var renderer=(ComponentRenderer<AppActionButton,Cuenta>)grid.getColumns().getLast().getRenderer();
        var id=UUID.randomUUID();
        var ver=renderer.createComponent(new Cuenta(id,null,null,"1","Activos",Tipo.AGRUPADORA,Naturaleza.DEUDORA,true,1,List.of()));
        assertThat(ver.getChildren()).singleElement().isInstanceOf(Icon.class);
        assertThat(((Icon)ver.getIcon()).getElement().getAttribute("icon")).isEqualTo("vaadin:eye");
        assertThat(ver.getText()).isEmpty();
        assertThat(ver.getElement().getAttribute("aria-label")).isEqualTo("Ver Libro Mayor");
        assertThat(ver.getClassNames()).contains("cc-action-grid","cc-action-view");

        var fechas=vista.getChildren().flatMap(c->c.getChildren()).filter(DatePicker.class::isInstance)
            .map(DatePicker.class::cast).toList();
        fechas.get(0).setValue(LocalDate.of(2026,10,5));
        fechas.get(1).setValue(LocalDate.of(2026,10,19));
        var ui=mock(UI.class);
        UI.setCurrent(ui);
        try {
            ver.click();
            verify(ui).navigate(eq("libro-mayor"),argThat((QueryParameters q)->q.getParameters().equals(Map.of(
                "accountId",List.of(id.toString()),"fromDate",List.of("2026-10-05"),"toDate",List.of("2026-10-19")))));
        } finally {
            UI.setCurrent(null);
        }
    }
}
