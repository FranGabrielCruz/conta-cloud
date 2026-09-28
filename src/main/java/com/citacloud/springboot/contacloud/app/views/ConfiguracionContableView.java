package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value = "configuracion-contable", layout = MainLayout.class)
@RouteAlias(value = "periodos-fiscales", layout = MainLayout.class)
@PageTitle("Configuración contable | ContaCloud")
@PermitAll
public class ConfiguracionContableView extends VerticalLayout implements BeforeEnterObserver, BeforeLeaveObserver {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ConfiguracionContableService configuracionService;
    private final AccountingPeriodService periodosService;
    private final EmpresaModuloService modulos;
    private final boolean verGeneral = hasAny("configuracion_contable.ver", "CONFIGURACION_CONTABLE_VER");
    private final boolean verPeriodos = hasAny("periodos_fiscales.ver", "PERIODO_VER");
    private final Tab generalTab = new Tab("Configuración general");
    private final Tab periodosTab = new Tab("Períodos fiscales");
    private final Tabs tabs = new Tabs();
    private final Div contenido = new Div();
    private final PanelGeneral general = new PanelGeneral();
    private final PanelPeriodos periodos = new PanelPeriodos();
    private boolean cambiandoTab;

    public ConfiguracionContableView(ConfiguracionContableService configuracionService,
                                     AccountingPeriodService periodosService,
                                     EmpresaModuloService modulos) {
        this.configuracionService = configuracionService;
        this.periodosService = periodosService;
        this.modulos = modulos;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        add(new AppPageHeader("CONFIGURACIÓN CONTABLE",
            "Administra los parámetros y períodos contables de la empresa."));
        if (verGeneral) tabs.add(generalTab);
        if (verPeriodos) tabs.add(periodosTab);
        tabs.addClassName("cc-infrastructure-tabs");
        contenido.addClassName("cc-tab-content");
        add(tabs, contenido);
        tabs.addSelectedChangeListener(event -> {
            if (!event.isFromClient() || cambiandoTab) return;
            Tab destino = event.getSelectedTab();
            Tab anterior = event.getPreviousTab();
            if (anterior == generalTab && general.dirty) {
                cambiandoTab = true;
                tabs.setSelectedTab(anterior);
                cambiandoTab = false;
                confirmarDescartar(() -> mostrar(destino, true));
            } else mostrar(destino, true);
        });
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!modulos.habilitado("CONFIGURACION_CONTABLE") || (!verGeneral && !verPeriodos)) {
            Notification.show("No tienes permiso para acceder a esta opción.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        String path = event.getLocation().getPath();
        String requested = "periodos-fiscales".equals(path) ? "periodos"
            : event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("tab", List.of("general")).stream().findFirst().orElse("general");
        Tab selected = "periodos".equalsIgnoreCase(requested) && verPeriodos ? periodosTab
            : verGeneral ? generalTab : periodosTab;
        mostrar(selected, !"configuracion-contable".equals(path));
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (!general.dirty) return;
        BeforeLeaveEvent.ContinueNavigationAction action = event.postpone();
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Cambios sin guardar");
        confirm.setText("Hay cambios sin guardar. ¿Deseas descartarlos y salir?");
        confirm.setCancelable(true);
        confirm.setCancelText("Continuar editando");
        confirm.setConfirmText("Descartar cambios");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(confirmEvent -> {
            general.dirty = false;
            action.proceed();
        });
        confirm.open();
    }

    private void mostrar(Tab tab, boolean canonicalizar) {
        cambiandoTab = true;
        tabs.setSelectedTab(tab);
        cambiandoTab = false;
        contenido.removeAll();
        if (tab == generalTab) {
            contenido.add(general);
            general.cargar();
        } else {
            contenido.add(periodos);
            periodos.cargar();
        }
        if (canonicalizar || UI.getCurrent() != null) {
            UI.getCurrent().getPage().getHistory().replaceState(null,
                "configuracion-contable?tab=" + (tab == generalTab ? "general" : "periodos"));
        }
    }

    private void confirmarDescartar(Runnable continuar) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Cambios sin guardar");
        confirm.setText("Hay cambios sin guardar. ¿Deseas descartarlos y continuar?");
        confirm.setCancelable(true);
        confirm.setCancelText("Cancelar");
        confirm.setConfirmText("Descartar cambios");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(event -> { general.dirty = false; continuar.run(); });
        confirm.open();
    }

    private final class PanelGeneral extends VerticalLayout {
        private final Select<String> metodo = new Select<>();
        private final Checkbox automatica = new Checkbox("Contabilización automática");
        private final Checkbox periodosCerrados = new Checkbox("Permitir contabilización en períodos cerrados");
        private boolean cargado;
        private boolean cargando;
        private boolean dirty;

