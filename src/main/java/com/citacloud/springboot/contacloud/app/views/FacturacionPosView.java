package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.PosChargeButton;
import com.citacloud.springboot.contacloud.app.views.components.PosCustomerLine;
import com.citacloud.springboot.contacloud.app.views.components.DoubleEnterDetector;
import com.citacloud.springboot.contacloud.app.views.components.PosPaymentDialog;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.*;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.math.*;
import java.util.*;
import java.util.stream.Stream;

@Route(value = "facturas", layout = MainLayout.class)
@PageTitle("Facturación | ContaCloud")
@PermitAll
public class FacturacionPosView extends VerticalLayout implements BeforeEnterObserver {
    private static final String TOKEN_KEY = "contacloud.pos.terminal.token";
    private final FacturacionPosService service;
    private final CashRegisterSessionService cajaService;
    private final FacturaVentaPdfService documentos;
    private final ModuleAuthorization authorization;
    private final List<Fila> filas = new ArrayList<>();
    private final Grid<Fila> grid = new Grid<>();
    private final VerticalLayout contenido = new VerticalLayout();
    private final Div clienteSlot = new Div();
    private final Span subtotal = new Span("0.00");
    private final Span descuento = new Span("0.00");
    private final Span recargo = new Span("0.00");
    private final Span impuestos = new Span("0.00");
    private final Span total = new Span("0.00");
    private final TextArea observacion = new TextArea("Observación interna");
    private final DoubleEnterDetector doubleEnter = new DoubleEnterDetector();
    private String token;
    private TerminalPosDto terminal;
    private ClientePosDto clienteSeleccionado;
    private ComboBox<ProductoPosDto> productoBuscar;
    private UUID idempotencia = UUID.randomUUID();

