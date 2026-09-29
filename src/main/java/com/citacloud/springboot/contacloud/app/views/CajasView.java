package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Route(value = "cajas", layout = MainLayout.class)
@PageTitle("Cajas | ContaCloud")
@PermitAll
public class CajasView extends VerticalLayout implements BeforeEnterObserver, BeforeLeaveObserver {
    private final CashRegisterService service;
    private final EmpresaModuloService modulos;
    private final TextField buscar = new TextField("Buscar caja");
    private final ComboBox<CajaCatalogosDto.SucursalOpcion> sucursal = new ComboBox<>("Sucursal");
    private final Select<String> estado = new Select<>();
    private final AppGrid<CajaDto> grid = new AppGrid<>(CajaDto.class);
    private final AppPagination pagination;
    private String ordenarPor = "nombre";
    private boolean ordenAscendente = true;
    private Dialog formularioAbierto;
    private EstadoFormulario cambiosFormulario;

    public CajasView(CashRegisterService service, EmpresaModuloService modulos) {
        this.service = service;
        this.modulos = modulos;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        AppActionButton nuevo = puede("cajas.crear")
            ? new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Nueva caja", event -> formulario(null))
            : null;
        add(new AppPageHeader("CAJAS",
            "Administra las cajas utilizadas para registrar movimientos de efectivo.",
            nuevo == null ? new Component[0] : new Component[]{nuevo}));
        configurarFiltros();
        configurarGrid();
        pagination = new AppPagination(request -> cargar());
        add(filtros(), grid, pagination);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!modulos.habilitado("CAJA_BANCOS") || !puede("cajas.ver")) {
            Notification.show("No tienes permiso para acceder a esta opción.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        recargarSucursales();
        cargar();
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (formularioAbierto == null || cambiosFormulario == null || !cambiosFormulario.dirty) return;
        BeforeLeaveEvent.ContinueNavigationAction action = event.postpone();
        confirmarDescartar(() -> {
            cambiosFormulario.dirty = false;
            formularioAbierto.close();
            action.proceed();
        });
    }

    private void configurarFiltros() {
        buscar.setPlaceholder("Buscar caja...");
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.setValueChangeTimeout(400);
        sucursal.setPlaceholder("Todas");
        sucursal.setClearButtonVisible(true);
        sucursal.setItemLabelGenerator(CajaCatalogosDto.SucursalOpcion::nombre);
        estado.setLabel("Estado");
        estado.setItems("Todos", "Activas", "Inactivas");
        estado.setValue("Todos");
        buscar.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
        sucursal.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
        estado.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
    }

    private HorizontalLayout filtros() {
        HorizontalLayout layout = new HorizontalLayout(buscar, sucursal, estado);
        layout.addClassName("cc-filter-bar");
        layout.setAlignItems(Alignment.END);
        layout.setWidthFull();
        return layout;
    }

    private void configurarGrid() {
        grid.addColumn(CajaDto::nombre).setHeader("Nombre").setKey("nombre")
            .setSortable(true).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(CajaDto::sucursalNombre).setHeader("Sucursal").setKey("sucursal")
            .setSortable(true).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(CajaDto::monedaCodigo).setHeader("Moneda").setKey("moneda")
            .setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(item -> badge(item.activa())).setHeader("Estado").setKey("estado")
            .setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No hay cajas registradas.");
        grid.addSortListener(event -> {
            if (!event.isFromClient() || event.getSortOrder().isEmpty() || pagination == null) return;
            GridSortOrder<CajaDto> sort = event.getSortOrder().getFirst();
            ordenarPor = sort.getSorted().getKey();
            ordenAscendente = sort.getDirection() == SortDirection.ASCENDING;
            reiniciar();
        });
    }

    private Component acciones(CajaDto item) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassName("cc-grid-actions");
        actions.setPadding(false);
        actions.setSpacing(false);
        actions.add(new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION,
            "Ver caja", event -> detalle(item.id())));
        if (puede("cajas.editar")) actions.add(new AppActionButton(ActionType.EDIT,
            ButtonSize.GRID_ACTION, "Editar caja", event -> formulario(item.id())));
        return actions;
    }

    private void recargarSucursales() {
        try { sucursal.setItems(service.catalogos().sucursales()); }
        catch (RuntimeException ex) { error(ex, "No fue posible cargar las sucursales."); }
    }

    private void cargar() {
        try {
            var request = pagination.currentRequest();
            UUID sucursalId = sucursal.getValue() == null ? null : sucursal.getValue().id();
            var page = service.buscar(buscar.getValue(), sucursalId, estadoFiltro(), request.page(), request.size(),
                ordenarPor, ordenAscendente);
            grid.setItems(page.getContent());
            boolean filtered = !buscar.getValue().isBlank() || sucursalId != null || estadoFiltro() != null;
            grid.setEmptyStateText(filtered ? "No se encontraron cajas con los filtros seleccionados."
                : "No hay cajas registradas.");
            pagination.setTotal(page.getTotalElements());
        } catch (RuntimeException ex) { error(ex, "No fue posible cargar las cajas."); }
    }

    private Boolean estadoFiltro() {
        return switch (estado.getValue() == null ? "Todos" : estado.getValue()) {
            case "Activas" -> true;
            case "Inactivas" -> false;
            default -> null;
        };
    }

    private void reiniciar() { pagination.reset(); cargar(); }

    private void formulario(UUID id) {
        try {
            boolean nueva = id == null;
            CajaDto actual = nueva ? null : service.obtener(id);
            CajaCatalogosDto catalogs = service.catalogos();
            Dialog dialog = dialog(nueva ? "NUEVA CAJA" : "EDITAR CAJA");
            TextField nombre = new TextField("Nombre");
            nombre.setRequired(true);
            nombre.setMaxLength(120);
            ComboBox<CajaCatalogosDto.SucursalOpcion> branch = new ComboBox<>("Sucursal");
            branch.setRequired(true);
            branch.setItems(catalogs.sucursales());
            branch.setItemLabelGenerator(CajaCatalogosDto.SucursalOpcion::nombre);
            ComboBox<CajaCatalogosDto.MonedaOpcion> currency = new ComboBox<>("Moneda");
            currency.setRequired(true);
            currency.setItems(catalogs.monedas());
            currency.setItemLabelGenerator(item -> item.codigo() + " - " + item.nombre());
            Checkbox active = new Checkbox("Activa", true);
            TextArea description = new TextArea("Descripción");
            description.setMaxLength(500);
            description.addClassName("cc-dialog-span-2");

            if (actual == null) {
                catalogs.monedas().stream().filter(item -> item.id().equals(catalogs.monedaBaseId()))
                    .findFirst().ifPresent(currency::setValue);
            } else {
                nombre.setValue(actual.nombre());
                catalogs.sucursales().stream().filter(item -> item.id().equals(actual.sucursalId()))
                    .findFirst().ifPresent(branch::setValue);
                catalogs.monedas().stream().filter(item -> item.id().equals(actual.monedaId()))
                    .findFirst().ifPresent(currency::setValue);
                description.setValue(actual.descripcion() == null ? "" : actual.descripcion());
                active.setValue(actual.activa());
                active.setReadOnly(true);
            }

            EstadoFormulario changes = new EstadoFormulario();
            nombre.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            branch.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            currency.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            description.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            active.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            dialog.add(form(nombre, branch, currency, active, description));

            AppActionButton save = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, "Guardar", null);
            save.addClickListener(event -> {
                CajaInput input = new CajaInput(nombre.getValue(), id(branch.getValue()), id(currency.getValue()),
                    description.getValue(), active.getValue());
                guardar(dialog, save, changes, actual, input);
            });
            dialog.getFooter().add(save);
            if (actual != null && puede("cajas.desactivar")) {
                ActionType type = actual.activa() ? ActionType.DEACTIVATE : ActionType.ACTIVATE;
                dialog.getFooter().add(new AppActionButton(type, ButtonSize.MAIN,
                    actual.activa() ? "Desactivar" : "Reactivar", event -> confirmarEstado(actual, dialog)));
            }
            dialog.getFooter().add(new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN,
                "Cancelar", event -> cerrarFormulario(dialog, changes)));
            dialog.setCloseOnEsc(false);
            dialog.setCloseOnOutsideClick(false);
            dialog.addDialogCloseActionListener(event -> cerrarFormulario(dialog, changes));
            formularioAbierto = dialog;
            cambiosFormulario = changes;
            dialog.addClosedListener(event -> {
                if (formularioAbierto == dialog) { formularioAbierto = null; cambiosFormulario = null; }
            });
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible abrir la caja."); }
    }

    private void guardar(Dialog dialog, AppActionButton button, EstadoFormulario changes,
                         CajaDto actual, CajaInput input) {
        button.setEnabled(false);
        try {
            if (actual == null) service.crear(input); else service.actualizar(actual.id(), input);
            changes.dirty = false;
            dialog.close();
            recargarSucursales();
            cargar();
            Notification.show(actual == null ? "Caja creada correctamente." : "Caja actualizada correctamente.");
        } catch (RuntimeException ex) {
            button.setEnabled(true);
            error(ex, "No fue posible guardar la caja.");
        }
    }

    private void detalle(UUID id) {
        try {
            CajaDto item = service.obtener(id);
            Dialog dialog = dialog("CAJA");
            dialog.add(new AppDetailSection("Información")
                .field("Nombre", item.nombre())
                .field("Sucursal", item.sucursalNombre())
                .field("Moneda", item.monedaCodigo() + " - " + item.monedaNombre())
                .field("Estado", item.activa() ? "Activa" : "Inactiva")
                .field("Descripción", item.descripcion() == null ? "Sin descripción" : item.descripcion()));
            if (puede("cajas.editar")) dialog.getFooter().add(new AppActionButton(ActionType.EDIT,
                ButtonSize.MAIN, "Editar caja", event -> { dialog.close(); formulario(item.id()); }));
            if (puede("cajas.desactivar")) {
                ActionType type = item.activa() ? ActionType.DEACTIVATE : ActionType.ACTIVATE;
                dialog.getFooter().add(new AppActionButton(type, ButtonSize.MAIN,
                    item.activa() ? "Desactivar" : "Reactivar", event -> confirmarEstado(item, dialog)));
            }
            dialog.getFooter().add(new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN,
                "Cerrar", event -> dialog.close()));
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible consultar la caja."); }
    }

    private void confirmarEstado(CajaDto item, Dialog parent) {
        boolean disable = item.activa();
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader(disable ? "Desactivar caja" : "Reactivar caja");
        confirm.setText(disable
            ? "Esta caja dejará de estar disponible para nuevas operaciones. El historial existente se conservará. ¿Deseas continuar?"
            : "La caja volverá a estar disponible para nuevas operaciones. ¿Deseas continuar?");
        confirm.setCancelable(true);
        confirm.setCancelText("Cancelar");
        confirm.setConfirmText(disable ? "Desactivar" : "Reactivar");
        if (disable) confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(event -> {
            try {
                if (disable) service.desactivar(item.id()); else service.reactivar(item.id());
                parent.close();
                cargar();
                Notification.show(disable ? "Caja desactivada correctamente." : "Caja reactivada correctamente.");
            } catch (RuntimeException ex) { error(ex, "No fue posible cambiar el estado de la caja."); }
        });
        confirm.open();
    }

    private static void cerrarFormulario(Dialog dialog, EstadoFormulario changes) {
        if (!changes.dirty) { dialog.close(); return; }
        confirmarDescartar(() -> { changes.dirty = false; dialog.close(); });
    }

    private static void confirmarDescartar(Runnable action) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Cambios sin guardar");
        confirm.setText("Hay cambios sin guardar. ¿Deseas descartarlos?");
        confirm.setCancelable(true);
        confirm.setCancelText("Continuar editando");
        confirm.setConfirmText("Descartar cambios");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(event -> action.run());
        confirm.open();
    }

    private static Dialog dialog(String title) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setWidth("min(760px, 96vw)");
        return dialog;
    }

    private static Div form(Component... fields) {
        Div form = new Div(fields);
        form.addClassName("cc-dialog-form");
        return form;
    }

    private static Span badge(boolean active) {
        Span badge = new Span(active ? "Activa" : "Inactiva");
        badge.getElement().getThemeList().add("badge " + (active ? "success" : "contrast"));
        return badge;
    }

    private static UUID id(CajaCatalogosDto.SucursalOpcion value) { return value == null ? null : value.id(); }
    private static UUID id(CajaCatalogosDto.MonedaOpcion value) { return value == null ? null : value.id(); }
    private static boolean puede(String... permissions) {
        Set<String> actual = TenantContext.principalActual().permisos();
        return Arrays.stream(permissions).anyMatch(actual::contains);
    }

    private static void error(RuntimeException ex, String fallback) {
        String message = ex instanceof ReglaNegocioException || ex instanceof RecursoNoEncontradoException
            ? ex.getMessage() : fallback;
        Notification.show(message == null || message.isBlank() ? fallback : message);
    }

    private static final class EstadoFormulario {
        private boolean dirty;
        void marcar(boolean fromClient) { if (fromClient) dirty = true; }
    }
}