        PanelGeneral() {
            setPadding(false);
            setSpacing(true);
            setWidthFull();
            metodo.setLabel("Método contable");
            metodo.setItems("DEVENGADO");
            metodo.setItemLabelGenerator(value -> "DEVENGADO".equals(value) ? "Devengado" : value);
            metodo.setRequiredIndicatorVisible(true);
            metodo.setHelperText("El método efectivo se habilitará cuando exista soporte en el motor contable.");
            automatica.setHelperText("Prepara la generación automática para los módulos que se integren posteriormente.");
            periodosCerrados.setValue(false);
            periodosCerrados.setReadOnly(true);
            periodosCerrados.setHelperText("Por seguridad, las contabilizaciones en períodos cerrados están bloqueadas.");

            metodo.addValueChangeListener(event -> marcarDirty(event.isFromClient()));
            automatica.addValueChangeListener(event -> marcarDirty(event.isFromClient()));
            periodosCerrados.addValueChangeListener(event -> marcarDirty(event.isFromClient()));

            Div parametros = seccion("PARÁMETROS GENERALES",
                "Define el comportamiento contable básico de la empresa.", metodo, periodosCerrados);
            Div asientos = seccion("CONFIGURACIÓN DE ASIENTOS",
                "Estos parámetros serán consumidos por las futuras integraciones contables.", automatica);
            Div cuentas = seccion("CUENTAS POR DEFECTO",
                "El catálogo de cuentas debe estar implementado y configurado antes de asignar cuentas contables predeterminadas.");
            add(parametros, cuentas, asientos);
            if (hasAny("configuracion_contable.editar", "CONFIGURACION_CONTABLE_EDITAR")) {
                add(new HorizontalLayout(new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, "Guardar", event -> guardar())));
            } else {
                metodo.setReadOnly(true);
                automatica.setReadOnly(true);
            }
        }

        void cargar() {
            if (cargado) return;
            cargado = true;
            recargar();
        }

        void recargar() {
            try {
                cargando = true;
                ConfiguracionContableDto dto = configuracionService.obtener();
                metodo.setValue(dto.metodoContable());
                automatica.setValue(dto.contabilizacionAutomatica());
                periodosCerrados.setValue(dto.permitirPeriodosCerrados());
                dirty = false;
            } catch (RuntimeException ex) {
                error(ex, "No fue posible cargar la configuración contable.");
            } finally { cargando = false; }
        }

        void marcarDirty(boolean fromClient) { if (fromClient && !cargando) dirty = true; }

