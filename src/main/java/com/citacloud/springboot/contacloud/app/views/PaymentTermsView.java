package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.CondicionPagoDto;
import com.citacloud.springboot.contacloud.app.dto.CondicionPagoInput;
import com.citacloud.springboot.contacloud.app.models.TipoCondicionPago;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Route(value = "condiciones-pago", layout = MainLayout.class)
@PageTitle("Condiciones de pago | ContaCloud")
@PermitAll
public class PaymentTermsView extends VerticalLayout implements BeforeEnterObserver, BeforeLeaveObserver {
    private final PaymentTermService service;
    private final EmpresaModuloService modulos;
    private final TextField buscar = new TextField("Buscar condición");
    private final Select<String> estado = new Select<>();
    private final AppGrid<CondicionPagoDto> grid = new AppGrid<>(CondicionPagoDto.class);
    private final AppPagination pagination;
    private String ordenarPor = "nombre";
    private boolean ordenAscendente = true;
    private Dialog formularioAbierto;
    private EstadoFormulario cambiosFormulario;

    public PaymentTermsView(PaymentTermService service, EmpresaModuloService modulos) {
        this.service = service;
        this.modulos = modulos;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        AppActionButton nuevo = puede("condiciones_pago.crear", "CONDICION_PAGO_CREAR")
            ? new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Nueva condición de pago", event -> formulario(null))
            : null;
        add(new AppPageHeader("CONDICIONES DE PAGO",
            "Administra las condiciones y plazos de pago utilizados por la empresa.",
            nuevo == null ? new Component[0] : new Component[]{nuevo}));
        configurarFiltros();
        configurarGrid();
        pagination = new AppPagination(request -> cargar());
        add(filtros(), grid, pagination);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!modulos.habilitado("CONDICIONES_PAGO")
            || !puede("condiciones_pago.ver", "CONDICION_PAGO_VER")) {
            Notification.show("No tienes permiso para acceder a esta opción.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        cargar();
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (formularioAbierto == null || cambiosFormulario == null || !cambiosFormulario.dirty) return;
        BeforeLeaveEvent.ContinueNavigationAction action = event.postpone();
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Cambios sin guardar");
        confirm.setText("Hay cambios sin guardar. ¿Deseas descartarlos y salir?");
        confirm.setCancelable(true);
        confirm.setCancelText("Continuar editando");
        confirm.setConfirmText("Descartar cambios");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(confirmEvent -> {
            cambiosFormulario.dirty = false;
            formularioAbierto.close();
            action.proceed();
        });
        confirm.open();
    }

    private void configurarFiltros() {
        buscar.setPlaceholder("Buscar condición...");
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.setValueChangeTimeout(400);
        estado.setLabel("Estado");
        estado.setItems("Todos", "Activas", "Inactivas");
        estado.setValue("Todos");
        buscar.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
        estado.addValueChangeListener(event -> { if (event.isFromClient()) reiniciar(); });
    }

    private HorizontalLayout filtros() {
        HorizontalLayout filtros = new HorizontalLayout(buscar, estado);
        filtros.addClassName("cc-filter-bar");
        filtros.setAlignItems(Alignment.END);
        filtros.setWidthFull();
        return filtros;
    }

    private void configurarGrid() {
        grid.addColumn(CondicionPagoDto::nombre).setHeader("Nombre").setKey("nombre")
            .setSortable(true).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(item -> etiquetaTipo(item.tipo())).setHeader("Tipo").setKey("tipo")
            .setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(CondicionPagoDto::dias).setHeader("Días").setKey("dias")
            .setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(item -> badge(item.activo())).setHeader("Estado").setKey("estado")
            .setSortable(true).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No hay condiciones de pago registradas.");
        grid.addSortListener(event -> {
            if (!event.isFromClient() || event.getSortOrder().isEmpty() || pagination == null) return;
            GridSortOrder<CondicionPagoDto> orden = event.getSortOrder().getFirst();
            ordenarPor = orden.getSorted().getKey();
            ordenAscendente = orden.getDirection() == SortDirection.ASCENDING;
            reiniciar();
        });
    }

    private Component acciones(CondicionPagoDto item) {
        HorizontalLayout acciones = new HorizontalLayout();
        acciones.addClassName("cc-grid-actions");
        acciones.setPadding(false);
        acciones.setSpacing(false);
        acciones.add(new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION,
            "Ver condición de pago", event -> detalle(item.id())));
        if (puede("condiciones_pago.editar", "CONDICION_PAGO_EDITAR")) {
            acciones.add(new AppActionButton(ActionType.EDIT, ButtonSize.GRID_ACTION,
                "Editar condición de pago", event -> formulario(item.id())));
        }
        return acciones;
    }