    public FacturacionPosView(FacturacionPosService service, CashRegisterSessionService cajaService,
                              FacturaVentaPdfService documentos, ModuleAuthorization authorization) {
        this.service = service;
        this.cajaService = cajaService;
        this.documentos = documentos;
        this.authorization = authorization;
        addClassNames("cc-page", "cc-pos-page");
        setPadding(false);
        setSpacing(false);
        setSizeFull();
        contenido.setPadding(false);
        contenido.setSpacing(false);
        contenido.setSizeFull();
        clienteSlot.addClassName("cc-pos-customer-slot");
        clienteSlot.setVisible(false);
        add(contenido);
        registrarAtajos();
        registrarDobleEnter();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!authorization.canAccess("VENTAS", "facturas.ver")) {
            Notification.show("No tienes permiso para acceder a facturación.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        UI.getCurrent().getPage().executeJs("return window.localStorage.getItem($0)", TOKEN_KEY)
                .then(String.class, value -> { token = value; inicializar(); });
    }

    private void registrarAtajos() {
        Shortcuts.addShortcutListener(this, this::enfocarProducto, Key.F2);
        Shortcuts.addShortcutListener(this, () -> pendiente("Descuento"), Key.F3);
        Shortcuts.addShortcutListener(this, () -> pendiente("Recargo"), Key.F4);
        Shortcuts.addShortcutListener(this, () -> pendiente("Retener venta"), Key.F5);
        Shortcuts.addShortcutListener(this, this::dialogoClientes, Key.F6);
        Shortcuts.addShortcutListener(this, () -> pendiente("Últimas ventas"), Key.F7);
        Shortcuts.addShortcutListener(this, this::dialogoCobro, Key.F9);
    }

    private void registrarDobleEnter() {
        getElement().addEventListener("cc-pos-enter", event -> {
            long timestamp = Math.round(event.getEventData().get("event.detail.timestamp").asDouble());
            if (doubleEnter.register(timestamp, true)) dialogoCobro();
        }).addEventData("event.detail.timestamp");
        getElement().addEventListener("cc-pos-enter-reset", event -> doubleEnter.reset());
        addAttachListener(event -> event.getUI().getPage().executeJs("""
                const host = $0;
                if (host.__ccPosEnterHandler) document.removeEventListener('keydown', host.__ccPosEnterHandler, true);
                const reset = () => {
                  host.__ccPosLastEnter = 0;
                  host.dispatchEvent(new CustomEvent('cc-pos-enter-reset'));
                };
                host.__ccPosEnterHandler = event => {
                  if (event.key !== 'Enter' || event.repeat) return;
                  const path = event.composedPath ? event.composedPath() : [event.target];
                  const modalOpen = document.querySelector('vaadin-dialog-overlay[opened], vaadin-confirm-dialog-overlay[opened]');
                  if (modalOpen) { reset(); return; }
                  const combo = path.find(node => node?.tagName === 'VAADIN-COMBO-BOX');
                  const productCombo = combo?.classList?.contains('cc-pos-product-search');
                  const filter = productCombo ? String(combo.filter || '').trim() : '';
                  const textArea = path.some(node => node?.tagName === 'TEXTAREA' || node?.tagName === 'VAADIN-TEXT-AREA');
                  const editable = path.some(node => node?.isContentEditable || ['INPUT','VAADIN-BIG-DECIMAL-FIELD','VAADIN-NUMBER-FIELD','VAADIN-TEXT-FIELD','BUTTON'].includes(node?.tagName));
                  if (textArea || (combo && (!productCombo || filter)) || (!combo && editable)) { reset(); return; }
                  if (productCombo && !filter) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                  }
                  const now = performance.now();
                  if (host.__ccPosLastEnter && now - host.__ccPosLastEnter <= 450) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                  }
                  host.dispatchEvent(new CustomEvent('cc-pos-enter', {detail: {timestamp: now}}));
                  host.__ccPosLastEnter = host.__ccPosLastEnter && now - host.__ccPosLastEnter <= 450 ? 0 : now;
                };
                document.addEventListener('keydown', host.__ccPosEnterHandler, true);
                """, getElement()));
        addDetachListener(event -> event.getUI().getPage().executeJs("""
                const host = $0;
                if (host.__ccPosEnterHandler) document.removeEventListener('keydown', host.__ccPosEnterHandler, true);
                host.__ccPosEnterHandler = null;
                """, getElement()));
    }

    private void reiniciarDobleEnter() {
        doubleEnter.reset();
        getElement().executeJs("this.__ccPosLastEnter = 0");
    }

    private void inicializar() {
        contenido.removeAll();
        layoutActivo().ifPresent(MainLayout::showDefaultTopBar);
        try {
            Optional<TerminalPosDto> encontrada = service.terminal(token);
            if (encontrada.isEmpty()) { configurarTerminal(); return; }
            terminal = encontrada.get();
            if (!terminal.cajaAbierta()) { abrirCaja(); return; }
            construirPos();
        } catch (RuntimeException ex) { error(ex, "No fue posible iniciar el punto de venta."); }
    }

    private void configurarTerminal() {
        Div aviso = new Div();
        aviso.addClassName("cc-pos-empty");
        aviso.add(new H2("Configurar esta terminal"),
                new Paragraph("Selecciona la caja autorizada que utilizará este navegador."));
        Button iniciar = new Button("Configurar terminal", VaadinIcon.DESKTOP.create(), e -> dialogoTerminal());
        iniciar.addClassName("cc-pos-primary");
        aviso.add(iniciar);
        contenido.add(aviso);
    }

    private void dialogoTerminal() {
        Dialog dialog = dialogo("CONFIGURAR TERMINAL");
        ComboBox<CajaPosOpcionDto> caja = new ComboBox<>("Sucursal y caja");
        caja.setItems(service.cajasDisponibles());
        caja.setItemLabelGenerator(CajaPosOpcionDto::toString);
        caja.setRequiredIndicatorVisible(true);
        caja.setWidthFull();
        TextField nombre = new TextField("Nombre de la terminal");
        nombre.setValue("Punto de venta");
        nombre.setRequiredIndicatorVisible(true);
        nombre.setWidthFull();
        dialog.add(caja, nombre);
        Button guardar = new Button("Guardar configuración", VaadinIcon.CHECK.create(), e -> {
            try {
                TerminalPosConfiguradaDto resultado = service.configurarTerminal(new ConfigurarTerminalPosInput(
                        caja.getValue() == null ? null : caja.getValue().id(), nombre.getValue()));
                token = resultado.token();
                UI.getCurrent().getPage().executeJs("window.localStorage.setItem($0,$1)", TOKEN_KEY, token);
                dialog.close();
                inicializar();
            } catch (RuntimeException ex) { error(ex, "No fue posible configurar la terminal."); }
        });
        guardar.addClassName("cc-pos-primary");
        dialog.getFooter().add(guardar);
        dialog.open();
    }

    private void abrirCaja() {
        Div aviso = new Div();
        aviso.addClassName("cc-pos-empty");
        aviso.add(new H2("Caja cerrada"), new Paragraph(terminal.sucursal() + " · " + terminal.caja()));
        Button abrir = new Button("Abrir caja", VaadinIcon.UNLOCK.create(), e -> dialogoApertura());
        abrir.addClassName("cc-pos-primary");
        aviso.add(abrir);
        contenido.add(aviso);
    }

    private void dialogoApertura() {
        Dialog dialog = dialogo("ABRIR CAJA");
        BigDecimalField fondo = new BigDecimalField("Fondo inicial");
        fondo.setValue(BigDecimal.ZERO);
        fondo.setRequiredIndicatorVisible(true);
        fondo.setWidthFull();
        TextArea nota = new TextArea("Observación");
        nota.setWidthFull();
        dialog.add(fondo, nota);
        Button abrir = new Button("Abrir caja", VaadinIcon.UNLOCK.create(), e -> {
            try {
                cajaService.abrirDesdePos(terminal.cajaId(), new AperturaCajaInput(fondo.getValue(), nota.getValue()));
                dialog.close();
                inicializar();
            } catch (RuntimeException ex) { error(ex, "No fue posible abrir la caja."); }
        });
        abrir.addClassName("cc-pos-primary");
        dialog.getFooter().add(abrir);
        dialog.open();
    }

    private void construirPos() {
        prepararNuevaVenta();
        contenido.removeAll();
        HorizontalLayout cuerpo = new HorizontalLayout();
        cuerpo.addClassName("cc-pos-body");
        cuerpo.setWidthFull();
        cuerpo.setAlignItems(Alignment.STRETCH);
        VerticalLayout venta = construirVenta();
        VerticalLayout lateral = construirPanelDerecho();
        cuerpo.add(venta, lateral);
        cuerpo.setFlexGrow(3, venta);
        cuerpo.setFlexGrow(1, lateral);
        layoutActivo().ifPresent(layout -> layout.showPosTopBar(
                terminal, TenantContext.principalActual().nombre(), this::pendiente));
        contenido.add(cuerpo);
        enfocarProducto();
    }

    private Optional<MainLayout> layoutActivo() {
        List<HasElement> targets = UI.getCurrent().getActiveRouterTargetsChain();
        if (!targets.contains(this)) return Optional.empty();
        return targets.stream().filter(MainLayout.class::isInstance).map(MainLayout.class::cast).findFirst();
    }

    private void prepararNuevaVenta() {
        filas.clear();
        clienteSeleccionado = null;
        clienteSlot.removeAll();
        clienteSlot.setVisible(false);
        observacion.clear();
        idempotencia = UUID.randomUUID();
        actualizarTotales();
    }

    private VerticalLayout construirVenta() {
        VerticalLayout box = new VerticalLayout();
        box.addClassName("cc-pos-sale");
        box.setPadding(false);
        box.setSpacing(false);
        box.setWidthFull();
        box.add(construirBusquedaProducto(), clienteSlot);
        configurarGrid();
        box.add(grid, construirZonaInferior());
        return box;
    }

    private Component construirBusquedaProducto() {
        productoBuscar = new ComboBox<>();
        productoBuscar.setPlaceholder("Buscar producto por código, nombre o código de barras...");
        productoBuscar.setPrefixComponent(VaadinIcon.SEARCH.create());
        productoBuscar.setClearButtonVisible(true);
        productoBuscar.setWidthFull();
        productoBuscar.addClassName("cc-pos-product-search");
        productoBuscar.setPageSize(20);
        productoBuscar.setAutoOpen(true);
        productoBuscar.setAllowCustomValue(true);
        productoBuscar.setItemLabelGenerator(p -> p.codigo() + " · " + p.nombre());
        productoBuscar.setRenderer(new ComponentRenderer<>(this::resultadoProducto));
        productoBuscar.setItems(query -> {
            try {
                return service.buscarProductos(token, query.getFilter().orElse(""), query.getOffset(), query.getLimit()).stream();
            } catch (RuntimeException ex) {
                error(ex, "No fue posible buscar productos.");
                return Stream.empty();
            }
        });
        productoBuscar.addValueChangeListener(event -> {
            if (event.isFromClient() && event.getValue() != null) seleccionarProductoPredictivo(event.getValue());
        });
        productoBuscar.addCustomValueSetListener(event -> resolverBusquedaProducto(event.getDetail()));
        Button consultar = new Button("Consultar precio", VaadinIcon.SEARCH.create(), e -> dialogoProductos(true));
        consultar.addClassName("cc-pos-price-button");
        consultar.setTooltipText("Consultar precio de producto");
        HorizontalLayout fila = new HorizontalLayout(productoBuscar, consultar);
        fila.addClassName("cc-pos-search-row");
        fila.setWidthFull();
        fila.setAlignItems(Alignment.END);
        fila.setFlexGrow(1, productoBuscar);
        return fila;
    }

    private Component resultadoProducto(ProductoPosDto producto) {
        Span nombre = new Span(producto.nombre());
        nombre.addClassName("cc-pos-suggestion-name");
        Span detalle = new Span(producto.codigo() + " · " + valor(producto.unidad(), "—")
                + " · Disp. " + (producto.controlaExistencia() ? formatoCantidad(producto.existencia()) : "—")
                + " · " + terminal.monedaCodigo() + " " + dinero(producto.precio()));
        detalle.addClassName("cc-pos-suggestion-detail");
        Div resultado = new Div(nombre, detalle);
        resultado.addClassName("cc-pos-suggestion");
        return resultado;
    }

    private void configurarGrid() {
        grid.removeAllColumns();
        grid.addClassName("cc-pos-grid");
        grid.setEmptyStateText("No hay productos agregados a la venta");
        grid.setAllRowsVisible(false);
        grid.addColumn(f -> filas.indexOf(f) + 1).setHeader("#").setWidth("36px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(f -> textoConTooltip(f.nombre(), "cc-pos-product-name")))
                .setHeader("Producto").setWidth("190px").setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(f -> textoConTooltip(f.codigo(), "cc-pos-product-code")))
                .setHeader("Código").setWidth("130px").setFlexGrow(1);
        grid.addColumn(f -> dinero(f.precio)).setHeader("Precio").setWidth("90px").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(this::campoCantidad)).setHeader("Cant.").setWidth("80px")
                .setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(f -> dinero(f.total())).setHeader("Total").setWidth("100px").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(f -> iconButton(VaadinIcon.TRASH, "Eliminar producto", e -> {
            filas.remove(f); refrescar();
        }))).setHeader("Acciones").setWidth("72px").setFlexGrow(0);
        grid.setItems(filas);
    }

    private static Span textoConTooltip(String texto, String clase) {
        Span span = new Span(valor(texto, "—"));
        span.addClassName(clase);
        span.getElement().setAttribute("title", span.getText());
        return span;
    }

    private BigDecimalField campoCantidad(Fila fila) {
        BigDecimalField cantidad = new BigDecimalField();
        cantidad.setValue(fila.cantidad);
        cantidad.addClassName("cc-pos-quantity");
        cantidad.addValueChangeListener(event -> {
            if (event.isFromClient()) reiniciarDobleEnter();
            if (event.getValue() != null && event.getValue().signum() > 0) {
                fila.cantidad = event.getValue();
                actualizarTotales();
            } else cantidad.setValue(fila.cantidad);
        });
        return cantidad;
    }

    private Component construirZonaInferior() {
        observacion.setPlaceholder("Agregar una observación a la venta...");
        observacion.setWidthFull();
        observacion.setMaxLength(1000);
        observacion.addClassName("cc-pos-observation");
        Div nota = new Div(observacion);
        nota.addClassName("cc-pos-bottom-card");
        Div resumen = new Div();
        resumen.addClassNames("cc-pos-summary", "cc-pos-bottom-card");
        Span titulo = new Span("RESUMEN DE VENTA");
        titulo.addClassName("cc-pos-summary-title");
        resumen.add(titulo, filaResumen("Subtotal", subtotal), filaResumen("Descuento", descuento),
                filaResumen("Recargo", recargo), filaResumen("ITBIS", impuestos), filaResumen("TOTAL", total));
        Div zona = new Div(nota, resumen);
        zona.addClassName("cc-pos-bottom");
        return zona;
    }

    private VerticalLayout construirPanelDerecho() {
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("cc-pos-side");
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.setWidthFull();
        panel.add(construirAccionesRapidas(), construirTeclado());
        Button cobrar = new PosChargeButton(e -> dialogoCobro());
        panel.add(cobrar);
        return panel;
    }

    private Component construirAccionesRapidas() {
        Span titulo = new Span("⚡ ACCIONES RÁPIDAS");
        titulo.addClassName("cc-pos-actions-title");
        Button mas = new Button("Más acciones  ›");
        mas.addClassName("cc-pos-more");
        mas.setTooltipText("Ver más acciones");
        configurarMasAcciones(mas);
        Div cabecera = new Div(titulo, mas);
        cabecera.addClassName("cc-pos-actions-head");
        Button agregar = tarjeta("+", "Agregar", "F2", "Agregar producto (F2)", e -> enfocarProducto());
        Button descuentoRapido = tarjeta("%", "Descuento", "F3", "Aplicar descuento (F3)", e -> pendiente("Descuento"));
        Button recargoRapido = tarjeta("↑", "Recargo", "F4", "Aplicar recargo (F4)", e -> pendiente("Recargo"));
        Button retener = tarjeta("⏸", "Retener", "F5", "Retener venta (F5)", e -> pendiente("Retener venta"));
        Button limpiar = tarjeta("🗑", "Limpiar", "", "Limpiar venta", e -> pendiente("Limpiar venta"));
        limpiar.addClassName("cc-pos-quick-clear");
        Div tarjetas = new Div(
                agregar, descuentoRapido, recargoRapido, retener, limpiar,
                tarjeta("👥", "Clientes", "F6", "Seleccionar cliente (F6)", e -> dialogoClientes()),
                tarjeta("🧾", "Últimas", "F7", "Ver últimas ventas (F7)", e -> pendiente("Últimas ventas")),
                tarjeta("↩", "Devoluciones", "", "Devoluciones", e -> pendiente("Devoluciones")));
        tarjetas.addClassName("cc-pos-quick-grid");
        Div bloque = new Div(cabecera, tarjetas);
        bloque.addClassName("cc-pos-quick-actions");
        return bloque;
    }

    private Component construirTeclado() {
        Span titulo = new Span("🔢 TECLADO NUMÉRICO");
        titulo.addClassName("cc-pos-keypad-title");
        Div teclas = new Div();
        teclas.addClassName("cc-pos-keypad");
        for (String tecla : List.of("7", "8", "9", "4", "5", "6", "1", "2", "3", "0", ".", "00"))
            teclas.add(new Button(tecla, e -> agregarTecla(tecla)));
        Div bloque = new Div(titulo, teclas);
        bloque.addClassName("cc-pos-keypad-block");
        return bloque;
    }

    private void configurarMasAcciones(Button target) {
        ContextMenu menu = new ContextMenu(target);
        menu.setOpenOnClick(true);
        menu.addItem("Cargar cotización", e -> pendiente("Cargar cotización"));
        menu.addItem("Apartados", e -> pendiente("Apartados"));
        menu.addItem("Recibos / Cobros", e -> pendiente("Recibos / Cobros"));
        menu.addItem("Ventas retenidas", e -> pendiente("Ventas retenidas"));
        menu.addItem("Consultar precio", e -> dialogoProductos(true));
        menu.addItem("Cerrar", e -> menu.close());
    }

    private Button tarjeta(String icono, String nombre, String atajo, String tooltip,
                           ComponentEventListener<ClickEvent<Button>> listener) {
        Span icon = new Span(icono);
        icon.addClassName("cc-pos-card-icon");
        Span shortcut = new Span(atajo);
        shortcut.addClassName("cc-pos-card-shortcut");
        Button button = new Button(new Div(icon, new Span(nombre), shortcut), listener);
        button.addClassName("cc-pos-quick-card");
        button.setTooltipText(tooltip);
        return button;
    }

    private void resolverBusquedaProducto(String valorBusqueda) {
        String filtro = valorBusqueda == null ? "" : valorBusqueda.trim();
        if (filtro.isBlank()) return;
        try {
            List<ProductoPosDto> resultados = service.buscarProductos(token, filtro, 0, 20);
            Optional<ProductoPosDto> exacto = resultados.stream().filter(p -> coincideExactamente(p, filtro)).findFirst();
            if (exacto.isPresent() || resultados.size() == 1) {
                seleccionarProductoPredictivo(exacto.orElse(resultados.getFirst()));
            } else {
                productoBuscar.getElement().setProperty("filter", filtro);
                productoBuscar.getElement().setProperty("opened", true);
            }
        } catch (RuntimeException ex) { error(ex, "No fue posible buscar productos."); }
    }

    private void seleccionarProductoPredictivo(ProductoPosDto producto) {
        reiniciarDobleEnter();
        agregar(producto);
        productoBuscar.clear();
        productoBuscar.getElement().setProperty("filter", "");
        productoBuscar.focus();
    }

    private void dialogoProductos(boolean soloConsulta) {
        if (terminal == null) return;
        Dialog dialog = dialogo(soloConsulta ? "CONSULTAR PRECIO" : "AGREGAR PRODUCTO");
        TextField filtro = new TextField();
        filtro.setPlaceholder("Buscar por código, nombre o código de barras...");
        filtro.setPrefixComponent(VaadinIcon.SEARCH.create());
        filtro.setClearButtonVisible(true);
        filtro.setValue("");
        filtro.setWidthFull();
        filtro.setValueChangeMode(ValueChangeMode.LAZY);
        filtro.setValueChangeTimeout(250);
        Grid<ProductoPosDto> resultados = new Grid<>();
        resultados.setAllRowsVisible(true);
        resultados.setEmptyStateText("No se encontraron productos.");
        resultados.addColumn(ProductoPosDto::nombre).setHeader("Producto").setFlexGrow(1);
        resultados.addColumn(ProductoPosDto::codigo).setHeader("Código").setAutoWidth(true);
        resultados.addColumn(ProductoPosDto::unidad).setHeader("Unidad").setAutoWidth(true);
        resultados.addColumn(p -> terminal.monedaCodigo() + " " + dinero(p.precio())).setHeader("Precio")
                .setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        Runnable cargar = () -> {
            try { resultados.setItems(service.buscarProductos(token, filtro.getValue(), 0, 30)); }
            catch (RuntimeException ex) { error(ex, "No fue posible buscar productos."); }
        };
        filtro.addValueChangeListener(e -> cargar.run());
        if (!soloConsulta) {
            resultados.addItemDoubleClickListener(e -> seleccionarProducto(dialog, e.getItem()));
            Button seleccionar = new Button("Agregar producto", VaadinIcon.PLUS.create(), e -> {
                ProductoPosDto producto = resultados.asSingleSelect().getValue();
                if (producto == null) { Notification.show("Selecciona un producto."); return; }
                seleccionarProducto(dialog, producto);
            });
            seleccionar.addClassName("cc-pos-primary");
            dialog.getFooter().add(seleccionar);
        }
        dialog.add(filtro, resultados);
        cargar.run();
        dialog.open();
        filtro.focus();
    }

    private void seleccionarProducto(Dialog dialog, ProductoPosDto producto) {
        agregar(producto);
        dialog.close();
        productoBuscar.clear();
        productoBuscar.focus();
    }

    private void dialogoClientes() {
        if (terminal == null) return;
        Dialog dialog = dialogo("SELECCIONAR CLIENTE");
        TextField filtro = new TextField();
        filtro.setPlaceholder("Buscar por nombre, RNC/Cédula o teléfono...");
        filtro.setPrefixComponent(VaadinIcon.SEARCH.create());
        filtro.setClearButtonVisible(true);
        filtro.setWidthFull();
        filtro.setValueChangeMode(ValueChangeMode.LAZY);
        filtro.setValueChangeTimeout(250);
        Grid<ClientePosDto> resultados = new Grid<>();
        resultados.setAllRowsVisible(true);
        resultados.setEmptyStateText("No se encontraron clientes.");
        resultados.addColumn(ClientePosDto::nombre).setHeader("Cliente").setFlexGrow(1);
        resultados.addColumn(c -> valor(c.identificacion(), "—")).setHeader("RNC / Cédula").setAutoWidth(true);
        resultados.addColumn(c -> valor(c.telefono(), "—")).setHeader("Teléfono").setAutoWidth(true);
        Runnable cargar = () -> {
            try { resultados.setItems(service.buscarClientes(filtro.getValue(), 0, 30)); }
            catch (RuntimeException ex) { error(ex, "No fue posible buscar clientes."); }
        };
        filtro.addValueChangeListener(e -> cargar.run());
        Button seleccionar = new Button("Seleccionar", VaadinIcon.CHECK.create(), e -> {
            ClientePosDto seleccionado = resultados.asSingleSelect().getValue();
            if (seleccionado == null) { Notification.show("Selecciona un cliente."); return; }
            seleccionarCliente(seleccionado);
            dialog.close();
            enfocarProducto();
        });
        seleccionar.addClassName("cc-pos-primary");
        resultados.addItemDoubleClickListener(e -> {
            seleccionarCliente(e.getItem());
            dialog.close();
            enfocarProducto();
        });
        dialog.add(filtro, resultados);
        dialog.getFooter().add(seleccionar);
        cargar.run();
        dialog.open();
        filtro.focus();
    }

    private void seleccionarCliente(ClientePosDto cliente) {
        clienteSeleccionado = cliente;
        clienteSlot.removeAll();
        if (cliente == null) {
            clienteSlot.setVisible(false);
        } else {
            clienteSlot.add(new PosCustomerLine(cliente, this::dialogoClientes, () -> seleccionarCliente(null)));
            clienteSlot.setVisible(true);
        }
        enfocarProducto();
    }

    private void agregar(ProductoPosDto producto) {
        for (Fila fila : filas) {
            if (fila.id.equals(producto.id())) {
                fila.cantidad = fila.cantidad.add(BigDecimal.ONE);
                refrescar();
                return;
            }
        }
        filas.add(new Fila(producto));
        refrescar();
    }

    private void refrescar() { grid.setItems(filas); actualizarTotales(); }

    private void actualizarTotales() {
        BigDecimal base = BigDecimal.ZERO, tax = BigDecimal.ZERO, grand = BigDecimal.ZERO;
        for (Fila fila : filas) {
            base = base.add(fila.base());
            tax = tax.add(fila.impuesto());
            grand = grand.add(fila.total());
        }
        subtotal.setText(moneda(base));
        descuento.setText(dinero(BigDecimal.ZERO));
        recargo.setText(dinero(BigDecimal.ZERO));
        impuestos.setText(dinero(tax));
        total.setText(moneda(grand));
        if (grid.getDataProvider() != null) grid.getDataProvider().refreshAll();
    }

    private void dialogoCobro() {
        reiniciarDobleEnter();
        if (terminal == null) return;
        if (filas.isEmpty()) { Notification.show("Agregue al menos un producto para cobrar."); return; }
        BigDecimal totalVenta = filas.stream().map(Fila::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        try {
            List<MetodoPagoPosDto> metodos = service.metodosPago();
            if (metodos.isEmpty()) { Notification.show("No existen métodos de pago activos para esta empresa."); return; }
            List<CuentaBancariaPosDto> cuentas = metodos.stream().anyMatch(m -> m.requiereCuentaBancaria())
                    ? service.cuentasBancarias() : List.of();
            final PosPaymentDialog[] referencia = new PosPaymentDialog[1];
            PosPaymentDialog dialog = new PosPaymentDialog(totalVenta, terminal.monedaCodigo(), metodos,
                    cuentas, aplicaciones -> confirmarCobro(referencia[0], aplicaciones));
            referencia[0] = dialog;
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible preparar el cobro."); }
    }

    private void confirmarCobro(Dialog dialog, List<AplicacionCobroInput> aplicaciones) {
        try {
            List<LineaVentaInput> lineas = filas.stream().map(f -> new LineaVentaInput(f.id, f.cantidad)).toList();
            FacturaVentaResultadoDto resultado = service.confirmar(new ConfirmarVentaInput(token,
                    clienteSeleccionado == null ? null : clienteSeleccionado.id(), observacion.getValue(),
                    idempotencia, lineas, aplicaciones));
            dialog.close();
            Notification.show("Factura " + resultado.numero() + " confirmada. Cambio: " + dinero(resultado.cambio()));
            abrirPdf(resultado);
            construirPos();
        } catch (RuntimeException ex) {
            error(ex, "No fue posible confirmar el cobro.");
        }
    }

    private void abrirPdf(FacturaVentaResultadoDto factura) {
        try {
            ArchivoPdfDto archivo = documentos.generar(factura.id());
            StreamResource recurso = new StreamResource(archivo.nombreArchivo(),
                    () -> new ByteArrayInputStream(archivo.contenido()));
            recurso.setContentType("application/pdf");
            recurso.setCacheTime(0);
            var registro = VaadinSession.getCurrent().getResourceRegistry().registerResource(recurso);
            UI.getCurrent().getPage().open(registro.getResourceUri().toString(), "_blank");
        } catch (RuntimeException ex) {
            Notification.show("La factura fue confirmada, pero no fue posible abrir el PDF. Puede generarlo nuevamente.");
        }
    }

    private void agregarTecla(String tecla) {
        if (productoBuscar != null) {
            productoBuscar.getElement().executeJs("this.filter = (this.filter || '') + $0", tecla);
            productoBuscar.focus();
        }
    }

    private void enfocarProducto() { if (productoBuscar != null) productoBuscar.focus(); }

    private void pendiente(String funcion) { Notification.show(funcion + " estará disponible próximamente."); }

    private static Button iconButton(VaadinIcon icono, String tooltip,
                                     ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(icono.create(), listener);
        button.setTooltipText(tooltip);
        button.addClassName("cc-pos-icon-button");
        button.getElement().setAttribute("aria-label", tooltip);
        return button;
    }

    private Div filaResumen(String nombre, Span valor) {
        Div fila = new Div(new Span(nombre), valor);
        fila.addClassName("cc-pos-summary-row");
        if ("TOTAL".equals(nombre)) fila.addClassName("cc-pos-total");
        return fila;
    }

    private String moneda(BigDecimal valor) {
        return terminal == null ? dinero(valor) : terminal.monedaCodigo() + " " + dinero(valor);
    }

    private static boolean coincideExactamente(ProductoPosDto producto, String filtro) {
        return filtro.equalsIgnoreCase(valor(producto.codigo(), ""))
                || filtro.equalsIgnoreCase(valor(producto.codigoBarras(), ""));
    }

    private static Dialog dialogo(String titulo) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(titulo);
        dialog.setWidth("min(760px, 95vw)");
        return dialog;
    }

    private static String dinero(BigDecimal valor) {
        return String.format(Locale.US, "%,.2f", valor == null ? BigDecimal.ZERO : valor);
    }

    private static String formatoCantidad(BigDecimal valor) {
        return valor == null ? "0" : valor.stripTrailingZeros().toPlainString();
    }

    private static String valor(String valor, String defecto) {
        return valor == null || valor.isBlank() ? defecto : valor;
    }

    private static void error(RuntimeException error, String fallback) {
        String mensaje = error.getMessage();
        Notification.show(mensaje == null || mensaje.isBlank() ? fallback : mensaje);
    }

    private static class Fila {
        private final UUID id;
        private final String codigo;
        private final String nombre;
        private final String unidad;
        private final BigDecimal precio;
        private final BigDecimal tasa;
        private final BigDecimal existencia;
        private final boolean controlaExistencia;
        private BigDecimal cantidad = BigDecimal.ONE;

        private Fila(ProductoPosDto producto) {
            id = producto.id();
            codigo = producto.codigo();
            nombre = producto.nombre();
            unidad = valor(producto.unidad(), "—");
            precio = producto.precio();
            tasa = producto.tasaImpuesto();
            existencia = producto.existencia();
            controlaExistencia = producto.controlaExistencia();
        }

        private String codigo() { return codigo; }
        private String nombre() { return nombre; }
        private String unidad() { return unidad; }
        private BigDecimal base() { return precio.multiply(cantidad).setScale(4, RoundingMode.HALF_UP); }
        private BigDecimal impuesto() { return base().multiply(tasa).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP); }
        private BigDecimal total() { return base().add(impuesto()); }
    }
}
