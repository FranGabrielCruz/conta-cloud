package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.services.ContextoUsuarioService;
import com.citacloud.springboot.contacloud.app.services.CompanyConfigurationService;
import com.citacloud.springboot.contacloud.app.services.MenuService;
import com.citacloud.springboot.contacloud.app.views.components.CompanyLogoAvatar;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Year;

@PermitAll
public class MainLayout extends AppLayout {

    private final Div contentHost = new Div();
    private final CompanyConfigurationService companyConfiguration;
    private final ContextoUsuarioService contextoUsuario;
    private CompanyLogoAvatar menuAvatar;
    private Span companyBadge;
    private Span menuCompanyName;
    private HasElement currentContent;

    public MainLayout(ContextoUsuarioService contextoUsuario, MenuService menuService,
                      CompanyConfigurationService companyConfiguration,
                      AuthenticationContext authenticationContext,
                      @Value("${contacloud.version}") String version,
                      @Value("${contacloud.session.warning-after}") Duration warningAfter,
                      @Value("${contacloud.session.expire-after}") Duration expireAfter) {
        this.companyConfiguration = companyConfiguration;
        this.contextoUsuario = contextoUsuario;
        var contexto = contextoUsuario.obtener();
        addToNavbar(crearHeader(contexto, authenticationContext, menuService));
        addToDrawer(crearDrawer(menuService));

        contentHost.addClassName("cc-content");
        var inactivityGuard = new InactivityGuard(authenticationContext, warningAfter, expireAfter);
        var shell = new VerticalLayout(inactivityGuard, contentHost, crearFooter(version));
        shell.addClassName("cc-shell");
        shell.setPadding(false);
        shell.setSpacing(false);
        shell.setSizeFull();
        shell.expand(contentHost);
        setContent(shell);
        setPrimarySection(Section.DRAWER);
    }

    private Component crearHeader(ContextoUsuarioService.Contexto contexto,
                                   AuthenticationContext authenticationContext,
                                   MenuService menuService) {
        var marca = new RouterLink();
        marca.setRoute(DashboardView.class);
        marca.addClassName("cc-brand");
        marca.add(new Span("C") {{ addClassName("cc-brand-mark"); }},
            new Span("ContaCloud") {{ addClassName("cc-brand-name"); }});

        companyBadge = new Span(contexto.empresa());
        companyBadge.addClassName("cc-context-badge");
        companyBadge.getElement().setAttribute("title", "Empresa activa: " + contexto.empresaCodigo());

        var usuario = new Button(contexto.usuario(), VaadinIcon.USER.create());
        usuario.addClassName("cc-user-button");
        usuario.getElement().setAttribute("aria-label", "Abrir menú de usuario");
        crearMenuUsuario(usuario, contexto, authenticationContext, menuService);

        var header = new HorizontalLayout(new DrawerToggle(), marca, companyBadge, usuario);
        header.addClassName("cc-header");
        header.setWidthFull();
        header.setPadding(false);
        header.setSpacing(true);
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.expand(marca);
        return header;
    }