    private void cargar() {
        try {
            var request = pagination.currentRequest();
            var page = service.buscar(buscar.getValue(), estadoFiltro(), request.page(), request.size(),
                ordenarPor, ordenAscendente);
            grid.setItems(page.getContent());
            boolean conFiltros = !buscar.getValue().isBlank() || estadoFiltro() != null;
            grid.setEmptyStateText(conFiltros
                ? "No se encontraron condiciones de pago con los filtros seleccionados."
                : "No hay condiciones de pago registradas.");
            pagination.setTotal(page.getTotalElements());
        } catch (RuntimeException ex) { error(ex, "No fue posible cargar las condiciones de pago."); }
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
            CondicionPagoDto actual = nueva ? null : service.obtener(id);
            Dialog dialog = dialog(nueva ? "NUEVA CONDICIÓN DE PAGO" : "EDITAR CONDICIÓN DE PAGO");
            TextField nombre = new TextField("Nombre");
            nombre.setRequired(true);
            nombre.setMaxLength(100);
            Select<TipoCondicionPago> tipo = new Select<>();
            tipo.setLabel("Tipo");
            tipo.setItems(TipoCondicionPago.CASH, TipoCondicionPago.CREDIT);
            tipo.setItemLabelGenerator(PaymentTermsView::etiquetaTipo);
            tipo.setRequiredIndicatorVisible(true);
            IntegerField dias = new IntegerField("Días para vencimiento");
            dias.setRequiredIndicatorVisible(true);
            dias.setMin(0);
            dias.setMax(PaymentTermService.MAX_DAYS_TO_DUE);
            TextArea descripcion = new TextArea("Descripción");
            descripcion.setMaxLength(500);
            descripcion.addClassName("cc-dialog-span-2");
            Checkbox activa = new Checkbox("Activa", true);
            activa.addClassName("cc-dialog-span-2");

            if (actual == null) {
                tipo.setValue(TipoCondicionPago.CASH);
                dias.setValue(0);
                dias.setReadOnly(true);
            } else {
                nombre.setValue(actual.nombre());
                tipo.setValue(actual.tipo());
                dias.setValue(actual.dias());
                dias.setReadOnly(actual.tipo() == TipoCondicionPago.CASH);
                descripcion.setValue(actual.descripcion() == null ? "" : actual.descripcion());
                activa.setValue(actual.activo());
                activa.setReadOnly(true);
            }

            EstadoFormulario cambios = new EstadoFormulario();
            nombre.addValueChangeListener(event -> cambios.marcar(event.isFromClient()));
            tipo.addValueChangeListener(event -> {
                if (event.getValue() == TipoCondicionPago.CASH) {
                    dias.setValue(0);
                    dias.setReadOnly(true);
                } else dias.setReadOnly(false);
                cambios.marcar(event.isFromClient());
            });
            dias.addValueChangeListener(event -> cambios.marcar(event.isFromClient()));
            descripcion.addValueChangeListener(event -> cambios.marcar(event.isFromClient()));
            activa.addValueChangeListener(event -> cambios.marcar(event.isFromClient()));
            dialog.add(form(nombre, tipo, dias, descripcion, activa));

            AppActionButton guardar = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, "Guardar", null);
            guardar.addClickListener(event -> {
                CondicionPagoInput input = new CondicionPagoInput(nombre.getValue(), tipo.getValue(), dias.getValue(),
                    descripcion.getValue(), activa.getValue());
                if (actual != null && (actual.tipo() != input.tipo() || actual.dias() != valor(input.dias()))) {
                    confirmarCambioCritico(() -> guardar(dialog, guardar, cambios, actual, input));
                } else guardar(dialog, guardar, cambios, actual, input);
            });
            dialog.getFooter().add(guardar);
            if (actual != null && puede("condiciones_pago.desactivar", "CONDICION_PAGO_DESACTIVAR")) {
                ActionType accion = actual.activo() ? ActionType.DEACTIVATE : ActionType.ACTIVATE;
                dialog.getFooter().add(new AppActionButton(accion, ButtonSize.MAIN,
                    actual.activo() ? "Desactivar" : "Reactivar", event -> confirmarEstado(actual, dialog)));
            }
            dialog.getFooter().add(new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN,
                "Cancelar", event -> cerrarFormulario(dialog, cambios)));
            dialog.setCloseOnEsc(false);
            dialog.setCloseOnOutsideClick(false);
            dialog.addDialogCloseActionListener(event -> cerrarFormulario(dialog, cambios));
            formularioAbierto = dialog;
            cambiosFormulario = cambios;
            dialog.addClosedListener(event -> {
                if (formularioAbierto == dialog) {
                    formularioAbierto = null;
                    cambiosFormulario = null;
                }
            });
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible abrir la condición de pago."); }
    }

    private void guardar(Dialog dialog, AppActionButton boton, EstadoFormulario cambios,
                         CondicionPagoDto actual, CondicionPagoInput input) {
        boton.setEnabled(false);
        try {
            if (actual == null) service.crear(input); else service.actualizar(actual.id(), input);
            cambios.dirty = false;
            dialog.close();
            cargar();
            Notification.show(actual == null ? "Condición de pago creada correctamente."
                : "Condición de pago actualizada correctamente.");
        } catch (RuntimeException ex) {
            boton.setEnabled(true);
            error(ex, "No fue posible guardar la condición de pago.");
        }
    }

    private void detalle(UUID id) {
        try {
            CondicionPagoDto item = service.obtener(id);
            Dialog dialog = dialog("CONDICIÓN DE PAGO");
            dialog.add(new AppDetailSection("Información")
                .field("Nombre", item.nombre())
                .field("Tipo", etiquetaTipo(item.tipo()))
                .field("Días para vencimiento", Integer.toString(item.dias()))
                .field("Estado", item.activo() ? "Activa" : "Inactiva")
                .field("Descripción", item.descripcion() == null ? "Sin descripción" : item.descripcion()));
            if (puede("condiciones_pago.editar", "CONDICION_PAGO_EDITAR")) {
                dialog.getFooter().add(new AppActionButton(ActionType.EDIT, ButtonSize.MAIN,
                    "Editar condición de pago", event -> { dialog.close(); formulario(item.id()); }));
            }
            if (puede("condiciones_pago.desactivar", "CONDICION_PAGO_DESACTIVAR")) {
                ActionType accion = item.activo() ? ActionType.DEACTIVATE : ActionType.ACTIVATE;
                dialog.getFooter().add(new AppActionButton(accion, ButtonSize.MAIN,
                    item.activo() ? "Desactivar" : "Reactivar", event -> confirmarEstado(item, dialog)));
            }
            dialog.getFooter().add(new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN,
                "Cerrar", event -> dialog.close()));
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible consultar la condición de pago."); }
    }

    private void confirmarEstado(CondicionPagoDto item, Dialog parent) {
        boolean desactivar = item.activo();
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader(desactivar ? "Desactivar condición de pago" : "Reactivar condición de pago");
        confirm.setText(desactivar
            ? "Esta condición de pago dejará de estar disponible para nuevas operaciones. Los documentos existentes no serán modificados. ¿Deseas continuar?"
            : "La condición volverá a estar disponible para nuevas operaciones. ¿Deseas continuar?");
        confirm.setCancelable(true);
        confirm.setCancelText("Cancelar");
        confirm.setConfirmText(desactivar ? "Desactivar" : "Reactivar");
        if (desactivar) confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(event -> {
            try {
                if (desactivar) service.desactivar(item.id()); else service.reactivar(item.id());
                parent.close();
                cargar();
                Notification.show(desactivar ? "Condición de pago desactivada correctamente."
                    : "Condición de pago reactivada correctamente.");
            } catch (RuntimeException ex) { error(ex, "No fue posible cambiar el estado de la condición de pago."); }
        });
        confirm.open();
    }

    private static void confirmarCambioCritico(Runnable accion) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Confirmar cambio de plazo");
        confirm.setText("Cambiar el tipo o los días afectará las operaciones futuras. Los documentos existentes no serán modificados. ¿Deseas continuar?");
        confirm.setCancelable(true);
        confirm.setCancelText("Cancelar");
        confirm.setConfirmText("Guardar cambios");
        confirm.addConfirmListener(event -> accion.run());
        confirm.open();
    }

    private static void cerrarFormulario(Dialog dialog, EstadoFormulario cambios) {
        if (!cambios.dirty) { dialog.close(); return; }
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Cambios sin guardar");
        confirm.setText("Hay cambios sin guardar. ¿Deseas descartarlos?");
        confirm.setCancelable(true);
        confirm.setCancelText("Continuar editando");
        confirm.setConfirmText("Descartar cambios");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(event -> { cambios.dirty = false; dialog.close(); });
        confirm.open();
    }

    private static Dialog dialog(String titulo) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(titulo);
        dialog.setWidth("min(760px, 96vw)");
        return dialog;
    }

    private static Div form(Component... campos) {
        Div form = new Div(campos);
        form.addClassName("cc-dialog-form");
        return form;
    }

    private static Span badge(boolean activo) {
        Span badge = new Span(activo ? "Activa" : "Inactiva");
        badge.getElement().getThemeList().add("badge " + (activo ? "success" : "contrast"));
        return badge;
    }

    private static String etiquetaTipo(TipoCondicionPago tipo) {
        return tipo == TipoCondicionPago.CASH ? "Contado" : "Crédito";
    }

    private static int valor(Integer valor) { return valor == null ? Integer.MIN_VALUE : valor; }

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
