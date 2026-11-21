package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.CentroCostoDto;
import com.vaadin.flow.component.checkbox.Checkbox;

import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.CatalogoCuentasPdfService;
import com.citacloud.springboot.contacloud.app.services.CatalogoCuentasService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.AbstractHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Route(value = "catalogo-cuentas", layout = MainLayout.class)
@PageTitle("Catálogo de cuentas | ContaCloud")
@PermitAll
public class CatalogoCuentasView extends VerticalLayout implements BeforeEnterObserver {
    private final CatalogoCuentasService servicio;
    private final CatalogoCuentasPdfService pdf;
    private final ModuleAuthorization autorizacion;
    private final TreeGrid<Cuenta> tabla = new TreeGrid<>();
    private final AppPagination pagina = new AppPagination(p -> cargar());
    private final ComboBox<Tipo> tipo = combo("Tipo", Tipo::etiqueta, List.of(Tipo.values()));
    private final ComboBox<Naturaleza> naturaleza = combo("Naturaleza", Naturaleza::etiqueta, List.of(Naturaleza.values()));
    private final ComboBox<Boolean> estado = combo("Estado", CatalogoCuentasView::etiquetaEstado, List.of(true, false));
    private final TextField buscar = texto("Buscar", 160);
    private final AppActionButton nuevo;
    private final AppActionButton imprimir;
    private final Span vacio = new Span("No se encontraron cuentas.");
    private final Arbol arbol = new Arbol();

