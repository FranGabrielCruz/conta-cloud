package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.views.components.AppPageHeader;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "configuracion", layout = MainLayout.class)
@PageTitle("Configuración | ContaCloud")
@PermitAll
public class ConfiguracionView extends VerticalLayout {

    public ConfiguracionView() {
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        add(new AppPageHeader("Configuración",
            "Configura los datos generales, sucursales y monedas utilizadas por la empresa."));

        var tarjetas = new Div();
        tarjetas.addClassName("cc-settings-grid");
        var permisos = TenantContext.principalActual().permisos();
        if (permisos.contains("EMPRESA_VER"))
            tarjetas.add(tarjeta("Datos de empresa", "Información legal, fiscal y de contacto.", "empresa"));
        if (permisos.contains("SUCURSAL_VER"))
            tarjetas.add(tarjeta("Sucursales", "Organización y sucursal principal.", "sucursales"));
        if (permisos.contains("MONEDA_VER"))
            tarjetas.add(tarjeta("Monedas", "Moneda base y monedas disponibles.", "monedas"));
        if (tarjetas.getComponentCount() == 0)
            tarjetas.add(new Paragraph("No hay opciones disponibles para tus permisos actuales."));
        add(tarjetas);
    }

    private Div tarjeta(String titulo, String descripcion, String ruta) {
        var enlace = new Anchor("/" + ruta, "Abrir");
        enlace.addClassName("cc-quick-link");
        var tarjeta = new Div(new H3(titulo), new Paragraph(descripcion), enlace);
        tarjeta.addClassNames("cc-card", "cc-section-card");
        return tarjeta;
    }
}
