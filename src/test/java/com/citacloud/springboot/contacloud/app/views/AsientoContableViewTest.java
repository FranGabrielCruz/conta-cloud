package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.citacloud.springboot.contacloud.app.views.components.AppGrid;
import com.citacloud.springboot.contacloud.app.views.components.AppPagination;
import com.citacloud.springboot.contacloud.app.services.AsientoContableService;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AsientoContableViewTest {
    @Test void editorCompactoActualizaTotalesYConservaAccionesEnElPie() {
        var servicio=mock(AsientoContableService.class);
        var moneda=new Moneda(UUID.randomUUID(),"USD");
        when(servicio.monedaBase()).thenReturn(moneda);
        when(servicio.hoy()).thenReturn(LocalDate.of(2026,11,15));
        var principal=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"DEMO","Admin","admin","",true,
            Set.of("asientos.contabilizar"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        try {
            var modal=new AsientosContablesView(servicio,null,null).construirFormulario(null,null);
            var general=buscar(modal,"cc-journal-general");
            assertThat(general.getChildren().toList()).hasSize(4);
            assertThat(((ComboBox<?>)general.getChildren().toList().get(2)).getValue()).isEqualTo(moneda);
            assertThat(general.getChildren().toList().getLast().getElement().getTextRecursively()).isEqualTo("OrigenManual");
            var campos=buscar(modal,"cc-journal-reference");
            assertThat(campos.getChildren().map(c->((TextField)c).getLabel()).toList()).containsExactly("Concepto","Referencia");
            var concepto=(TextField)campos.getChildren().findFirst().orElseThrow();
            assertThat(concepto.isRequiredIndicatorVisible()).isTrue();
            var movimientos=buscar(modal,"cc-journal-movements");
            var filas=movimientos.getChildren().toList();
            assertThat(filas).hasSize(3);
            assertThat(filas.getFirst().hasClassName("cc-journal-columns")).isTrue();
            var totales=buscar(modal,"cc-journal-totals");
            var botones=modal.getFooter().getElement().getChildren().map(e->(AppActionButton)e.getComponent().orElseThrow()).toList();
            assertThat(botones).extracting(b->b.getAriaLabel().orElse("")).containsExactly("Guardar","Contabilizar","Cancelar");
            var post=botones.get(1);
            assertThat(post.isEnabled()).isFalse();
            seleccionarCuenta(filas.get(1));
            seleccionarCuenta(filas.get(2));
            campoImporte(filas.get(1),2).setValue(new BigDecimal("120"));
            campoImporte(filas.get(2),3).setValue(new BigDecimal("95"));
            assertThat(totales.getElement().getTextRecursively()).contains("25.00 · Descuadrado");
            campoImporte(filas.get(2),3).setValue(new BigDecimal("120"));
            assertThat(post.isEnabled()).isFalse();
            concepto.setValue("Prueba");
            assertThat(post.isEnabled()).isTrue();
            var agregar=(AppActionButton)buscar(modal,"cc-detail-heading").getChildren().toList().getLast();
            agregar.click();
            assertThat(movimientos.getChildren().toList()).hasSize(4);
            ((AppActionButton)filas.get(2).getChildren().toList().getLast()).click();
            assertThat(totales.getElement().getTextRecursively()).contains("120.00 · Descuadrado");
            assertThat(post.isEnabled()).isFalse();
        } finally { SecurityContextHolder.clearContext(); }
    }

    private static Component buscar(Dialog modal,String clase) {
        return modal.getChildren().filter(c->c.hasClassName(clase)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static void seleccionarCuenta(Component fila) {
        var combo=(ComboBox<Cuenta>)fila.getChildren().findFirst().orElseThrow();
        combo.setValue(new Cuenta(UUID.randomUUID(),"1.1.01.01","Caja General",true,true));
    }

    private static BigDecimalField campoImporte(Component fila,int indice) {
        return (BigDecimalField)fila.getChildren().toList().get(indice);
    }

    @Test void muestraFiltrosYUnaAccionDirecta() {
        var vista=new AsientosContablesView(null,null,null);
        var filtros=vista.getChildren().filter(c->c.hasClassName("cc-journal-filters")).findFirst().orElseThrow();
        assertThat(filtros.getChildren().toList()).hasSize(6);
        assertThat(filtros.getChildren().toList().getLast().hasClassName("cc-maintenance-search")).isTrue();
        var grid=(AppGrid<?>)vista.getChildren().filter(AppGrid.class::isInstance).findFirst().orElseThrow();
        var pagina=(AppPagination)vista.getChildren().filter(AppPagination.class::isInstance).findFirst().orElseThrow();
        assertThat(grid.getWidth()).isEqualTo("100%");
        assertThat(grid.isAllRowsVisible()).isTrue();
        assertThat(pagina.currentRequest().size()).isEqualTo(10);
        @SuppressWarnings("unchecked")
        var renderer=(ComponentRenderer<AppActionButton,Fila>)grid.getColumns().getLast().getRenderer();
        var ver=renderer.createComponent(new Fila(UUID.randomUUID(),"AS-000001",LocalDate.of(2026,11,15),Tipo.DIARIO,
            Origen.MANUAL,"Prueba","USD",Estado.BORRADOR,new Total(BigDecimal.ZERO,BigDecimal.ZERO)));
        assertThat(ver.getAriaLabel()).contains("Ver asiento");
        assertThat(ver.getText()).isEmpty();
    }
}
