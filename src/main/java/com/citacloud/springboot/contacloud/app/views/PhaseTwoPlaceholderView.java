package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.services.PhaseTwoNavigation;
import com.citacloud.springboot.contacloud.app.views.components.AppPageHeader;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
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
@RouteAlias(value = "proveedores", layout = MainLayout.class)
@RouteAlias(value = "ordenes-compra", layout = MainLayout.class)
@RouteAlias(value = "facturas-proveedores", layout = MainLayout.class)
@RouteAlias(value = "notas-credito-proveedores", layout = MainLayout.class)
@RouteAlias(value = "pagos-proveedores", layout = MainLayout.class)
@RouteAlias(value = "cuentas-por-pagar", layout = MainLayout.class)
@RouteAlias(value = "cuentas-bancarias", layout = MainLayout.class)
@RouteAlias(value = "ingresos", layout = MainLayout.class)
@RouteAlias(value = "egresos", layout = MainLayout.class)
@RouteAlias(value = "transferencias", layout = MainLayout.class)
@RouteAlias(value = "conciliacion-bancaria", layout = MainLayout.class)
@PageTitle("Fase 2 | ContaCloud")
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
        add(new AppPageHeader(definition.title(), definition.description()));
    }
}
