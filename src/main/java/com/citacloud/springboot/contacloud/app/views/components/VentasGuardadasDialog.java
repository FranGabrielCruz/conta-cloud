package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.VentaGuardadaResumenDto;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.VentaGuardadaService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class VentasGuardadasDialog extends Dialog {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VentaGuardadaService service;
    private final String token;
    private final Runnable guardarVentaActual;
    private final Consumer<UUID> recuperar;
    private final Runnable alCambiar;
    private final Runnable alCerrar;
    private final TextField buscar = new TextField();
    private final Grid<VentaGuardadaResumenDto> grid = new Grid<>();
    private final AppPagination paginacion;
    private final AppActionButton guardarActual;
    private final boolean puedeConsultar;
    private List<VentaGuardadaResumenDto> visibles = List.of();

    public VentasGuardadasDialog(VentaGuardadaService service, String token, boolean ventaActualDisponible,
                                 Runnable guardarVentaActual, Consumer<UUID> recuperar,
                                 Runnable alCambiar, Runnable alCerrar) {
        this.service = Objects.requireNonNull(service);
        this.token = token;
        this.guardarVentaActual = Objects.requireNonNull(guardarVentaActual);
        this.recuperar = Objects.requireNonNull(recuperar);
        this.alCambiar = Objects.requireNonNull(alCambiar);
        this.alCerrar = Objects.requireNonNull(alCerrar);
        this.puedeConsultar = can("ventas_guardadas.consultar");

        setHeaderTitle("VENTAS GUARDADAS");
        setWidth("min(1060px, 96vw)");
        setMaxHeight("94vh");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);
        addClassName("cc-saved-sales-dialog");

        Button cerrarCabecera = compactIcon(VaadinIcon.CLOSE, "Cerrar", e -> close());
        getHeader().add(cerrarCabecera);

        buscar.setPlaceholder("Buscar por ID, referencia o cliente...");
        buscar.setPrefixComponent(VaadinIcon.SEARCH.create());
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.setValueChangeTimeout(300);
        buscar.setEnabled(puedeConsultar);

        guardarActual = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN,
                "Guardar venta actual", e -> this.guardarVentaActual.run());
        guardarActual.setVisible(can("ventas_guardadas.guardar"));
        actualizarVentaActual(ventaActualDisponible);

        configurarGrid();
        paginacion = new AppPagination(r -> cargar(), 10, 10);
        paginacion.setVisible(puedeConsultar);
        buscar.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                paginacion.reset();
                cargar();
            }
        });

        HorizontalLayout herramientas = new HorizontalLayout(buscar, guardarActual);
        herramientas.addClassName("cc-saved-sales-tools");
        herramientas.setWidthFull();
        herramientas.setAlignItems(HorizontalLayout.Alignment.END);
        herramientas.setFlexGrow(1, buscar);

        Div body = new Div(herramientas, grid, paginacion);
        body.addClassName("cc-saved-sales-body");
        add(body);
        getFooter().add(new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN, "Cerrar", e -> close()));

        registrarTeclado();
        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                cargar();
                buscar.focus();
            }
        });
        addClosedListener(e -> this.alCerrar.run());
    }

    public void actualizarVentaActual(boolean disponible) {
        guardarActual.setEnabled(disponible);
        guardarActual.setTooltipText(disponible
                ? "Guardar venta actual" : "No hay una venta en curso para guardar.");
    }

    public void ventaActualGuardada() {
        actualizarVentaActual(false);
        buscar.clear();
        paginacion.reset();
        cargar();
        alCambiar.run();
        buscar.focus();
    }

    public void refrescarListado() {
        cargar();
        alCambiar.run();
    }

    private void configurarGrid() {
        grid.addColumn(VentaGuardadaResumenDto::numero).setHeader("ID").setWidth("135px").setFlexGrow(0);
        grid.addColumn(v -> DATE.format(v.fechaHora().atZoneSameInstant(ZoneId.systemDefault())))
                .setHeader("Fecha/Hora").setWidth("175px").setFlexGrow(0);
        grid.addColumn(v -> value(v.referencia())).setHeader("Referencia").setFlexGrow(1);
        grid.addComponentColumn(this::cliente).setHeader("Cliente").setFlexGrow(1);
        grid.addColumn(VentaGuardadaResumenDto::items).setHeader("Ítems").setWidth("80px")
                .setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(this::total).setHeader("Total").setWidth("155px")
                .setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setWidth("100px").setFlexGrow(0);
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        // El repositorio ya pagina de 10 en 10. El Grid solo autoajusta su altura
        // a las filas de la página actual; no provoca la carga del histórico completo.
        grid.setAllRowsVisible(true);
        grid.setEmptyStateText(puedeConsultar ? "No hay ventas guardadas."
                : "No tienes permiso para consultar ventas guardadas.");
        grid.addClassName("cc-saved-sales-grid");
    }

    private Component cliente(VentaGuardadaResumenDto venta) {
        Span cliente = new Span(value(venta.cliente()));
        cliente.addClassName("cc-saved-sales-customer");
        cliente.getElement().setAttribute("title", value(venta.cliente()));
        return cliente;
    }

    private Component total(VentaGuardadaResumenDto venta) {
        Span total = new Span(venta.moneda() + " " + money(venta.total()));
        total.addClassName("cc-saved-sales-total");
        return total;
    }

    private Component acciones(VentaGuardadaResumenDto venta) {
        HorizontalLayout acciones = new HorizontalLayout();
        acciones.addClassName("cc-grid-actions");
        acciones.setPadding(false);
        acciones.setSpacing(false);
        if (can("ventas_guardadas.recuperar")) {
            acciones.add(new AppActionButton(ActionType.RECOVER, ButtonSize.GRID_ACTION,
                    "Recuperar venta", e -> recuperar.accept(venta.id())));
        }
        if (can("ventas_guardadas.descartar")) {
            acciones.add(new AppActionButton(ActionType.DELETE, ButtonSize.GRID_ACTION,
                    "Descartar venta", e -> confirmarDescarte(venta)));
        }
        return acciones;
    }

    private void confirmarDescarte(VentaGuardadaResumenDto venta) {
        Dialog confirmacion = new Dialog();
        confirmacion.setHeaderTitle("DESCARTAR VENTA");
        confirmacion.setWidth("min(560px, 94vw)");
        confirmacion.setCloseOnOutsideClick(false);
        confirmacion.addClassName("cc-discard-saved-sale-dialog");
        confirmacion.getHeader().add(compactIcon(VaadinIcon.CLOSE, "Cerrar", e -> confirmacion.close()));
        confirmacion.add(new Paragraph("¿Descartar la venta " + venta.numero() + "?"),
                new Paragraph("Esta venta dejará de estar disponible para recuperarse."));

        AppActionButton descartar = new AppActionButton(ActionType.DELETE, ButtonSize.MAIN,
                "Descartar venta", e -> {
            try {
                service.descartar(venta.id(), token);
                confirmacion.close();
                Notification.show("Venta " + venta.numero() + " descartada.");
                paginacion.reset();
                cargar();
                alCambiar.run();
            } catch (RuntimeException ex) {
                error(ex, "No fue posible descartar la venta.");
            }
        });
        AppActionButton cancelar = new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN,
                "Cancelar", e -> confirmacion.close());
        confirmacion.getFooter().add(descartar, cancelar);
        confirmacion.open();
    }

    private void cargar() {
        if (!puedeConsultar) {
            visibles = List.of();
            grid.setItems(visibles);
            return;
        }
        try {
            var request = paginacion.currentRequest();
            var page = service.buscar(token, buscar.getValue(), request.page());
            visibles = page.getContent();
            grid.setItems(visibles);
            paginacion.setTotal(page.getTotalElements());
            grid.deselectAll();
        } catch (RuntimeException ex) {
            error(ex, "No fue posible consultar las ventas guardadas.");
        }
    }

    private void registrarTeclado() {
        Shortcuts.addShortcutListener(this, () -> mover(1), Key.ARROW_DOWN);
        Shortcuts.addShortcutListener(this, () -> mover(-1), Key.ARROW_UP);
        Shortcuts.addShortcutListener(this, () -> {
            if (can("ventas_guardadas.recuperar")) {
                grid.getSelectedItems().stream().findFirst().ifPresent(v -> recuperar.accept(v.id()));
            }
        }, Key.ENTER);
    }

    private void mover(int paso) {
        if (visibles.isEmpty()) return;
        VentaGuardadaResumenDto actual = grid.getSelectedItems().stream().findFirst().orElse(null);
        int indice = actual == null
                ? (paso > 0 ? 0 : visibles.size() - 1)
                : Math.max(0, Math.min(visibles.size() - 1, visibles.indexOf(actual) + paso));
        grid.select(visibles.get(indice));
        grid.scrollToIndex(indice);
    }

    private static Button compactIcon(VaadinIcon icon, String tooltip,
                                      com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button button = new Button(icon.create(), listener);
        button.addClassName("cc-pos-icon-button");
        button.setTooltipText(tooltip);
        button.getElement().setAttribute("aria-label", tooltip);
        return button;
    }

    private static boolean can(String permission) {
        return TenantContext.principalActual().permisos().contains(permission);
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String money(BigDecimal value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    private static void error(RuntimeException error, String fallback) {
        com.citacloud.springboot.contacloud.app.errors.UiErrorHandler.show(error, fallback);
    }
}
