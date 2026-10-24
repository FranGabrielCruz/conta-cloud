package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PosDialogGlobalPaginationTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void dialogosPaginadosHeredanCincoYAutoajustanSusFilas() throws Exception {
        for (String archivo : new String[]{"DevolucionesVentasDialog.java", "CargarCotizacionDialog.java",
                "VentasGuardadasDialog.java", "UltimasFacturasDialog.java", "DescuentoVentaDialog.java",
                "RecargoVentaDialog.java", "PosPaymentDialog.java"}) {
            String fuente = read("src/main/java/com/citacloud/springboot/contacloud/app/views/components/" + archivo);
            assertThat(fuente).as(archivo).contains("PosDialogPagination");
        }
        assertThat(read("src/main/java/com/citacloud/springboot/contacloud/app/services/PosPaginationPolicy.java"))
                .contains("DIALOG_DEFAULT_PAGE_SIZE = 5");
        assertThat(read("src/main/java/com/citacloud/springboot/contacloud/app/views/components/DescuentoVentaDialog.java"))
                .contains("seleccionadas", "linea.lineaId()", "grid.setAllRowsVisible(true)");
        assertThat(read("src/main/java/com/citacloud/springboot/contacloud/app/views/components/RecargoVentaDialog.java"))
                .contains("seleccionadas", "linea.lineaId()", "grid.setAllRowsVisible(true)");
    }

    @Test
    void cargarCotizacionPaginaEnBackendConOrdenYTenantDeterministas() throws Exception {
        String servicio = read("src/main/java/com/citacloud/springboot/contacloud/app/services/CotizacionService.java");
        String repositorio = read("src/main/java/com/citacloud/springboot/contacloud/app/repositories/CotizacionRepository.java");

        assertThat(servicio).contains("buscarParaPos(String texto,int pagina)",
                "PosPaginationPolicy.DIALOG_DEFAULT_PAGE_SIZE", "Sort.Direction.DESC,\"id\"");
        assertThat(repositorio).contains("c.tenantId=:tenant and c.empresaId=:empresa", "c.status in :estados");
    }

    @Test
    void reglaNoPaginaElCarritoPrincipalNiCambiaElEstandarGlobal() throws Exception {
        String pos = read("src/main/java/com/citacloud/springboot/contacloud/app/views/FacturacionPosView.java");
        String servicio = read("src/main/java/com/citacloud/springboot/contacloud/app/services/FacturacionPosService.java");
        String global = read("src/main/java/com/citacloud/springboot/contacloud/app/views/components/AppPagination.java");

        assertThat(global).contains("this(onChange, 10, 10, 25, 50, 100)");
        assertThat(pos).contains("private final Grid<Fila> grid = new Grid<>()", "grid.setItems(filas)",
                        "new PosDialogPagination(request -> cargar.run())", "buscarProductosPagina")
                .doesNotContain("PosDialogPagination paginacionCarrito");
        assertThat(servicio).contains("buscarProductosPagina", "PosPaginationPolicy.DIALOG_DEFAULT_PAGE_SIZE",
                "Sort.by(\"nombre\").and(Sort.by(\"codigo\")).and(Sort.by(\"id\"))");
    }

    private static String read(String ruta) throws Exception {
        return Files.readString(ROOT.resolve(ruta));
    }
}
