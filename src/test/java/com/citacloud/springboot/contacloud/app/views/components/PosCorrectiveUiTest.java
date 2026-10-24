package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PosCorrectiveUiTest {

    @Test
    void devolucionesUsaUnaSolaAccionCompactaConElTooltipAcordado() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/citacloud/springboot/contacloud/app/views/components/DevolucionesVentasDialog.java"));

        assertThat(source).contains("ActionType.RETURN,ButtonSize.GRID_ACTION",
                "\"Iniciar devolución\"", ".setWidth(\"48px\")");
        assertThat(source).doesNotContain("\"Crear devolución\"", "cc-grid-icon-button");
    }

    @Test
    void devolucionesUsaCincoFilasAutoajustablesYReiniciaLaPaginaAlFiltrar() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/citacloud/springboot/contacloud/app/views/components/DevolucionesVentasDialog.java"));
        String repository = Files.readString(Path.of(
                "src/main/java/com/citacloud/springboot/contacloud/app/repositories/FacturaVentaRepository.java"));

        assertThat(source).contains("new PosDialogPagination(req->cargarFacturas())",
                "facturas.setAllRowsVisible(true)",
                "buscar.addValueChangeListener(e->{paginacion.reset();cargarFacturas();})",
                "desde.addValueChangeListener(e->{paginacion.reset();cargarFacturas();})",
                "hasta.addValueChangeListener(e->{paginacion.reset();cargarFacturas();})",
                "estado.addValueChangeListener(e->{paginacion.reset();cargarFacturas();})");
        assertThat(repository).contains("ORDER BY f.confirmed_at DESC,f.id DESC");
    }

    @Test
    void recargoConservaFilaUnicaYTooltipsParaOpcionesLargas() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/citacloud/springboot/contacloud/app/views/components/RecargoVentaDialog.java"));
        String css = Files.readString(Path.of("src/main/frontend/themes/contacloud/styles.css"));

        assertThat(source).contains("cc-surcharge-inline-fields", "opcionConTooltip",
                "new Div(valor, abrirTeclado)");
        assertThat(css).contains("minmax(0, 30fr) minmax(190px, 25fr) minmax(0, 45fr)",
                ".cc-adjustment-combo-option", "text-overflow: ellipsis");
    }
}
