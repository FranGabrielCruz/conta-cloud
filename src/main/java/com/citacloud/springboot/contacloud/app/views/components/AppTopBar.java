package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.TerminalPosDto;
import com.citacloud.springboot.contacloud.app.services.ContextoUsuarioService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Barra global única de la aplicación, con presentación normal y operativa de punto de venta. */
public final class AppTopBar extends HorizontalLayout {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ACCESSIBLE_DATE =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.forLanguageTag("es-DO"));

    private final Span companyBadge = new Span();
    private final Button userButton = new Button(VaadinIcon.USER.create());
    private final Span posSeparator = new Span();
    private final Span posTitle = new Span("PUNTO DE VENTA");
    private final Div posCashContext = new Div();
    private final Div posDateContext = new Div();
    private final Div spacer = new Div();
    private String defaultUser;

    public AppTopBar(ContextoUsuarioService.Contexto contexto) {
        Objects.requireNonNull(contexto, "El contexto de usuario es obligatorio.");
        defaultUser = contexto.usuario();

        Anchor brand = new Anchor("/dashboard");
        brand.addClassName("cc-brand");
        Span mark = new Span("C");
        mark.addClassName("cc-brand-mark");
        Span name = new Span("ContaCloud");
        name.addClassName("cc-brand-name");
        brand.add(mark, name);

        posSeparator.addClassName("cc-header-pos-separator");
        posSeparator.getElement().setAttribute("aria-hidden", "true");
        posTitle.addClassName("cc-header-pos-title");
        posCashContext.addClassName("cc-header-pos-cash-host");
        posDateContext.addClassName("cc-header-pos-date-host");
        spacer.addClassName("cc-header-spacer");

        companyBadge.addClassName("cc-context-badge");
        userButton.addClassName("cc-user-button");
        userButton.getElement().setAttribute("aria-label", "Abrir menú de usuario");
        refreshContext(contexto);

        add(new DrawerToggle(), brand, posSeparator, posTitle, spacer,
                companyBadge, posCashContext, userButton, posDateContext);
        addClassName("cc-header");
        setWidthFull();
        setPadding(false);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        expand(spacer);
        showDefaultMode();
    }

    public Button userMenuTarget() {
        return userButton;
    }

    public void refreshContext(ContextoUsuarioService.Contexto contexto) {
        Objects.requireNonNull(contexto, "El contexto de usuario es obligatorio.");
        defaultUser = contexto.usuario();
        companyBadge.setText(contexto.empresa());
        companyBadge.getElement().setAttribute("title", "Empresa activa: " + contexto.empresaCodigo());
        if (!isPosMode()) userButton.setText(defaultUser);
    }

    public void showPosMode(TerminalPosDto terminal, String cashier, Consumer<String> pendingAction) {
        Objects.requireNonNull(terminal, "La terminal es obligatoria.");
        Objects.requireNonNull(pendingAction, "La acción operativa es obligatoria.");
        if (!terminal.cajaAbierta()) {
            throw new IllegalArgumentException("El modo POS solo puede mostrarse con una caja abierta.");
        }
        LocalDate operatingDate = Objects.requireNonNull(
                terminal.fechaOperativa(), "La fecha operativa es obligatoria.");
        String cashierName = text(cashier, "Usuario");
        String cashLabel = cashLabel(terminal);

        Button cash = createCashContext(cashLabel, pendingAction);
        Div date = informativeItem(VaadinIcon.CALENDAR, formatDate(operatingDate),
                "Fecha operativa: " + formatDate(operatingDate),
                "Fecha operativa " + ACCESSIBLE_DATE.format(operatingDate));
        date.addClassName("cc-header-pos-date");
        posCashContext.removeAll();
        posDateContext.removeAll();
        posCashContext.add(cash);
        posDateContext.add(date);

        companyBadge.setVisible(false);
        posSeparator.setVisible(true);
        posTitle.setVisible(true);
        posCashContext.setVisible(true);
        posDateContext.setVisible(true);
        userButton.setText(cashierName);
        userButton.addClassName("cc-header-cashier");
        userButton.setTooltipText("Cajero: " + cashierName);
        userButton.getElement().setAttribute("aria-label", "Cajero " + cashierName);
        addClassName("cc-header-pos-mode");
    }

    public void showDefaultMode() {
        posCashContext.removeAll();
        posDateContext.removeAll();
        posCashContext.setVisible(false);
        posDateContext.setVisible(false);
        posSeparator.setVisible(false);
        posTitle.setVisible(false);
        companyBadge.setVisible(true);
        userButton.setText(defaultUser);
        userButton.removeClassName("cc-header-cashier");
        userButton.setTooltipText("Abrir menú de usuario");
        userButton.getElement().setAttribute("aria-label", "Abrir menú de usuario");
        removeClassName("cc-header-pos-mode");
    }

    public boolean isPosMode() {
        return getClassNames().contains("cc-header-pos-mode");
    }

    static String cashLabel(TerminalPosDto terminal) {
        String cash = text(terminal.caja(), "Caja");
        String branch = text(terminal.sucursal(), "");
        return branch.isBlank() || cash.equalsIgnoreCase(branch) ? cash : cash + " · " + branch;
    }

    static String formatDate(LocalDate date) {
        return Objects.requireNonNull(date, "La fecha operativa es obligatoria.").format(DATE);
    }

    private static Button createCashContext(String label, Consumer<String> pendingAction) {
        Span status = new Span("●");
        status.addClassName("cc-pos-open-dot");
        Button cash = new Button(new Div(status, new Span(label)));
        cash.addClassName("cc-header-pos-cash");
        cash.setTooltipText(label + " · Estado: Abierta · Operaciones de caja");
        cash.getElement().setAttribute("aria-label", label + ", abierta. Operaciones de caja");

        ContextMenu menu = new ContextMenu(cash);
        menu.setOpenOnClick(true);
        var heading = menu.addItem(label.toUpperCase(Locale.ROOT));
        heading.setEnabled(false);
        var state = menu.addItem("● Abierta");
        state.setEnabled(false);
        menu.addSeparator();
        menu.addItem("Recuento de caja", event -> pendingAction.accept("Recuento de caja"));
        menu.addItem("Movimientos de caja", event -> pendingAction.accept("Movimientos de caja"));
        menu.addItem("Vale de caja", event -> pendingAction.accept("Vale de caja"));
        menu.addSeparator();
        menu.addItem("Cerrar caja", event -> pendingAction.accept("Cerrar caja"));
        return cash;
    }

    private static Div informativeItem(VaadinIcon iconType, String value, String tooltip, String ariaLabel) {
        var icon = iconType.create();
        icon.getElement().setAttribute("aria-hidden", "true");
        Div item = new Div(icon, new Span(value));
        item.addClassName("cc-header-pos-item");
        item.getElement().setAttribute("title", tooltip);
        item.getElement().setAttribute("aria-label", ariaLabel);
        return item;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
