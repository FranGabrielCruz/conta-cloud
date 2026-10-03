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
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseTwoNavigationTest {
    @Test
    void defineTodasLasRutasSinDuplicados() {
        var items = PhaseTwoNavigation.SECTIONS.stream().flatMap(section -> section.items().stream()).toList();

        assertThat(items).hasSize(20);
        assertThat(items).extracting(PhaseTwoNavigation.Item::route).doesNotHaveDuplicates()
            .containsExactly(
                "clientes", "cotizaciones", "facturas", "notas-credito", "notas-debito",
                "recibos", "cuentas-por-cobrar", "proveedores", "ordenes-compra",
                "facturas-proveedores", "notas-credito-proveedores", "pagos-proveedores",
                "cuentas-por-pagar", "cajas", "operaciones-caja", "cuentas-bancarias", "ingresos", "egresos",
                "transferencias", "conciliacion-bancaria");
    }

    @Test
    void resuelveRutaPrincipalYSecundariaConLaMismaDefinicion() {
        assertThat(PhaseTwoNavigation.byRoute("/facturas")).get()
            .extracting(PhaseTwoNavigation.Item::permission).isEqualTo("facturas.ver");
        assertThat(PhaseTwoNavigation.byRoute("facturas/123/editar")).get()
            .extracting(PhaseTwoNavigation.Item::module).isEqualTo("VENTAS");
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
}