    private void crearMenuUsuario(Button target, ContextoUsuarioService.Contexto contexto,
                                  AuthenticationContext authenticationContext,
                                  MenuService menuService) {
        var menu = new ContextMenu(target);
        menu.setOpenOnClick(true);

        menuAvatar = new CompanyLogoAvatar(contexto.inicialEmpresa(), false);
        menuAvatar.refresh(companyConfiguration.currentLogoAvailable());
        menuCompanyName = new Span(contexto.empresa());
        menuCompanyName.addClassName("cc-user-company");
        var datos = new VerticalLayout(new Span(contexto.usuario()) {{ addClassName("cc-user-name"); }}, menuCompanyName);
        datos.addClassName("cc-user-info");
        datos.setPadding(false);
        datos.setSpacing(false);
        var perfil = new HorizontalLayout(menuAvatar, datos);
        perfil.addClassName("cc-user-profile");
        perfil.setAlignItems(HorizontalLayout.Alignment.CENTER);
        menu.addComponent(perfil);
        menu.addSeparator();

        if (menuService.mostrarConfiguracionUsuario()) {
            menu.addItem(filaMenu(VaadinIcon.COG.create(), "Configuración"),
                event -> UI.getCurrent().navigate("configuracion"));
        }

        var temaOscuro = new Checkbox();
        temaOscuro.setAriaLabel("Modo oscuro");
        var iconoTema = VaadinIcon.MOON.create();
        iconoTema.addClassName("cc-theme-toggle-control");
        iconoTema.getElement().addEventListener("click",
            event -> temaOscuro.setValue(!temaOscuro.getValue()));
        var textoTema = new Span("Modo oscuro");
        textoTema.addClassNames("cc-theme-label", "cc-theme-toggle-control");
        textoTema.addClickListener(event -> temaOscuro.setValue(!temaOscuro.getValue()));
        var filaTema = new HorizontalLayout(iconoTema, textoTema);
        filaTema.addClassName("cc-menu-row");
        filaTema.setAlignItems(HorizontalLayout.Alignment.CENTER);
        filaTema.add(temaOscuro);
        var itemTema = menu.addItem(filaTema);
        itemTema.setCheckable(false);
        target.getElement().executeJs("return localStorage.getItem('contacloud.theme') === 'dark'")
            .then(Boolean.class, temaOscuro::setValue);
        temaOscuro.addValueChangeListener(event -> aplicarTema(event.getValue()));

        menu.addSeparator();
        menu.addItem(filaMenu(VaadinIcon.SIGN_OUT.create(), "Cerrar sesión"),
            event -> authenticationContext.logout());
    }

    private HorizontalLayout filaMenu(Icon icono, String texto) {
        var fila = new HorizontalLayout(icono, new Span(texto));
        fila.addClassName("cc-menu-row");
        fila.setAlignItems(HorizontalLayout.Alignment.CENTER);
        return fila;
    }

    private Component crearDrawer(MenuService menuService) {
        var drawer = new Div();
        drawer.addClassName("cc-drawer");
        for (var grupo : menuService.obtener()) {
            var titulo = new Div(grupo.titulo());
            titulo.addClassName("cc-nav-label");
            var navegacion = new SideNav();
            for (var opcion : grupo.opciones()) {
                navegacion.addItem(new SideNavItem(opcion.titulo(), opcion.ruta(), icono(opcion.icono())));
            }
            drawer.add(titulo, navegacion);
        }
        return drawer;
    }

    private Icon icono(String nombre) {
        try {
            return VaadinIcon.valueOf(nombre).create();
        } catch (IllegalArgumentException ex) {
            return VaadinIcon.CIRCLE_THIN.create();
        }
    }

    private Component crearFooter(String version) {
        var izquierda = new Span("© " + Year.now().getValue() + " ContaCloud");
        var derecha = new Span("Versión " + version + " · Soporte · Privacidad");
        var footer = new Footer(izquierda, derecha);
        footer.addClassName("cc-footer");
        return footer;
    }

    private void aplicarTema(boolean oscuro) {
        UI.getCurrent().getPage().executeJs("""
            const theme = $0 ? 'dark' : 'light';
            localStorage.setItem('contacloud.theme', theme);
            document.documentElement.setAttribute('theme', theme);
            """, oscuro);
    }

    public void refreshCompanyLogo() {
        menuAvatar.refresh(companyConfiguration.currentLogoAvailable());
    }

    public void refreshCompanyContext() {
        var contexto = contextoUsuario.obtener();
        companyBadge.setText(contexto.empresa());
        companyBadge.getElement().setAttribute("title", "Empresa activa: " + contexto.empresaCodigo());
        menuCompanyName.setText(contexto.empresa());
        menuAvatar.setInitial(contexto.inicialEmpresa());
        refreshCompanyLogo();
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        if (currentContent != null) currentContent.getElement().removeFromParent();
        contentHost.getElement().appendChild(content.getElement());
        currentContent = content;
    }
}
