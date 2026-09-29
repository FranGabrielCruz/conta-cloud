package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaBancaria;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.util.*;

@Route(value = "cuentas-bancarias", layout = MainLayout.class)
@PageTitle("Cuentas bancarias | ContaCloud")
@PermitAll
public class CuentasBancariasView extends VerticalLayout
    implements BeforeEnterObserver, BeforeLeaveObserver {

    private final BankAccountService service;
    private final EmpresaModuloService modulos;
    private final TextField buscar = new TextField();
    private final ComboBox<CuentaBancariaCatalogosDto.MonedaOpcion> moneda = new ComboBox<>("Moneda");
    private final Select<String> estado = new Select<>();
    private final AppGrid<CuentaBancariaDto> grid = new AppGrid<>(CuentaBancariaDto.class);
    private AppPagination pagination;
    private String ordenarPor = "banco";
    private boolean ordenAscendente = true;
    private Dialog formularioAbierto;
    private EstadoFormulario cambiosFormulario;

    public CuentasBancariasView(BankAccountService service, EmpresaModuloService modulos) {
        this.service = service;
        this.modulos = modulos;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        AppActionButton nuevo = puede("cuentas_bancarias.crear")
            ? new AppActionButton(ActionType.NEW, ButtonSize.MAIN,
                "Nueva cuenta bancaria", event -> formulario(null)) : null;
        add(new AppPageHeader("CUENTAS BANCARIAS",
            "Administra las cuentas bancarias utilizadas por la empresa.",
            nuevo == null ? new Component[0] : new Component[]{nuevo}));
        configurarFiltros();
        configurarGrid();
        pagination = new AppPagination(request -> cargar());
        add(filtros(), grid, pagination);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!modulos.habilitado("CAJA_BANCOS") || !puede("cuentas_bancarias.ver")) {
            Notification.show("No tienes permiso para acceder a esta opción.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        recargarMonedas();
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
        buscar.setPlaceholder("Buscar cuenta...");
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.setValueChangeTimeout(400);
        moneda.setPlaceholder("Todas");
        moneda.setClearButtonVisible(true);
        moneda.setItemLabelGenerator(item -> item.codigo() + " - " + item.nombre());
        estado.setLabel("Estado");
        estado.setItems("Todos", "Activas", "Inactivas");
        estado.setValue("Todos");
        buscar.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
        moneda.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
        estado.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
    }

    private HorizontalLayout filtros() {
        HorizontalLayout layout = new HorizontalLayout(buscar, moneda, estado);
        layout.addClassName("cc-filter-bar");
        layout.setAlignItems(Alignment.END);
        layout.setWidthFull();
        return layout;
    }

    private void configurarGrid() {
        grid.addColumn(CuentaBancariaDto::banco).setHeader("Banco").setKey("banco")
            .setSortable(true).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(CuentaBancariaDto::nombre).setHeader("Nombre de cuenta").setKey("nombre")
            .setSortable(true).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(item -> item.tipo().getEtiqueta()).setHeader("Tipo").setKey("tipo")
            .setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(CuentaBancariaDto::monedaCodigo).setHeader("Moneda").setKey("moneda")
            .setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(item -> badge(item.activa())).setHeader("Estado").setKey("estado")
            .setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No hay cuentas bancarias registradas.");
        grid.addSortListener(event -> {
            if (!event.isFromClient() || event.getSortOrder().isEmpty() || pagination == null) return;
            GridSortOrder<CuentaBancariaDto> sort = event.getSortOrder().getFirst();
            ordenarPor = sort.getSorted().getKey();
            ordenAscendente = sort.getDirection() == SortDirection.ASCENDING;
            reiniciar();
        });
    }

    private Component acciones(CuentaBancariaDto item) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassName("cc-grid-actions");
        actions.setPadding(false);
        actions.setSpacing(false);
        actions.add(new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION,
            "Ver cuenta bancaria", event -> detalle(item.id())));
        if (puede("cuentas_bancarias.editar")) actions.add(new AppActionButton(ActionType.EDIT,
            ButtonSize.GRID_ACTION, "Editar cuenta bancaria", event -> formulario(item.id())));
        return actions;
    }

    private void recargarMonedas() {
        try { moneda.setItems(service.catalogos().monedas()); }
        catch (RuntimeException ex) { error(ex, "No fue posible cargar las monedas."); }
    }

    private void cargar() {
        try {
            var request = pagination.currentRequest();
            UUID monedaId = moneda.getValue() == null ? null : moneda.getValue().id();
            var page = service.buscar(buscar.getValue(), monedaId, estadoFiltro(), request.page(),
                request.size(), ordenarPor, ordenAscendente);
            grid.setItems(page.getContent());
            boolean filtered = !buscar.getValue().isBlank() || monedaId != null || estadoFiltro() != null;
            grid.setEmptyStateText(filtered
                ? "No se encontraron cuentas bancarias con los filtros seleccionados."
                : "No hay cuentas bancarias registradas.");
            pagination.setTotal(page.getTotalElements());
        } catch (RuntimeException ex) { error(ex, "No fue posible cargar las cuentas bancarias."); }
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
            CuentaBancariaEdicionDto actual = nueva ? null : service.obtenerParaEditar(id);
            CuentaBancariaCatalogosDto catalogs = service.catalogos();
            Dialog dialog = dialog(nueva ? "NUEVA CUENTA BANCARIA" : "EDITAR CUENTA BANCARIA");
            TextField banco = new TextField("Banco");
            banco.setRequired(true);
            banco.setMaxLength(120);
            TextField nombre = new TextField("Nombre de cuenta");
            nombre.setRequired(true);
            nombre.setMaxLength(120);
            Select<TipoCuentaBancaria> tipo = new Select<>();
            tipo.setLabel("Tipo de cuenta");
            tipo.setItems(TipoCuentaBancaria.values());
            tipo.setItemLabelGenerator(TipoCuentaBancaria::getEtiqueta);
            tipo.setRequiredIndicatorVisible(true);
            ComboBox<CuentaBancariaCatalogosDto.MonedaOpcion> currency = new ComboBox<>("Moneda");
            currency.setRequired(true);
            currency.setItems(catalogs.monedas());
            currency.setItemLabelGenerator(item -> item.codigo() + " - " + item.nombre());
            TextField numero = new TextField("Número de cuenta");
            numero.setRequired(true);
            numero.setMaxLength(80);
            numero.getElement().setAttribute("autocomplete", "off");
            Checkbox active = new Checkbox("Activa", true);
            TextArea description = new TextArea("Descripción");
            description.setMaxLength(500);
            description.addClassName("cc-dialog-span-2");

            if (actual == null) {
                catalogs.monedas().stream().filter(item -> item.id().equals(catalogs.monedaBaseId()))
                    .findFirst().ifPresent(currency::setValue);
            } else {
                banco.setValue(actual.banco());
                nombre.setValue(actual.nombre());
                tipo.setValue(actual.tipo());
                catalogs.monedas().stream().filter(item -> item.id().equals(actual.monedaId()))
                    .findFirst().ifPresent(currency::setValue);
                numero.setValue(actual.numeroCuenta());
                description.setValue(actual.descripcion() == null ? "" : actual.descripcion());
                active.setValue(actual.activa());
                active.setReadOnly(true);
            }

            EstadoFormulario changes = new EstadoFormulario();
            banco.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            nombre.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            tipo.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            currency.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            numero.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            description.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            active.addValueChangeListener(event -> changes.marcar(event.isFromClient()));
            dialog.add(form(banco, nombre, tipo, currency, numero, active, description));

            AppActionButton save = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, "Guardar", null);
            save.addClickListener(event -> guardar(dialog, save, changes, actual,
                new CuentaBancariaInput(banco.getValue(), nombre.getValue(), tipo.getValue(),
                    id(currency.getValue()), numero.getValue(), description.getValue(), active.getValue())));
            dialog.getFooter().add(save);
            if (actual != null && puede("cuentas_bancarias.desactivar")) {
                ActionType action = actual.activa() ? ActionType.DEACTIVATE : ActionType.ACTIVATE;
                dialog.getFooter().add(new AppActionButton(action, ButtonSize.MAIN,
                    actual.activa() ? "Desactivar" : "Reactivar",
                    event -> confirmarEstado(service.obtener(actual.id()), dialog)));
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
        } catch (RuntimeException ex) { error(ex, "No fue posible abrir la cuenta bancaria."); }
    }

    private void guardar(Dialog dialog, AppActionButton button, EstadoFormulario changes,
                         CuentaBancariaEdicionDto actual, CuentaBancariaInput input) {
        button.setEnabled(false);
        try {
            if (actual == null) service.crear(input); else service.actualizar(actual.id(), input);
            changes.dirty = false;
            dialog.close();
            recargarMonedas();
            cargar();
            Notification.show(actual == null ? "Cuenta bancaria creada correctamente."
                : "Cuenta bancaria actualizada correctamente.");
        } catch (RuntimeException ex) {
            button.setEnabled(true);
            error(ex, "No fue posible guardar la cuenta bancaria.");
        }
    }

    private void detalle(UUID id) {
        try {
            CuentaBancariaDto item = service.obtener(id);
            Dialog dialog = dialog("CUENTA BANCARIA");
            dialog.add(new AppDetailSection("Información")
                .field("Banco", item.banco())
                .field("Nombre de cuenta", item.nombre())
                .field("Tipo de cuenta", item.tipo().getEtiqueta())
                .field("Moneda", item.monedaCodigo() + " - " + item.monedaNombre())
                .field("Número de cuenta", item.numeroEnmascarado())
                .field("Estado", item.activa() ? "Activa" : "Inactiva")
                .field("Descripción", item.descripcion() == null ? "Sin descripción" : item.descripcion()));
            if (puede("cuentas_bancarias.editar")) dialog.getFooter().add(
                new AppActionButton(ActionType.EDIT, ButtonSize.MAIN, "Editar cuenta bancaria",
                    event -> { dialog.close(); formulario(item.id()); }));
            if (puede("cuentas_bancarias.desactivar")) {
                ActionType action = item.activa() ? ActionType.DEACTIVATE : ActionType.ACTIVATE;
                dialog.getFooter().add(new AppActionButton(action, ButtonSize.MAIN,
                    item.activa() ? "Desactivar" : "Reactivar",
                    event -> confirmarEstado(item, dialog)));
            }
            dialog.getFooter().add(new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN,
                "Cerrar", event -> dialog.close()));
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible consultar la cuenta bancaria."); }
    }

    private void confirmarEstado(CuentaBancariaDto item, Dialog parent) {
        boolean disable = item.activa();
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader(disable ? "Desactivar cuenta bancaria" : "Reactivar cuenta bancaria");
        confirm.setText(disable
            ? "Esta cuenta bancaria dejará de estar disponible para nuevas operaciones. El historial existente se conservará. ¿Deseas continuar?"
            : "La cuenta bancaria volverá a estar disponible para nuevas operaciones. ¿Deseas continuar?");
        confirm.setCancelable(true);
        confirm.setCancelText("Cancelar");
        confirm.setConfirmText(disable ? "Desactivar" : "Reactivar");
        if (disable) confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(event -> {
            try {
                if (disable) service.desactivar(item.id()); else service.reactivar(item.id());
                parent.close();
                cargar();
                Notification.show(disable ? "Cuenta bancaria desactivada correctamente."
                    : "Cuenta bancaria reactivada correctamente.");
            } catch (RuntimeException ex) { error(ex, "No fue posible cambiar el estado de la cuenta bancaria."); }
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

    private static UUID id(CuentaBancariaCatalogosDto.MonedaOpcion value) {
        return value == null ? null : value.id();
    }

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
