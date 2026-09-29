package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.views.components.AppPageHeader;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import jakarta.annotation.security.PermitAll;

import java.util.Map;

@Route(value = "modulo", layout = MainLayout.class)
@RouteAlias(value = "sucursales", layout = MainLayout.class)
@RouteAlias(value = "monedas", layout = MainLayout.class)
@RouteAlias(value = "condiciones-pago", layout = MainLayout.class)
@PageTitle("Módulo | ContaCloud")
@PermitAll
public class PlaceholderView extends VerticalLayout implements BeforeEnterObserver {

    private static final Map<String, Modulo> MODULOS = Map.ofEntries(
        Map.entry("sucursales", new Modulo("Sucursales", "SUCURSAL_VER")),
        Map.entry("monedas", new Modulo("Monedas", "MONEDA_VER")),
        Map.entry("condiciones-pago", new Modulo("Condiciones de pago", "CONDICION_PAGO_VER")));

    public PlaceholderView() {
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String ruta = event.getLocation().getPath();
        Modulo modulo = MODULOS.get(ruta);
        if (modulo == null || !TenantContext.principalActual().permisos().contains(modulo.permiso())) {
            Notification.show("No tienes permiso para acceder a esta opción.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        removeAll();
        add(new AppPageHeader(modulo.titulo(),
            "La base segura de este módulo está preparada para continuar con su flujo CRUD."));
    }

    private record Modulo(String titulo, String permiso) {}
}
