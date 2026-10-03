package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.views.PhaseTwoPlaceholderView;
import com.citacloud.springboot.contacloud.app.views.CajasView;
import com.citacloud.springboot.contacloud.app.views.CuentasBancariasView;
import com.citacloud.springboot.contacloud.app.views.IngresosView;
import com.citacloud.springboot.contacloud.app.views.EgresosView;
import com.citacloud.springboot.contacloud.app.views.TransferenciasView;
import com.citacloud.springboot.contacloud.app.views.ConciliacionBancariaView;
import com.citacloud.springboot.contacloud.app.views.OperacionesCajaView;
import com.citacloud.springboot.contacloud.app.views.ProveedoresView;
import com.citacloud.springboot.contacloud.app.views.OrdenesCompraView;
import com.citacloud.springboot.contacloud.app.views.ProductosView;
import com.citacloud.springboot.contacloud.app.views.UnidadesMedidaView;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseTwoNavigationTest {
    @Test
    void defineTodasLasRutasSinDuplicados() {
        var items = PhaseTwoNavigation.SECTIONS.stream().flatMap(section -> section.items().stream()).toList();

        assertThat(items).hasSize(61);
        assertThat(items).extracting(PhaseTwoNavigation.Item::route).doesNotHaveDuplicates()
            .contains("clientes", "ordenes-compra", "transferencias", "productos",
                "inventario/transferencias", "catalogo-cuentas", "fiscal/606", "activos-fijos",
                "presupuestos", "reportes/financieros");
        assertThat(PhaseTwoNavigation.SECTIONS).extracting(PhaseTwoNavigation.Section::title)
            .containsExactly("VENTAS", "COMPRAS", "CAJA Y BANCOS", "INVENTARIO", "CONTABILIDAD",
                "FISCAL", "ACTIVOS FIJOS", "FINANZAS", "REPORTES");
    }

    @Test
    void resuelveRutaPrincipalYSecundariaConLaMismaDefinicion() {
        assertThat(PhaseTwoNavigation.byRoute("/facturas")).get()
            .extracting(PhaseTwoNavigation.Item::permission).isEqualTo("facturas.ver");
        assertThat(PhaseTwoNavigation.byRoute("facturas/123/editar")).get()
            .extracting(PhaseTwoNavigation.Item::module).isEqualTo("VENTAS");
        assertThat(PhaseTwoNavigation.byRoute("inventario/transferencias")).get()
            .extracting(PhaseTwoNavigation.Item::permission).isEqualTo("inventario_transferencias.ver");
        assertThat(PhaseTwoNavigation.byRoute("fiscal/606/detalle")).get()
            .extracting(PhaseTwoNavigation.Item::module).isEqualTo("FISCAL");
        assertThat(PhaseTwoNavigation.byRoute("reportes/inventario")).get()
            .extracting(PhaseTwoNavigation.Item::permission).isEqualTo("reportes_inventario.ver");
    }

    @Test
    void conservaElOrdenSolicitadoDentroDeCadaNuevaSeccion() {
        assertThat(section("INVENTARIO").items()).extracting(PhaseTwoNavigation.Item::title)
            .containsExactly("Productos", "Categorías", "Unidades de medida", "Almacenes", "Movimientos", "Transferencias",
                "Ajustes", "Kardex", "Conteo físico");
        assertThat(section("CONTABILIDAD").items()).extracting(PhaseTwoNavigation.Item::title)
            .containsExactly("Catálogo de cuentas", "Asientos", "Libro diario", "Libro mayor", "Balanza",
                "Centros de costos", "Cierres");
        assertThat(section("FISCAL").items()).extracting(PhaseTwoNavigation.Item::title)
            .containsExactly("NCF", "e-CF", "Impuestos", "Retenciones", "606", "607", "608", "609", "IT-1", "IR-17");
        assertThat(section("ACTIVOS FIJOS").items()).extracting(PhaseTwoNavigation.Item::title)
            .containsExactly("Activos", "Depreciaciones", "Mantenimiento", "Bajas");
        assertThat(section("FINANZAS").items()).extracting(PhaseTwoNavigation.Item::title)
            .containsExactly("Presupuestos", "Flujo de efectivo", "Proyecciones", "Rentabilidad");
        assertThat(section("REPORTES").items()).extracting(PhaseTwoNavigation.Item::title)
            .containsExactly("Financieros", "Ventas", "Compras", "Inventario", "Fiscal",
                "Cuentas por cobrar", "Cuentas por pagar");
    }

    @Test
    void utilizaIconosDeLaBibliotecaActual() {
        assertThat(PhaseTwoNavigation.SECTIONS.stream().flatMap(section -> section.items().stream()))
            .allSatisfy(item -> assertThat(VaadinIcon.valueOf(item.icon())).isNotNull());
    }

    @Test
    void todasLasDefinicionesTienenUnaRutaVaadinRegistrada() {
        String principal = PhaseTwoPlaceholderView.class.getAnnotation(Route.class).value();
        var aliases = Stream.of(PhaseTwoPlaceholderView.class.getAnnotationsByType(RouteAlias.class))
            .map(RouteAlias::value);
        var registered = Stream.concat(
            Stream.concat(Stream.of(principal), aliases),
            Stream.of(CajasView.class.getAnnotation(Route.class).value(),
                ProveedoresView.class.getAnnotation(Route.class).value(),
                ProductosView.class.getAnnotation(Route.class).value(),
                UnidadesMedidaView.class.getAnnotation(Route.class).value(),
                OrdenesCompraView.class.getAnnotation(Route.class).value(),
                OperacionesCajaView.class.getAnnotation(Route.class).value(),
                CuentasBancariasView.class.getAnnotation(Route.class).value(),
                IngresosView.class.getAnnotation(Route.class).value(),
                EgresosView.class.getAnnotation(Route.class).value(),
                TransferenciasView.class.getAnnotation(Route.class).value(),
                ConciliacionBancariaView.class.getAnnotation(Route.class).value())).toList();

        assertThat(registered).doesNotHaveDuplicates();
        assertThat(registered).containsExactlyInAnyOrderElementsOf(
            PhaseTwoNavigation.SECTIONS.stream().flatMap(section -> section.items().stream())
                .map(PhaseTwoNavigation.Item::route).toList());
    }

    private static PhaseTwoNavigation.Section section(String title) {
        return PhaseTwoNavigation.SECTIONS.stream().filter(section -> section.title().equals(title)).findFirst().orElseThrow();
    }
}
