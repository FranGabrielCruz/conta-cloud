package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Route(value = "tasas-cambio", layout = MainLayout.class)
@PageTitle("Tasas de cambio | ContaCloud")
@PermitAll
public class ExchangeRateView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final ExchangeRateService service;
    private final EmpresaModuloService modulos;
    private final TextField buscar = new TextField("Buscar moneda");
    private final ComboBox<MonedaResumenDto> moneda = new ComboBox<>("Moneda");
    private final DatePicker fecha = new DatePicker("Fecha");
    private final AppGrid<TasaCambioDto> grid = new AppGrid<>(TasaCambioDto.class);
    private final AppPagination pagination;
    private String ordenarPor = "fecha";
    private boolean ordenAscendente;

    public ExchangeRateView(ExchangeRateService service, EmpresaModuloService modulos) {
        this.service = service;
        this.modulos = modulos;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();

        AppActionButton nuevo = puede("tasas_cambio.crear", "TASA_CAMBIO_CREAR")
            ? new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Nueva tasa de cambio", event -> formulario(null))
            : null;
        add(new AppPageHeader("TASAS DE CAMBIO",
            "Administra las tasas de cambio utilizadas por la empresa.",
            nuevo == null ? new Component[0] : new Component[]{nuevo}));

        configurarFiltros();
        configurarGrid();
        pagination = new AppPagination(request -> cargar());
        add(filtros(), grid, pagination);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!modulos.habilitado("TASAS_CAMBIO") || !puede("tasas_cambio.ver", "TASA_CAMBIO_VER")) {
            Notification.show("No tienes permiso para acceder a esta opción.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        cargarMonedasFiltro();
        cargar();
    }

    private void configurarFiltros() {
        buscar.setPlaceholder("Buscar moneda...");
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.setValueChangeTimeout(400);
        moneda.setPlaceholder("Todas");
        moneda.setClearButtonVisible(true);
        moneda.setItemLabelGenerator(MonedaResumenDto::etiqueta);
        fecha.setLocale(Locale.forLanguageTag("es-DO"));
        fecha.setClearButtonVisible(true);
        buscar.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
        moneda.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
        fecha.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
    }

    private HorizontalLayout filtros() {
        HorizontalLayout filtros = new HorizontalLayout(buscar, moneda, fecha);
        filtros.addClassName("cc-filter-bar");
        filtros.setAlignItems(Alignment.END);
        filtros.setWidthFull();
        return filtros;
    }

    private void configurarGrid() {
        grid.addColumn(TasaCambioDto::monedaOrigenCodigo).setHeader("Moneda origen").setKey("origen")
            .setSortable(true).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(TasaCambioDto::monedaDestinoCodigo).setHeader("Moneda destino").setKey("destino")
            .setSortable(true).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(item -> mostrarTasa(item.tasa())).setHeader("Tasa").setKey("tasa")
            .setSortable(true).setAutoWidth(true).setFlexGrow(0);
        Grid.Column<TasaCambioDto> fechaColumn = grid.addColumn(item -> DATE.format(item.fecha()))
            .setHeader("Fecha").setKey("fecha").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(item -> estado(item.activo())).setHeader("Estado").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No hay tasas de cambio registradas.");
        grid.addSortListener(event -> {
            if (!event.isFromClient() || event.getSortOrder().isEmpty() || pagination == null) return;
            GridSortOrder<TasaCambioDto> orden = event.getSortOrder().getFirst();
            ordenarPor = orden.getSorted().getKey();
            ordenAscendente = orden.getDirection() == SortDirection.ASCENDING;
            reiniciar();
        });
    }

    private Component acciones(TasaCambioDto item) {
        HorizontalLayout acciones = new HorizontalLayout();
        acciones.addClassName("cc-grid-actions");
        acciones.setPadding(false);
        acciones.setSpacing(false);
        acciones.add(new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION,
            "Ver tasa de cambio", event -> detalle(item.id())));
        if (puede("tasas_cambio.editar", "TASA_CAMBIO_EDITAR")) {
            acciones.add(new AppActionButton(ActionType.EDIT, ButtonSize.GRID_ACTION,
                "Editar tasa de cambio", event -> formulario(item.id())));
        }
        return acciones;
    }

    private void cargarMonedasFiltro() {
        try {
            MonedaResumenDto seleccion = moneda.getValue();
            List<MonedaResumenDto> monedas = service.listarMonedas(false);
            moneda.setItems(monedas);
            if (seleccion != null) monedas.stream().filter(item -> item.id().equals(seleccion.id()))
                .findFirst().ifPresent(moneda::setValue);
        } catch (RuntimeException ex) { error(ex, "No fue posible cargar las monedas."); }
    }

    private void cargar() {
        try {
            var request = pagination.currentRequest();
            UUID monedaId = moneda.getValue() == null ? null : moneda.getValue().id();
            var page = service.buscar(buscar.getValue(), monedaId, fecha.getValue(), request.page(), request.size(),
                ordenarPor, ordenAscendente);
            grid.setItems(page.getContent());
            boolean filtrosActivos = !buscar.getValue().isBlank() || monedaId != null || fecha.getValue() != null;
            grid.setEmptyStateText(filtrosActivos
                ? "No se encontraron tasas de cambio con los filtros seleccionados."
                : "No hay tasas de cambio registradas.");
            pagination.setTotal(page.getTotalElements());
        } catch (RuntimeException ex) { error(ex, "No fue posible cargar las tasas de cambio."); }
    }

    private void reiniciar() { pagination.reset(); cargar(); }

    private void formulario(UUID id) {
        try {
            boolean nueva = id == null;
            TasaCambioDto actual = nueva ? null : service.obtener(id);
            List<MonedaResumenDto> disponibles = service.listarMonedas(nueva);
            Dialog dialog = dialog(nueva ? "NUEVA TASA DE CAMBIO" : "EDITAR TASA DE CAMBIO");
            ComboBox<MonedaResumenDto> origen = monedaField("Moneda origen", disponibles);
            ComboBox<MonedaResumenDto> destino = monedaField("Moneda destino", disponibles);
            BigDecimalField tasa = new BigDecimalField("Tasa de cambio");
            tasa.setRequiredIndicatorVisible(true);
            tasa.getElement().setProperty("step", "0.00000001");
            tasa.setHelperText("Selecciona las monedas e introduce la tasa.");
            DatePicker rateDate = new DatePicker("Fecha");
            rateDate.setRequired(true);
            rateDate.setLocale(Locale.forLanguageTag("es-DO"));
            Checkbox activa = new Checkbox("Activa", true);
            activa.addClassName("cc-dialog-span-2");
            if (actual != null) {
                seleccionar(origen, disponibles, actual.monedaOrigenId());
                seleccionar(destino, disponibles, actual.monedaDestinoId());
                tasa.setValue(actual.tasa());
                rateDate.setValue(actual.fecha());
                activa.setValue(actual.activo());
                activa.setReadOnly(true);
            } else rateDate.setValue(LocalDate.now());

            EstadoFormulario estado = new EstadoFormulario();
            Runnable actualizarAyuda = () -> tasa.setHelperText(ayuda(origen.getValue(), destino.getValue(), tasa.getValue()));
            origen.addValueChangeListener(event -> { actualizarAyuda.run(); estado.marcar(event.isFromClient()); });
            destino.addValueChangeListener(event -> { actualizarAyuda.run(); estado.marcar(event.isFromClient()); });
            tasa.addValueChangeListener(event -> { actualizarAyuda.run(); estado.marcar(event.isFromClient()); });
            rateDate.addValueChangeListener(event -> estado.marcar(event.isFromClient()));
            activa.addValueChangeListener(event -> estado.marcar(event.isFromClient()));
            actualizarAyuda.run();
            dialog.add(form(origen, destino, tasa, rateDate, activa));

            AppActionButton guardar = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, "Guardar", null);
            guardar.addClickListener(event -> {
                guardar.setEnabled(false);
                try {
                    TasaCambioInput input = new TasaCambioInput(id(origen.getValue()), id(destino.getValue()),
                        tasa.getValue(), rateDate.getValue(), activa.getValue());
                    if (nueva) service.crear(input); else service.actualizar(id, input);
                    estado.dirty = false;
                    dialog.close();
                    cargarMonedasFiltro();
                    cargar();
                    Notification.show(nueva ? "Tasa de cambio creada correctamente."
                        : "Tasa de cambio actualizada correctamente.");
                } catch (RuntimeException ex) {
                    guardar.setEnabled(true);
                    error(ex, "No fue posible guardar la tasa de cambio.");
                }
            });
            dialog.getFooter().add(guardar);
            if (!nueva && puede("tasas_cambio.desactivar", "TASA_CAMBIO_DESACTIVAR")) {
                ActionType tipo = actual.activo() ? ActionType.DEACTIVATE : ActionType.ACTIVATE;
                String etiqueta = actual.activo() ? "Desactivar" : "Reactivar";
                dialog.getFooter().add(new AppActionButton(tipo, ButtonSize.MAIN, etiqueta,
                    event -> confirmarEstado(actual, dialog)));
            }
            dialog.getFooter().add(new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN,
                "Cancelar", event -> cerrarFormulario(dialog, estado)));
            dialog.setCloseOnEsc(false);
            dialog.setCloseOnOutsideClick(false);
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible abrir la tasa de cambio."); }
    }

    private void detalle(UUID id) {
        try {
            TasaCambioDto item = service.obtener(id);
            Dialog dialog = dialog("TASA DE CAMBIO");
            dialog.add(new AppDetailSection("Información")
                .field("Moneda origen", item.monedaOrigenCodigo() + " – " + item.monedaOrigenNombre())
                .field("Moneda destino", item.monedaDestinoCodigo() + " – " + item.monedaDestinoNombre())
                .field("Tasa", mostrarTasa(item.tasa()))
                .field("Fecha", DATE.format(item.fecha()))
                .field("Estado", item.activo() ? "Activa" : "Inactiva"));
            if (puede("tasas_cambio.editar", "TASA_CAMBIO_EDITAR")) {
                dialog.getFooter().add(new AppActionButton(ActionType.EDIT, ButtonSize.MAIN,
                    "Editar tasa de cambio", event -> { dialog.close(); formulario(item.id()); }));
            }
            if (puede("tasas_cambio.desactivar", "TASA_CAMBIO_DESACTIVAR")) {
                ActionType tipo = item.activo() ? ActionType.DEACTIVATE : ActionType.ACTIVATE;
                dialog.getFooter().add(new AppActionButton(tipo, ButtonSize.MAIN,
                    item.activo() ? "Desactivar" : "Reactivar", event -> confirmarEstado(item, dialog)));
            }
            dialog.getFooter().add(new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN,
                "Cerrar", event -> dialog.close()));
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible consultar la tasa de cambio."); }
    }

    private void confirmarEstado(TasaCambioDto item, Dialog parent) {
        boolean desactivar = item.activo();
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader(desactivar ? "Desactivar tasa de cambio" : "Reactivar tasa de cambio");
        confirm.setText(desactivar
            ? "Esta tasa dejará de estar disponible para nuevas operaciones. Los documentos históricos no serán modificados. ¿Deseas continuar?"
            : "La tasa volverá a estar disponible para nuevas operaciones. ¿Deseas continuar?");
        confirm.setCancelable(true);
        confirm.setCancelText("Cancelar");
        confirm.setConfirmText(desactivar ? "Desactivar" : "Reactivar");
        if (desactivar) confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(event -> {
            try {
                if (desactivar) service.desactivar(item.id()); else service.reactivar(item.id());
                parent.close();
                cargar();
                Notification.show(desactivar ? "Tasa de cambio desactivada correctamente."
                    : "Tasa de cambio reactivada correctamente.");
            } catch (RuntimeException ex) { error(ex, "No fue posible cambiar el estado de la tasa de cambio."); }
        });
        confirm.open();
    }

    private static ComboBox<MonedaResumenDto> monedaField(String label, List<MonedaResumenDto> items) {
        ComboBox<MonedaResumenDto> field = new ComboBox<>(label);
        field.setItems(items);
        field.setItemLabelGenerator(MonedaResumenDto::etiqueta);
        field.setRequired(true);
        field.setAllowCustomValue(false);
        return field;
    }

    private static void seleccionar(ComboBox<MonedaResumenDto> field, List<MonedaResumenDto> items, UUID id) {
        items.stream().filter(item -> item.id().equals(id)).findFirst().ifPresent(field::setValue);
    }

    private static UUID id(MonedaResumenDto moneda) { return moneda == null ? null : moneda.id(); }

    private static String ayuda(MonedaResumenDto origen, MonedaResumenDto destino, BigDecimal tasa) {
        if (origen == null || destino == null || tasa == null) return "Selecciona las monedas e introduce la tasa.";
        return "1 " + origen.codigo() + " = " + mostrarTasa(tasa) + " " + destino.codigo();
    }

    private static String mostrarTasa(BigDecimal tasa) {
        return tasa == null ? "" : tasa.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static Span estado(boolean activo) {
        Span badge = new Span(activo ? "Activa" : "Inactiva");
        badge.getElement().getThemeList().add("badge " + (activo ? "success" : "contrast"));
        return badge;
    }

    private static Dialog dialog(String titulo) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(titulo);
        dialog.setWidth("min(760px, 96vw)");
        return dialog;
    }

    private static Div form(Component... fields) {
        Div form = new Div(fields);
        form.addClassName("cc-dialog-form");
        return form;
    }

    private static void cerrarFormulario(Dialog dialog, EstadoFormulario estado) {
        if (!estado.dirty) { dialog.close(); return; }
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Cambios sin guardar");
        confirm.setText("Hay cambios sin guardar. ¿Deseas descartarlos?");
        confirm.setCancelable(true);
        confirm.setCancelText("Continuar editando");
        confirm.setConfirmText("Descartar cambios");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(event -> { estado.dirty = false; dialog.close(); });
        confirm.open();
    }

    private static boolean puede(String... permisos) {
        Set<String> actuales = TenantContext.principalActual().permisos();
        return Arrays.stream(permisos).anyMatch(actuales::contains);
    }

    private static void error(RuntimeException ex, String fallback) {
        String mensaje = ex instanceof ReglaNegocioException || ex instanceof RecursoNoEncontradoException
            ? ex.getMessage() : fallback;
        Notification.show(mensaje == null || mensaje.isBlank() ? fallback : mensaje);
    }

    private static final class EstadoFormulario {
        private boolean dirty;
        void marcar(boolean fromClient) { if (fromClient) dirty = true; }
    }
}
