package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.vaadin.flow.component.applayout.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.*;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;

@PermitAll
public class MainLayout extends AppLayout {
    public MainLayout() {
        var logo = new H2("ContaCloud"); logo.getStyle().set("margin", "0").set("color", "#155eef");
        var principal = TenantContext.principalActual();
        var contexto = new Span(principal.empresaCodigo() + " · " + principal.nombre());
        var header = new HorizontalLayout(new DrawerToggle(), logo, contexto);
        header.setWidthFull(); header.setAlignItems(HorizontalLayout.Alignment.CENTER); header.expand(logo);
        header.getStyle().set("padding", "0 .75rem"); addToNavbar(header);

        var nav = new SideNav();
        nav.addItem(new SideNavItem("Dashboard", DashboardView.class, VaadinIcon.HOME.create()));
        nav.addItem(new SideNavItem("Datos de empresa", EmpresaView.class, VaadinIcon.OFFICE.create()));
        var administracion = new SideNavItem("Administracion");
        administracion.addItem(new SideNavItem("Sucursales", PlaceholderView.class, VaadinIcon.BUILDING.create()));
        administracion.addItem(new SideNavItem("Usuarios", PlaceholderView.class, VaadinIcon.USERS.create()));
        nav.addItem(administracion);
        var configuracion = new SideNavItem("Configuracion");
        configuracion.addItem(new SideNavItem("Monedas y tasas", PlaceholderView.class, VaadinIcon.COIN_PILES.create()));
        configuracion.addItem(new SideNavItem("Impuestos", PlaceholderView.class, VaadinIcon.CALC.create()));
        configuracion.addItem(new SideNavItem("Periodos fiscales", PlaceholderView.class, VaadinIcon.CALENDAR.create()));
        nav.addItem(configuracion);
        addToDrawer(nav); setPrimarySection(Section.DRAWER);
    }
}
