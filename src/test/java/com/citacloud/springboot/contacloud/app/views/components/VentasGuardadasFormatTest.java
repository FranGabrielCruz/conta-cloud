package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VentasGuardadasFormatTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void f5SiempreAbreVentasGuardadas() throws Exception {
        String pos = read("src/main/java/com/citacloud/springboot/contacloud/app/views/FacturacionPosView.java");

        assertThat(pos)
                .contains("this::dialogoVentasGuardadas, Key.F5",
                        "\"Guardar\", \"F5\", \"Guardar venta (F5)\", e -> dialogoVentasGuardadas()",
                        "new VentasGuardadasDialog(ventasGuardadas, token, !filas.isEmpty()")
                .doesNotContain("Retener venta", "Ventas retenidas",
                        "if (filas.isEmpty()) { Notification.show(\"No hay una venta para guardar.\"); return; }\n        if (!puede(\"ventas_guardadas.consultar\")");
    }

    @Test
    void listadoCentralizaGuardarBuscarRecuperarYDescartar() throws Exception {
        String dialog = read("src/main/java/com/citacloud/springboot/contacloud/app/views/components/VentasGuardadasDialog.java");

        assertThat(dialog)
                .contains("Guardar venta actual", "Recuperar venta", "Descartar venta",
                        "Buscar por ID, referencia o cliente...", "new AppPagination(r -> cargar(), 10, 10)",
                        "No hay ventas guardadas.", "ventaActualGuardada()")
                .doesNotContain("Factura", "Cobrar");
    }

    @Test
    void botonesPrincipalesUsanElEstandarGlobalIconOnly() throws Exception {
        String pos = read("src/main/java/com/citacloud/springboot/contacloud/app/views/FacturacionPosView.java");
        String dialog = read("src/main/java/com/citacloud/springboot/contacloud/app/views/components/VentasGuardadasDialog.java");

        assertThat(pos).contains("ActionType.SAVE, ButtonSize.MAIN",
                "ActionType.CANCEL, ButtonSize.MAIN");
        assertThat(dialog).contains("ActionType.SAVE, ButtonSize.MAIN",
                "ActionType.CLOSE, ButtonSize.MAIN", "ActionType.DELETE, ButtonSize.MAIN",
                "ActionType.RECOVER, ButtonSize.GRID_ACTION", "ActionType.DELETE, ButtonSize.GRID_ACTION");
    }

    @Test
    void gridAutoajustaSuAlturaSinModificarLaPaginacionBackend() throws Exception {
        String dialog = read("src/main/java/com/citacloud/springboot/contacloud/app/views/components/VentasGuardadasDialog.java");
        String css = read("src/main/frontend/themes/contacloud/styles.css");

        assertThat(dialog)
                .contains("grid.setAllRowsVisible(true)", "new AppPagination(r -> cargar(), 10, 10)",
                        "setMaxHeight(\"94vh\")")
                .doesNotContain("grid.setHeightFull()", "setHeight(\"min(720px, 94vh)\")");
        assertThat(css)
                .contains(".cc-saved-sales-body > vaadin-grid", "flex: 0 0 auto;", "height: auto;",
                        "vaadin-dialog-overlay.cc-saved-sales-dialog::part(content)")
                .doesNotContain(".cc-saved-sales-body > vaadin-grid {\n    flex: 1 1 auto;\n    min-height: 300px;");
    }

    @Test
    void migracionIncluyePermisosYAdmin() throws Exception {
        String sql = read("src/main/resources/db/migration/V76__implementar_ventas_guardadas_pos.sql");

        assertThat(sql).contains("ventas_guardadas.guardar", "ventas_guardadas.consultar",
                "ventas_guardadas.recuperar", "ventas_guardadas.recuperar_otros",
                "ventas_guardadas.descartar", "upper(r.codigo)='ADMINISTRADOR'");
    }

    private static String read(String path) throws Exception {
        return Files.readString(ROOT.resolve(path));
    }
}
