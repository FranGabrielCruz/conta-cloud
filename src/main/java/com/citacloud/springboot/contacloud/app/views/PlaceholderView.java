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
@RouteAlias(value = "tasas-cambio", layout = MainLayout.class)
@RouteAlias(value = "impuestos", layout = MainLayout.class)
@RouteAlias(value = "comprobantes-fiscales", layout = MainLayout.class)
@RouteAlias(value = "secuencias", layout = MainLayout.class)
@RouteAlias(value = "condiciones-pago", layout = MainLayout.class)
@RouteAlias(value = "periodos-fiscales", layout = MainLayout.class)
@RouteAlias(value = "configuracion-contable", layout = MainLayout.class)
@RouteAlias(value = "bases-datos", layout = MainLayout.class)
@RouteAlias(value = "migraciones", layout = MainLayout.class)
@PageTitle("Módulo | ContaCloud")
@PermitAll
public class PlaceholderView extends VerticalLayout implements BeforeEnterObserver {

    private static final Map<String, Modulo> MODULOS = Map.ofEntries(
        Map.entry("sucursales", new Modulo("Sucursales", "SUCURSAL_VER")),
        Map.entry("monedas", new Modulo("Monedas", "MONEDA_VER")),
        Map.entry("tasas-cambio", new Modulo("Tasas de cambio", "TASA_CAMBIO_VER")),
        Map.entry("impuestos", new Modulo("Impuestos", "IMPUESTO_VER")),
        Map.entry("comprobantes-fiscales", new Modulo("Comprobantes fiscales", "COMPROBANTE_VER")),
        Map.entry("secuencias", new Modulo("Secuencias", "SECUENCIA_VER")),
        Map.entry("condiciones-pago", new Modulo("Condiciones de pago", "CONDICION_PAGO_VER")),
        Map.entry("periodos-fiscales", new Modulo("Períodos fiscales", "PERIODO_VER")),
        Map.entry("configuracion-contable", new Modulo("Configuración contable", "CONFIGURACION_CONTABLE_VER")),
        Map.entry("bases-datos", new Modulo("Bases de datos", "bases_datos.ver")),
        Map.entry("migraciones", new Modulo("Migraciones", "migraciones.ver")));

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