    public CatalogoCuentasView(CatalogoCuentasService servicio, CatalogoCuentasPdfService pdf, ModuleAuthorization autorizacion) {
        this.servicio = servicio;
        this.pdf = pdf;
        this.autorizacion = autorizacion;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        addClassName("cc-page");
        nuevo = accion(ActionType.NEW, "Nueva cuenta", () -> ejecutar("No fue posible abrir el formulario.", () -> editar(null, null)));
        imprimir = AppActionButton.verPdf(e -> ejecutar("No fue posible generar el catálogo.", () ->
            PdfEnPestana.abrir(pdf.generar(filtro()))));
        nuevo.setVisible(false);
        imprimir.setVisible(false);
        buscar.setPlaceholder("Código o nombre de cuenta");
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.setClearButtonVisible(true);
        buscar.addClassName("cc-maintenance-search");
        var filtros = new Div(tipo, naturaleza, estado, buscar);
        filtros.addClassNames("cc-maintenance-filters", "cc-chart-filters");
        tipo.setPlaceholder("Todos");
        naturaleza.setPlaceholder("Todas");
        estado.setPlaceholder("Todos");
        for (var campo : List.<HasValue<?, ?>>of(tipo, naturaleza, estado, buscar))
            campo.addValueChangeListener(e -> { if (e.isFromClient()) reiniciar(); });
        tabla.setSelectionMode(Grid.SelectionMode.NONE);
        tabla.setWidthFull();
        tabla.setAllRowsVisible(true);
        tabla.addClassName("cc-auto-grid");
        tabla.getElement().setAttribute("aria-label", "Catálogo de cuentas");
        tabla.addColumn(Cuenta::codigo).setHeader("Código").setAutoWidth(true);
        tabla.addHierarchyColumn(Cuenta::nombre).setHeader("Cuenta").setFlexGrow(4);
        tabla.addColumn(c -> c.tipo().etiqueta()).setHeader("Tipo").setAutoWidth(true);
        tabla.addColumn(c -> c.naturaleza().etiqueta()).setHeader("Naturaleza").setAutoWidth(true);
        tabla.addComponentColumn(c -> badge(c.activo())).setHeader("Estado").setAutoWidth(true);
        tabla.addComponentColumn(c -> new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION, "Ver cuenta", e -> ver(c.id())))
            .setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        tabla.setDataProvider(arbol);
        vacio.setVisible(false);
        var acciones = new Div(nuevo, imprimir);
        acciones.getStyle().set("display", "flex").set("gap", "var(--lumo-space-s)");
        add(new AppPageHeader("CATÁLOGO DE CUENTAS", "Organiza las cuentas contables de la empresa.", acciones),
            filtros, tabla, vacio, pagina);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!autorizacion.canAccess("CONTABILIDAD", CatalogoCuentasService.RECURSO + "ver")) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        nuevo.setVisible(puede("crear"));
        imprimir.setVisible(puede("imprimir"));
        cargar();
    }

    private void reiniciar() {
        pagina.reset();
        cargar();
    }

    private Filtro filtro() { return new Filtro(tipo.getValue(), naturaleza.getValue(), estado.getValue(), buscar.getValue()); }

    private void cargar() {
        ejecutar("No fue posible cargar el catálogo.", () -> {
            var p = pagina.currentRequest();
            var filtro = filtro();
            var resultado = servicio.raices(filtro, p.page(), p.size());
            tabla.collapse(arbol.raices);
            arbol.raices = resultado.getContent();
            arbol.filtro = filtro;
            arbol.ramas.clear();
            arbol.refreshAll();
            pagina.setTotal(resultado.getTotalElements());
            vacio.setVisible(resultado.isEmpty());
            if (!buscar.isEmpty()) tabla.expandRecursively(arbol.raices, Integer.MAX_VALUE);
        });
    }

    private class Arbol extends AbstractHierarchicalDataProvider<Cuenta, Void> {
        private List<Cuenta> raices = List.of();
        private Filtro filtro;
        private final Map<UUID, List<Cuenta>> ramas = new HashMap<>();

        @Override public Object getId(Cuenta c) { return c.id(); }
        @Override public boolean hasChildren(Cuenta c) { return c.hijos(); }
        @Override public boolean isInMemory() { return false; }
        @Override public int getChildCount(HierarchicalQuery<Cuenta, Void> q) { return datos(q.getParent()).size(); }
        @Override public Stream<Cuenta> fetchChildren(HierarchicalQuery<Cuenta, Void> q) {
            return datos(q.getParent()).stream().skip(q.getOffset()).limit(q.getLimit());
        }

        private List<Cuenta> datos(Cuenta padre) {
            if (padre == null) return raices;
            return ramas.computeIfAbsent(padre.id(), id -> servicio.hijos(id, filtro));
        }
    }

    private void ver(UUID id) {
        ejecutar("No fue posible consultar la cuenta.", () -> {
            var d = servicio.detalle(id);
            var c = d.cuenta();
            var modal = modal("CUENTA — " + c.codigo());
            modal.add(seccion("INFORMACIÓN GENERAL").field("Código", c.codigo()).field("Cuenta", c.nombre())
                .badge("Estado", badge(c.activo())).field("Cuenta padre", d.padre() == null ? "Raíz" : d.padre().etiqueta())
                .field("Nivel", Integer.toString(d.nivel())).field("Cuentas hijas", Integer.toString(d.hijos().size())),
                seccion("CLASIFICACIÓN CONTABLE").field("Tipo", c.tipo().etiqueta())
                    .field("Clasificación", c.clasificacion().etiqueta()).field("Naturaleza", c.naturaleza().etiqueta())
                    .field("Permite movimientos", c.permiteMovimientos() ? "Sí" : "No")
                    .field("Uso de Centro de Costos",c.usoCentroCosto().etiqueta())
                    .field("Habilitada para resultado del ejercicio",c.permiteResultadoEjercicio()?"Sí":"No")
                    .field("Tiene movimientos", d.movimientos() ? "Sí" : "No")
                    .field("Saldo actual", "Disponible al integrar Asientos"));
            if (c.descripcion() != null) {
                var descripcion = new AppDetailSection("DESCRIPCIÓN").field("Descripción", c.descripcion());
                descripcion.addClassName("cc-detail-full");
                modal.add(descripcion);
            }
            if (!d.hijos().isEmpty()) {
                var hijas = new AppGrid<Cuenta>(Cuenta.class);
                hijas.addColumn(Cuenta::codigo).setHeader("Código");
                hijas.addColumn(Cuenta::nombre).setHeader("Cuenta").setFlexGrow(3);
                hijas.addComponentColumn(h -> badge(h.activo())).setHeader("Estado");
                hijas.setItems(d.hijos());
                modal.add(new com.vaadin.flow.component.details.Details("Cuentas hijas", hijas));
            }
            var usos = d.usos().stream().filter(u -> !u.historico()).toList();
            if (usos.isEmpty()) {
                modal.add(seccion("INTEGRACIONES").field("Configuraciones", "Sin integraciones registradas"));
            } else {
                var integraciones = new AppGrid<Uso>(Uso.class);
                integraciones.addColumn(Uso::modulo).setHeader("Módulo");
                integraciones.addColumn(Uso::concepto).setHeader("Uso").setFlexGrow(3);
                integraciones.addComponentColumn(u -> badge(u.activo())).setHeader("Estado");
                integraciones.setItems(usos);
                modal.add(new com.vaadin.flow.component.details.Details("Integraciones", integraciones));
            }
            if (puede("editar")) modal.getFooter().add(accion(ActionType.EDIT, "Editar cuenta", () ->
                ejecutar("No fue posible editar la cuenta.", () -> editar(d, modal))));
            if (!c.estructural() && puede(c.activo() ? "desactivar" : "activar"))
                modal.getFooter().add(accion(c.activo() ? ActionType.DEACTIVATE : ActionType.ACTIVATE,
                    c.activo() ? "Desactivar cuenta" : "Activar cuenta", () -> confirmarEstado(c, modal)));
            modal.getFooter().add(accion(ActionType.CLOSE, "Cerrar", modal::close));
            modal.open();
        });
    }

    private void editar(Detalle previo, Dialog origen) {
        var c = previo == null ? null : previo.cuenta();
        var modal = modal(c == null ? "NUEVA CUENTA" : "EDITAR CUENTA — " + c.codigo());
        var nombre = texto("Nombre", 160);
        var padre = combo("Cuenta padre", Cuenta::etiqueta, List.of());
        padre.setPlaceholder("Selecciona una cuenta agrupadora");
        padre.setItems(q -> servicio.padres(c == null ? null : c.id(), q.getFilter().orElse(""), q.getOffset(), q.getLimit()).stream());
        var clase = texto("Clasificación", 24);
        clase.setReadOnly(true);
        var naturaleza = combo("Naturaleza", Naturaleza::etiqueta, List.of(Naturaleza.values()));
        var tipoCuenta = combo("Tipo", Tipo::etiqueta, List.of(Tipo.values()));
        var usoCentro = combo("Uso de Centro de Costos", CentroCostoDto.Uso::etiqueta, List.of(CentroCostoDto.Uso.values()));
        usoCentro.setValue(c!=null&&c.permiteMovimientos()?c.usoCentroCosto():CentroCostoDto.Uso.OPCIONAL);
        tipoCuenta.addValueChangeListener(e->usoCentro.setVisible(e.getValue()==Tipo.MOVIMIENTO));
        var descripcion = new TextArea("Descripción");
        descripcion.setWidthFull();
        descripcion.setMaxLength(2000);
        for (var campo : List.<HasValue<?, ?>>of(nombre, naturaleza, tipoCuenta))
            campo.setRequiredIndicatorVisible(true);
        padre.setRequiredIndicatorVisible(c == null || c.padre() != null);

        if (c != null) {
            nombre.setValue(c.nombre());
            if (previo.padre() != null) padre.setValue(previo.padre());
            clase.setValue(c.clasificacion().etiqueta());
            naturaleza.setValue(c.naturaleza());
            tipoCuenta.setValue(c.tipo());
            descripcion.setValue(Objects.toString(c.descripcion(), ""));
            boolean protegida = previo.estructuraProtegida();
            naturaleza.setReadOnly(protegida);
            for (var campo : List.<HasValue<?, ?>>of(padre, tipoCuenta)) campo.setReadOnly(protegida || c.hijos());
        }
        usoCentro.setVisible(tipoCuenta.getValue()==Tipo.MOVIMIENTO);
        var estadoInicial = texto("Estado", 10);
        var funcionResultado=new Checkbox("Habilitar para resultado del ejercicio");
        funcionResultado.setValue(c!=null&&c.permiteResultadoEjercicio());
        Runnable mostrarFuncion=()->funcionResultado.setVisible(tipoCuenta.getValue()==Tipo.MOVIMIENTO
            &&Clasificacion.PATRIMONIO.etiqueta().equals(clase.getValue()));
        tipoCuenta.addValueChangeListener(e->mostrarFuncion.run());
        clase.addValueChangeListener(e->mostrarFuncion.run());
        mostrarFuncion.run();
        estadoInicial.setValue(etiquetaEstado(c == null || c.activo()));
        estadoInicial.setReadOnly(true);
        padre.addValueChangeListener(e -> {
            if (!e.isFromClient()) return;
            var elegido = e.getValue();
            clase.setValue(elegido == null ? "" : elegido.clasificacion().etiqueta());
            if (elegido != null && (naturaleza.isEmpty()
                    || e.getOldValue() != null && naturaleza.getValue() == e.getOldValue().naturaleza()))
                naturaleza.setValue(elegido.naturaleza());
        });
        var general = formulario("INFORMACIÓN GENERAL", nombre, padre);
        general.addClassName("cc-chart-form-general");
        modal.add(general,
            formulario("CLASIFICACIÓN CONTABLE", clase, naturaleza, tipoCuenta),
            formulario("CONFIGURACIÓN", estadoInicial,usoCentro,funcionResultado), descripcion);
        var solicitud = UUID.randomUUID();
        modal.getFooter().add(accion(ActionType.SAVE, "Guardar", () -> ejecutar("No fue posible guardar la cuenta.", () -> {
            if (nombre.isInvalid()) return;
            var entrada = new Entrada(padre.isEmpty() ? null : padre.getValue().id(), nombre.getValue(),
                tipoCuenta.getValue(), naturaleza.getValue(), descripcion.getValue(),usoCentro.getValue(),
                funcionResultado.isVisible()&&funcionResultado.getValue());
            UUID id = c == null ? servicio.crear(entrada, solicitud) : c.id();
            if (c != null) servicio.editar(id, c.version(), entrada);
            modal.close();
            Notification.show("Cuenta guardada.");
            reiniciar();
            ver(id);
        })), accion(ActionType.CANCEL, "Cancelar", () -> {
            modal.close();
            if (origen != null) origen.open();
        }));
        if (origen != null) origen.close();
        modal.open();
    }

    private void confirmarEstado(Cuenta c, Dialog origen) {
        var accion = c.activo() ? "Desactivar cuenta" : "Activar cuenta";
        var dialogo = modal(accion);
        dialogo.add(new Span(c.etiqueta()), new Span(c.activo()
            ? "La cuenta quedará inactiva para nuevas operaciones." : "La cuenta volverá a estar activa."));
        dialogo.getFooter().add(accion(c.activo() ? ActionType.DEACTIVATE : ActionType.ACTIVATE, accion, () ->
            ejecutar("No fue posible cambiar el estado de la cuenta.", () -> {
                if (c.activo()) servicio.desactivar(c.id(), c.version());
                else servicio.activar(c.id(), c.version());
                dialogo.close();
                origen.close();
                reiniciar();
                ver(c.id());
            })), accion(ActionType.CANCEL, "Cancelar", dialogo::close));
        dialogo.open();
    }

    private static AppDetailSection seccion(String titulo) {
        var seccion = new AppDetailSection(titulo);
        seccion.addClassName("cc-detail-three");
        return seccion;
    }

    private static Div formulario(String titulo, Component... campos) {
        var encabezado = new H3(titulo);
        encabezado.addClassName("cc-section-heading");
        var contenido = new Div(campos);
        contenido.addClassName("cc-detail-grid");
        var seccion = new Div(encabezado, contenido);
        seccion.addClassNames("cc-form-section", "cc-detail-three");
        return seccion;
    }

    private static Dialog modal(String titulo) {
        var modal = new Dialog();
        modal.setHeaderTitle(titulo);
        modal.setWidth("min(960px,96vw)");
        modal.setMaxHeight("92vh");
        return modal;
    }

    private static AppStatusBadge badge(boolean activo) {
        return new AppStatusBadge(etiquetaEstado(activo), activo ? AppStatusBadge.Variant.SUCCESS : AppStatusBadge.Variant.NEUTRAL);
    }

    private static String etiquetaEstado(boolean activo) { return activo ? "Activo" : "Inactivo"; }

    private static TextField texto(String etiqueta, int maximo) {
        var campo = new TextField(etiqueta);
        campo.setWidthFull();
        campo.setMaxLength(maximo);
        return campo;
    }

    private static <T> ComboBox<T> combo(String etiqueta, SerializableFunction<T, String> nombre, List<T> valores) {
        var campo = new ComboBox<T>(etiqueta);
        campo.setItems(valores);
        campo.setItemLabelGenerator(nombre::apply);
        campo.setWidthFull();
        campo.setClearButtonVisible(true);
        return campo;
    }

    private static boolean puede(String permiso) {
        return TenantContext.principalActual().permisos().contains(CatalogoCuentasService.RECURSO + permiso);
    }

    private static AppActionButton accion(ActionType tipo, String etiqueta, Runnable manejador) {
        return new AppActionButton(tipo, ButtonSize.MAIN, etiqueta, e -> manejador.run());
    }

    private static void ejecutar(String mensaje, Runnable accion) {
        try { accion.run(); }
        catch (RuntimeException ex) { UiErrorHandler.show(ex, mensaje); }
    }
}
