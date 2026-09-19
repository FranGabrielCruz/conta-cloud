package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.services.DashboardService;
import com.citacloud.springboot.contacloud.app.views.components.AppPageHeader;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Route(value = "dashboard", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | ContaCloud")
@PermitAll
public class DashboardView extends VerticalLayout {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public DashboardView(DashboardService service) {
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();

        var resumen = service.obtener();
        add(new AppPageHeader("Hola, " + resumen.empresa(),
            "Este es el resumen administrativo y de configuración de tu empresa."));

        var metricas = new Div(
            metrica("Usuarios", resumen.metricas().usuarios()),
            metrica("Roles", resumen.metricas().roles()),
            metrica("Sucursales", resumen.metricas().sucursales()),
            metrica("Monedas", resumen.metricas().monedas()),
            metrica("Impuestos", resumen.metricas().impuestos()));
        metricas.addClassName("cc-metrics-grid");
        add(metricas);

        var columnas = new Div(configuracion(resumen), accesosRapidos(resumen));
        columnas.addClassName("cc-dashboard-columns");
        add(columnas, actividadReciente(resumen));
    }

    private Div metrica(String titulo, long valor) {
        var tarjeta = new Div(new Span(titulo), new H2(String.valueOf(valor)));
        tarjeta.addClassNames("cc-card", "cc-metric-card");
        return tarjeta;
    }

    private Div configuracion(DashboardService.Resumen resumen) {
        var tarjeta = new Div();
        tarjeta.addClassNames("cc-card", "cc-section-card");
        tarjeta.add(new H3("Configuración pendiente"));
        if (resumen.pendientes().isEmpty()) {
            var estado = new Paragraph("La configuración esencial está completa.");
            estado.addClassName("cc-success-text");
            tarjeta.add(estado);
        } else {
            var lista = new com.vaadin.flow.component.html.UnorderedList();
            resumen.pendientes().forEach(item -> lista.add(new com.vaadin.flow.component.html.ListItem(item)));
            tarjeta.add(lista);
        }
        return tarjeta;
    }

    private Div accesosRapidos(DashboardService.Resumen resumen) {
        var tarjeta = new Div();
        tarjeta.addClassNames("cc-card", "cc-section-card");
        tarjeta.add(new H3("Accesos rápidos"));
        if (resumen.accesosRapidos().isEmpty()) {
            tarjeta.add(new Paragraph("No hay acciones disponibles para tus permisos actuales."));
        } else {
            var enlaces = new Div();
            enlaces.addClassName("cc-quick-links");
            resumen.accesosRapidos().forEach(acceso -> {
                var enlace = new Anchor("/" + acceso.ruta(), acceso.titulo());
                enlace.addClassName("cc-quick-link");
                enlaces.add(enlace);
            });
            tarjeta.add(enlaces);
        }
        return tarjeta;
    }

    private Div actividadReciente(DashboardService.Resumen resumen) {
        var tarjeta = new Div();
        tarjeta.addClassNames("cc-card", "cc-section-card", "cc-activity-card");
        tarjeta.add(new H3("Actividad reciente"));
        if (resumen.actividadReciente().isEmpty()) {
            tarjeta.add(new Paragraph("Todavía no hay actividad registrada para esta empresa."));
            return tarjeta;
        }
        resumen.actividadReciente().forEach(actividad -> {
            String fecha = FECHA_HORA.format(actividad.fechaHora().atZone(ZoneId.systemDefault()));
            var fila = new HorizontalLayout(
                new Div(new Span(actividad.accion()), new Text(" · "), new Span(actividad.entidad())),
                new Span(fecha));
            fila.addClassName("cc-activity-row");
            fila.setWidthFull();
            fila.expand(fila.getComponentAt(0));
            tarjeta.add(fila);
        });
        return tarjeta;
    }
}
