package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.ConfiguracionDescuentoService;
import com.citacloud.springboot.contacloud.app.services.ConfiguracionRecargoService;
import com.citacloud.springboot.contacloud.app.views.components.AppPageHeader;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Route(value = "configuracion/facturacion/descuentos-recargos", layout = MainLayout.class)
@PageTitle("Descuentos y recargos | ContaCloud")
@PermitAll
public class ConfiguracionDescuentosRecargosView extends VerticalLayout implements BeforeEnterObserver {
    private final ConfiguracionDescuentoService descuentoService;
    private final ConfiguracionRecargoService recargoService;

    public ConfiguracionDescuentosRecargosView(ConfiguracionDescuentoService descuentoService,
            ConfiguracionRecargoService recargoService) {
        this.descuentoService = descuentoService;
        this.recargoService = recargoService;
        addClassNames("cc-page", "cc-adjustments-config-page");
        setPadding(true);
        setSpacing(true);
        setWidthFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Set<String> permisos = TenantContext.principalActual().permisos();
        boolean verDescuentos = permisos.contains("descuentos_configuracion.ver");
        boolean verRecargos = permisos.contains("recargos_configuracion.ver");
        if (!verDescuentos && !verRecargos) {
            Notification.show("No tienes permiso para consultar la configuración de descuentos y recargos.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        construir(verDescuentos, verRecargos);
    }

    private void construir(boolean verDescuentos, boolean verRecargos) {
        removeAll();
        add(new AppPageHeader("Descuentos y recargos",
                "Configura las políticas, límites, tipos y motivos aplicables a descuentos y recargos en facturación."));

        Tabs tabs = new Tabs();
        tabs.addClassName("cc-adjustments-tabs");
        Div paginas = new Div();
        paginas.addClassName("cc-adjustments-tab-pages");
        List<Tab> opciones = new ArrayList<>();
        List<Component> contenidos = new ArrayList<>();

        if (verDescuentos) {
            Tab tab = new Tab("DESCUENTOS");
            ConfiguracionDescuentosView contenido = new ConfiguracionDescuentosView(descuentoService);
            contenido.prepararIntegrada();
            opciones.add(tab);
            contenidos.add(contenido);
            tabs.add(tab);
            paginas.add(contenido);
        }
        if (verRecargos) {
            Tab tab = new Tab("RECARGOS");
            ConfiguracionRecargosView contenido = new ConfiguracionRecargosView(recargoService);
            contenido.prepararIntegrada();
            opciones.add(tab);
            contenidos.add(contenido);
            tabs.add(tab);
            paginas.add(contenido);
        }

        for (int i = 0; i < contenidos.size(); i++) contenidos.get(i).setVisible(i == 0);
        tabs.addSelectedChangeListener(event -> {
            int seleccion = opciones.indexOf(event.getSelectedTab());
            for (int i = 0; i < contenidos.size(); i++) contenidos.get(i).setVisible(i == seleccion);
        });
        add(tabs, paginas);
    }
}
