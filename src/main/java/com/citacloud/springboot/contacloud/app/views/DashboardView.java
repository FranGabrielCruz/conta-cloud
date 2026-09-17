package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.services.DashboardService;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

@Route(value = "", layout = MainLayout.class) @PageTitle("Dashboard | ContaCloud") @PermitAll
public class DashboardView extends VerticalLayout {
    public DashboardView(DashboardService service) {
        setPadding(true); setSpacing(true); var r = service.obtener();
        add(new H1("Hola, " + r.empresa()), new Paragraph("Resumen de la configuracion de tu empresa"));
        var cards = new HorizontalLayout(card("Sucursales activas", String.valueOf(r.sucursalesActivas())),
            card("Usuarios activos", String.valueOf(r.usuariosActivos())), card("Moneda base", r.monedaBase()),
            card("Periodo actual", r.periodoActual()));
        cards.setWidthFull(); cards.getStyle().set("flex-wrap", "wrap"); add(cards);
    }
    private Div card(String titulo, String valor) {
        var d = new Div(new Span(titulo), new H2(valor)); d.setWidth("220px");
        d.getStyle().set("padding", "1.25rem").set("border", "1px solid #e2e8f0").set("border-radius", "12px").set("background", "white"); return d;
    }
}
