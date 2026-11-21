package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.CierreContableDto.Estado;
import com.citacloud.springboot.contacloud.app.dto.CierreContableDto.Periodo;
import com.citacloud.springboot.contacloud.app.dto.CierreContableDto.*;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CierreContableViewTest {
    @Test void separaEjercicioMesYMensajesDeValidacion() {
        var fecha=LocalDate.of(2026,12,1);
        var p=new Periodo(UUID.randomUUID(),"Diciembre",fecha,fecha.plusDays(30),Estado.ABIERTO,null);
        var periodo=new Resultado(BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO);
        var anual=new Resultado(BigDecimal.valueOf(100),BigDecimal.ZERO,BigDecimal.ZERO);
        var mensaje="Completa y cierra todos los períodos anteriores del ejercicio.";
        var e=new Evaluacion(Tipo.EJERCICIO,fecha.withDayOfYear(1),"DOP",null,anual,
            List.of(new Validacion("Períodos del ejercicio",false,mensaje,"Pendientes")),fecha.withDayOfYear(1),periodo);
        var columnas=CierreContableView.detalleSuperior(p,e,null).getChildren().toList();
        assertThat(columnas).hasSize(3);
        assertThat(columnas.get(0).getElement().getTextRecursively()).contains("PERÍODO","Diciembre");
        assertThat(columnas.get(1).getElement().getTextRecursively())
            .contains("CIERRE DE EJERCICIO","100.00","No configurada","Configuración requerida").doesNotContain(mensaje);
        assertThat(columnas.get(2).getElement().getTextRecursively()).contains("VALIDACIONES PREVIAS","Pendientes",mensaje);
        assertThat(CierreContableView.resultadoPeriodo(e).getElement().getTextRecursively())
            .contains("RESULTADO DEL PERÍODO","10.00").doesNotContain("100.00",mensaje);
    }
    @Test @SuppressWarnings("unchecked")
    void abreConTodosLosEstadosYUnaAccionDirecta() {
        var vista=new CierreContableView(null,null);
        var estado=(Select<Estado>)vista.getChildren().flatMap(c->c.getChildren())
            .filter(Select.class::isInstance).findFirst().orElseThrow();
        assertThat(estado.isEmpty()).isTrue();
        var grid=(Grid<Periodo>)vista.getChildren().filter(Grid.class::isInstance).findFirst().orElseThrow();
        assertThat(grid.getColumns()).extracting(Grid.Column::getHeaderText)
            .containsExactly("Período","Desde","Hasta","Estado","Fecha cierre","Acc.");
        var renderer=(ComponentRenderer<AppActionButton,Periodo>)grid.getColumns().getLast().getRenderer();
        var dia=LocalDate.of(2026,11,1);
        var ver=renderer.createComponent(new Periodo(UUID.randomUUID(),"Noviembre",dia,dia.plusDays(29),Estado.ABIERTO,null));
        assertThat(ver.getElement().getAttribute("aria-label")).isEqualTo("Ver cierre");
        assertThat(ver.getIcon().getElement().getAttribute("icon")).isEqualTo("vaadin:eye");
        assertThat(ver.getText()).isEmpty();
    }
}
