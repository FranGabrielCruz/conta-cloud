package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.services.PhaseTwoNavigation;
import com.citacloud.springboot.contacloud.app.views.components.AppPageHeader;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import jakarta.annotation.security.PermitAll;

@Route(value = "clientes", layout = MainLayout.class)
@RouteAlias(value = "cotizaciones", layout = MainLayout.class)
@RouteAlias(value = "facturas", layout = MainLayout.class)
@RouteAlias(value = "notas-credito", layout = MainLayout.class)
@RouteAlias(value = "notas-debito", layout = MainLayout.class)
@RouteAlias(value = "recibos", layout = MainLayout.class)
@RouteAlias(value = "cuentas-por-cobrar", layout = MainLayout.class)
@RouteAlias(value = "cuentas-por-pagar", layout = MainLayout.class)
@RouteAlias(value = "almacenes", layout = MainLayout.class)
@RouteAlias(value = "inventario/movimientos", layout = MainLayout.class)
@RouteAlias(value = "inventario/transferencias", layout = MainLayout.class)
@RouteAlias(value = "inventario/ajustes", layout = MainLayout.class)
@RouteAlias(value = "kardex", layout = MainLayout.class)
@RouteAlias(value = "inventario/conteo-fisico", layout = MainLayout.class)
@RouteAlias(value = "catalogo-cuentas", layout = MainLayout.class)
@RouteAlias(value = "asientos", layout = MainLayout.class)
@RouteAlias(value = "libro-diario", layout = MainLayout.class)
@RouteAlias(value = "libro-mayor", layout = MainLayout.class)
@RouteAlias(value = "balanza", layout = MainLayout.class)
@RouteAlias(value = "centros-costos", layout = MainLayout.class)
@RouteAlias(value = "cierres-contables", layout = MainLayout.class)
@RouteAlias(value = "fiscal/ncf", layout = MainLayout.class)
@RouteAlias(value = "fiscal/ecf", layout = MainLayout.class)
@RouteAlias(value = "fiscal/impuestos", layout = MainLayout.class)
@RouteAlias(value = "fiscal/retenciones", layout = MainLayout.class)
@RouteAlias(value = "fiscal/606", layout = MainLayout.class)
@RouteAlias(value = "fiscal/607", layout = MainLayout.class)
@RouteAlias(value = "fiscal/608", layout = MainLayout.class)
@RouteAlias(value = "fiscal/609", layout = MainLayout.class)
@RouteAlias(value = "fiscal/it-1", layout = MainLayout.class)
@RouteAlias(value = "fiscal/ir-17", layout = MainLayout.class)
@RouteAlias(value = "activos-fijos", layout = MainLayout.class)
@RouteAlias(value = "activos-fijos/depreciaciones", layout = MainLayout.class)
@RouteAlias(value = "activos-fijos/mantenimiento", layout = MainLayout.class)
@RouteAlias(value = "activos-fijos/bajas", layout = MainLayout.class)
@RouteAlias(value = "presupuestos", layout = MainLayout.class)
@RouteAlias(value = "flujo-efectivo", layout = MainLayout.class)
@RouteAlias(value = "proyecciones", layout = MainLayout.class)
@RouteAlias(value = "rentabilidad", layout = MainLayout.class)
@RouteAlias(value = "reportes/financieros", layout = MainLayout.class)
@RouteAlias(value = "reportes/ventas", layout = MainLayout.class)
@RouteAlias(value = "reportes/compras", layout = MainLayout.class)
@RouteAlias(value = "reportes/inventario", layout = MainLayout.class)
@RouteAlias(value = "reportes/fiscal", layout = MainLayout.class)
@RouteAlias(value = "reportes/cuentas-por-cobrar", layout = MainLayout.class)
@RouteAlias(value = "reportes/cuentas-por-pagar", layout = MainLayout.class)
@PermitAll
public class PhaseTwoPlaceholderView extends VerticalLayout implements BeforeEnterObserver {
    private final ModuleAuthorization authorization;

    public PhaseTwoPlaceholderView(ModuleAuthorization authorization) {
        this.authorization = authorization;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var definition = PhaseTwoNavigation.byRoute(event.getLocation().getPath()).orElse(null);
        if (definition == null || !authorization.canAccess(definition.module(), definition.permission())) {
            Notification.show("No tienes permiso para acceder a esta opción.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        removeAll();
        UI.getCurrent().getPage().setTitle(definition.placeholderTitle() + " | ContaCloud");
        add(new AppPageHeader(definition.placeholderTitle(), definition.description()));
    }
}