        void guardar() {
            try {
                configuracionService.actualizar(new ConfiguracionContableInput(
                    metodo.getValue(), automatica.getValue(), periodosCerrados.getValue()));
                dirty = false;
                Notification.show("Configuración contable actualizada correctamente.");
            } catch (RuntimeException ex) {
                error(ex, "No fue posible guardar la configuración contable.");
            }
        }
    }

    private final class PanelPeriodos extends VerticalLayout {
        private final TextField buscar = new TextField();
        private final ComboBox<Integer> anio = new ComboBox<>("Año");
        private final ComboBox<String> estado = new ComboBox<>("Estado");
        private final AppGrid<PeriodoFiscalDto> grid = new AppGrid<>(PeriodoFiscalDto.class);
        private final AppPagination pagination;
        private boolean cargado;

        PanelPeriodos() {
            setPadding(false);
            setSpacing(true);
            setWidthFull();
            AppActionButton nuevo = hasAny("periodos_fiscales.crear", "PERIODO_CREAR")
                ? new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Nuevo período fiscal", event -> editar(null)) : null;
            add(new AppPageHeader("PERÍODOS FISCALES",
                "Administra los períodos contables y controla su apertura y cierre.",
                nuevo == null ? new Component[0] : new Component[]{nuevo}));
            buscar.setPlaceholder("Buscar período...");
            buscar.setClearButtonVisible(true);
            buscar.setValueChangeMode(ValueChangeMode.LAZY);
            anio.setPlaceholder("Todos");
            anio.setClearButtonVisible(true);
            estado.setItems("Todos", "Abiertos", "Cerrados");
            estado.setValue("Todos");
            HorizontalLayout filtros = new HorizontalLayout(buscar, anio, estado);
            filtros.addClassName("cc-filter-bar");
            filtros.setAlignItems(Alignment.END);

            grid.addColumn(PeriodoFiscalDto::nombre).setHeader("Período").setAutoWidth(true).setFlexGrow(1);
            grid.addColumn(item -> FECHA.format(item.fechaInicial())).setHeader("Fecha inicial").setAutoWidth(true);
            grid.addColumn(item -> FECHA.format(item.fechaFinal())).setHeader("Fecha final").setAutoWidth(true);
            grid.addComponentColumn(item -> badge(item.estado())).setHeader("Estado").setAutoWidth(true);
            grid.addComponentColumn(this::acciones).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
            grid.setEmptyStateText("No hay períodos fiscales configurados.");
            pagination = new AppPagination(request -> recargar());
            buscar.addValueChangeListener(event -> { if (event.isFromClient()) reset(); });
            anio.addValueChangeListener(event -> { if (event.isFromClient()) reset(); });
            estado.addValueChangeListener(event -> { if (event.isFromClient()) reset(); });
            add(filtros, grid, pagination);
        }

        Component acciones(PeriodoFiscalDto item) {
            HorizontalLayout acciones = new HorizontalLayout();
            acciones.addClassName("cc-grid-actions");
            acciones.setPadding(false);
            acciones.setSpacing(false);
            acciones.add(new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION,
                "Ver período fiscal", event -> detalle(item.id())));
            if ("ABIERTO".equals(item.estado()) && hasAny("periodos_fiscales.editar", "PERIODO_EDITAR")) {
                acciones.add(new AppActionButton(ActionType.EDIT, ButtonSize.GRID_ACTION,
                    "Editar período fiscal", event -> editar(item)));
            }
            return acciones;
        }

        void cargar() { if (!cargado) { cargado = true; recargar(); } }

        void recargar() {
            try {
                var request = pagination.currentRequest();
                var page = periodosService.buscar(buscar.getValue(), anio.getValue(), estadoFiltro(),
                    request.page(), request.size());
                grid.setItems(page.getContent());
                pagination.setTotal(page.getTotalElements());
                List<Integer> disponibles = periodosService.listarAnios();
                Integer seleccionado = anio.getValue();
                anio.setItems(disponibles);
                if (seleccionado != null && disponibles.contains(seleccionado)) anio.setValue(seleccionado);
            } catch (RuntimeException ex) {
                error(ex, "No fue posible cargar los períodos fiscales.");
            }
        }

        void reset() { pagination.reset(); recargar(); }

        String estadoFiltro() {
            return switch (estado.getValue() == null ? "Todos" : estado.getValue()) {
                case "Abiertos" -> "ABIERTO";
                case "Cerrados" -> "CERRADO";
                default -> null;
            };
        }

        void editar(PeriodoFiscalDto item) {
            boolean nuevo = item == null;
            Dialog dialog = dialog(nuevo ? "Nuevo período fiscal" : "Editar período fiscal");
            TextField nombre = new TextField("Nombre");
            nombre.setRequired(true);
            nombre.setMaxLength(120);
            DatePicker inicio = fecha("Fecha inicial");
            DatePicker fin = fecha("Fecha final");
            Select<String> estadoInicial = new Select<>();
            estadoInicial.setLabel("Estado inicial");
            estadoInicial.setItems("ABIERTO");
            estadoInicial.setItemLabelGenerator(value -> "Abierto");
            estadoInicial.setValue("ABIERTO");
            estadoInicial.setReadOnly(true);
            if (!nuevo) {
                nombre.setValue(item.nombre());
                inicio.setValue(item.fechaInicial());
                fin.setValue(item.fechaFinal());
            }
            dialog.add(form(nombre, estadoInicial, inicio, fin));
            dialog.getFooter().add(new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, "Guardar", event -> {
                try {
                    var input = new PeriodoFiscalInput(nombre.getValue(), inicio.getValue(), fin.getValue());
                    if (nuevo) periodosService.crear(input); else periodosService.actualizar(item.id(), input);
                    dialog.close();
                    recargar();
                    Notification.show(nuevo ? "Período fiscal creado correctamente."
                        : "Período fiscal actualizado correctamente.");
                } catch (RuntimeException ex) { error(ex, "No fue posible guardar el período fiscal."); }
            }));
            dialog.getFooter().add(new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN,
                "Cancelar", event -> dialog.close()));
            dialog.open();
        }

        void detalle(UUID id) {
            try {
                PeriodoFiscalDto item = periodosService.obtener(id);
                Dialog dialog = dialog("Detalle del período fiscal");
                AppDetailSection detalle = new AppDetailSection("Información")
                    .field("Nombre", item.nombre()).field("Estado", etiquetaEstado(item.estado()))
                    .field("Fecha inicial", FECHA.format(item.fechaInicial()))
                    .field("Fecha final", FECHA.format(item.fechaFinal()));
                if (item.fechaCierre() != null) {
                    detalle.field("Fecha de cierre", FECHA_HORA.format(item.fechaCierre()))
                        .field("Cerrado por", referenciaUsuario(item.usuarioCierreId()));
                }
                dialog.add(detalle);
                if ("ABIERTO".equals(item.estado()) && hasAny("periodos_fiscales.editar", "PERIODO_EDITAR")) {
                    dialog.getFooter().add(new AppActionButton(ActionType.EDIT, ButtonSize.MAIN,
                        "Editar período fiscal", event -> { dialog.close(); editar(item); }));
                }
                if ("ABIERTO".equals(item.estado()) && hasAny("periodos_fiscales.cerrar", "PERIODO_CERRAR")) {
                    dialog.getFooter().add(new AppActionButton(ActionType.CLOSE_PERIOD, ButtonSize.MAIN,
                        "Cerrar período", event -> confirmarCierre(item, dialog)));
                }
                if ("CERRADO".equals(item.estado()) && hasAny("periodos_fiscales.reabrir")) {
                    dialog.getFooter().add(new AppActionButton(ActionType.REOPEN_PERIOD, ButtonSize.MAIN,
                        "Reabrir período", event -> confirmarReapertura(item, dialog)));
                }
                dialog.getFooter().add(new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN,
                    "Cerrar", event -> dialog.close()));
                dialog.open();
            } catch (RuntimeException ex) { error(ex, "No fue posible consultar el período fiscal."); }
        }

        void confirmarCierre(PeriodoFiscalDto item, Dialog detail) {
            confirmar("Cerrar período",
                "El período dejará de aceptar nuevas contabilizaciones. ¿Deseas cerrar este período?",
                "Confirmar cierre", () -> {
                    periodosService.cerrar(item.id()); detail.close(); recargar();
                    Notification.show("Período fiscal cerrado correctamente.");
                }, "No fue posible cerrar el período fiscal.");
        }

        void confirmarReapertura(PeriodoFiscalDto item, Dialog detail) {
            confirmar("Reabrir período",
                "Este período volverá a permitir contabilizaciones. ¿Deseas reabrirlo?",
                "Confirmar reapertura", () -> {
                    periodosService.reabrir(item.id()); detail.close(); recargar();
                    Notification.show("Período fiscal reabierto correctamente.");
                }, "No fue posible reabrir el período fiscal.");
        }
    }

    private static Div seccion(String titulo, String descripcion, Component... componentes) {
        Div contenido = new Div(componentes);
        contenido.addClassName("cc-accounting-section-grid");
        Div section = new Div(new H3(titulo), new Paragraph(descripcion), contenido);
        section.addClassName("cc-accounting-section");
        return section;
    }

    private static Dialog dialog(String titulo) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(titulo);
        dialog.setWidth("min(760px, 96vw)");
        return dialog;
    }

    private static Div form(Component... components) {
        Div form = new Div(components);
        form.addClassName("cc-dialog-form");
        return form;
    }

    private static DatePicker fecha(String label) {
        DatePicker field = new DatePicker(label);
        field.setRequired(true);
        field.setLocale(Locale.forLanguageTag("es-DO"));
        return field;
    }

    private static Span badge(String estado) {
        Span badge = new Span(etiquetaEstado(estado));
        badge.getElement().getThemeList().add("badge " + ("ABIERTO".equals(estado) ? "success" : "contrast"));
        return badge;
    }

    private static String etiquetaEstado(String estado) {
        return "ABIERTO".equals(estado) ? "Abierto" : "CERRADO".equals(estado) ? "Cerrado" : "Bloqueado";
    }

    private static String referenciaUsuario(UUID id) { return id == null ? "No disponible" : "Usuario " + id; }

    private static void confirmar(String titulo, String texto, String confirmar, Runnable accion, String fallback) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(titulo);
        dialog.setText(texto);
        dialog.setCancelable(true);
        dialog.setCancelText("Cancelar");
        dialog.setConfirmText(confirmar);
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            try { accion.run(); }
            catch (RuntimeException ex) { error(ex, fallback); }
        });
        dialog.open();
    }

    private static boolean hasAny(String... permisos) {
        Set<String> actuales = TenantContext.principalActual().permisos();
        return Arrays.stream(permisos).anyMatch(actuales::contains);
    }

    private static void error(RuntimeException ex, String fallback) {
        String mensaje = ex instanceof ReglaNegocioException || ex instanceof RecursoNoEncontradoException
            ? ex.getMessage() : fallback;
        Notification.show(mensaje == null || mensaje.isBlank() ? fallback : mensaje);
    }
}
