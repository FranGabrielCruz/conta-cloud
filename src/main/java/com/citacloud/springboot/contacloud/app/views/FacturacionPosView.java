package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.PosChargeButton;
import com.citacloud.springboot.contacloud.app.views.components.PosCustomerLine;
import com.citacloud.springboot.contacloud.app.views.components.DoubleEnterDetector;
import com.citacloud.springboot.contacloud.app.views.components.PosPaymentDialog;
import com.citacloud.springboot.contacloud.app.views.components.UltimasFacturasDialog;
import com.citacloud.springboot.contacloud.app.views.components.VentasGuardadasDialog;
import com.citacloud.springboot.contacloud.app.views.components.ActionType;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.citacloud.springboot.contacloud.app.views.components.ButtonSize;
import com.citacloud.springboot.contacloud.app.views.components.DescuentoVentaDialog;
import com.citacloud.springboot.contacloud.app.views.components.RecargoVentaDialog;
import com.citacloud.springboot.contacloud.app.views.components.DevolucionesVentasDialog;
import com.citacloud.springboot.contacloud.app.views.components.CargarCotizacionDialog;
import com.citacloud.springboot.contacloud.app.views.components.PosDialogPagination;
import com.citacloud.springboot.contacloud.app.views.components.PosNumericKeypad;
import com.citacloud.springboot.contacloud.app.views.components.PosNumberFormatter;
import com.citacloud.springboot.contacloud.app.views.components.RecibosCobrosDialog;
import com.citacloud.springboot.contacloud.app.views.components.RecuentoCajaDialog;
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
    private final UltimasFacturasService ultimasFacturas;
    private final VentaGuardadaService ventasGuardadas;
    private final DescuentoVentaService descuentosService;
    private final RecargoVentaService recargosService;
    private final DevolucionVentaService devolucionesService;
    private final DevolucionVentaPdfService devolucionesPdf;
    private final CotizacionService cotizacionesService;
    private final CotizacionPosService cotizacionPosService;
    private final CobroClienteService cobrosCliente;
    private final CobroClientePdfService cobrosPdf;
    private final RecuentoCajaService recuentosCaja;
    private final RecuentoCajaPdfService recuentosCajaPdf;
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
    private final Div resumen = new Div();
    private final TextArea observacion = new TextArea("Observación interna");
    private final DoubleEnterDetector doubleEnter = new DoubleEnterDetector();
    private final PosNumericKeypad tecladoNumerico = new PosNumericKeypad();
    private String token;
    private TerminalPosDto terminal;
    private PosPaymentDialog dialogoCobroActivo;
    private Dialog dialogoGuardarVentaActivo;
    private Dialog dialogoClienteActivo;
    private UltimasFacturasDialog dialogoUltimasActivo;
    private VentasGuardadasDialog dialogoVentasGuardadasActivo;
    private DescuentoVentaDialog dialogoDescuentoActivo;
    private RecargoVentaDialog dialogoRecargoActivo;
    private Dialog dialogoLimpiarActivo;
    private DevolucionesVentasDialog dialogoDevolucionesActivo;
    private CargarCotizacionDialog dialogoCotizacionesActivo;
    private RecibosCobrosDialog dialogoRecibosActivo;
    private ClientePosDto clienteSeleccionado;
    private ComboBox<ProductoPosDto> productoBuscar;
    private UUID idempotencia = UUID.randomUUID();
    private BigDecimal descuentoVenta = BigDecimal.ZERO;
    private BigDecimal recargoVenta = BigDecimal.ZERO;
    private DescuentoVentaInput descuentoActual;
    private RecargoVentaInput recargoActual;
    private boolean agregandoProducto;
    private UUID ventaGuardadaActivaId;
    private String ventaGuardadaActivaNumero;
    private String referenciaVentaGuardada;
    private UUID cotizacionOrigenId;
    private Button guardarVentaRapida;
    private Button limpiarVentaRapida;

    public FacturacionPosView(FacturacionPosService service, CashRegisterSessionService cajaService,
                              FacturaVentaPdfService documentos, UltimasFacturasService ultimasFacturas, VentaGuardadaService ventasGuardadas,
                              DescuentoVentaService descuentosService, RecargoVentaService recargosService,
                              DevolucionVentaService devolucionesService, DevolucionVentaPdfService devolucionesPdf,
                              CotizacionService cotizacionesService, CotizacionPosService cotizacionPosService,
                              CobroClienteService cobrosCliente, CobroClientePdfService cobrosPdf,
                              RecuentoCajaService recuentosCaja, RecuentoCajaPdfService recuentosCajaPdf,
                              ModuleAuthorization authorization) {
        this.service = service;
        this.cajaService = cajaService;
        this.documentos = documentos;
        this.ultimasFacturas = ultimasFacturas;
        this.ventasGuardadas = ventasGuardadas;
        this.descuentosService = descuentosService;
        this.recargosService = recargosService;
        this.devolucionesService = devolucionesService;
        this.devolucionesPdf = devolucionesPdf;
        this.cotizacionesService = cotizacionesService;
        this.cotizacionPosService = cotizacionPosService;
        this.cobrosCliente = cobrosCliente;
        this.cobrosPdf = cobrosPdf;
        this.recuentosCaja = recuentosCaja;
        this.recuentosCajaPdf = recuentosCajaPdf;
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
        observacion.addValueChangeListener(event -> actualizarEstadoLimpiar());
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
        Shortcuts.addShortcutListener(this, this::dialogoDescuento, Key.F3);
        Shortcuts.addShortcutListener(this, this::dialogoRecargo, Key.F4);
        Shortcuts.addShortcutListener(this, this::dialogoVentasGuardadas, Key.F5);
        Shortcuts.addShortcutListener(this, this::dialogoClientes, Key.F6);
        Shortcuts.addShortcutListener(this, this::dialogoUltimasFacturas, Key.F7);
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
                  const textArea = path.some(node => node?.tagName === 'TEXTAREA' || node?.tagName === 'VAADIN-TEXT-AREA');
                  const editable = path.some(node => node?.isContentEditable || ['INPUT','VAADIN-BIG-DECIMAL-FIELD','VAADIN-NUMBER-FIELD','VAADIN-TEXT-FIELD','BUTTON'].includes(node?.tagName));
                  if (combo) {
                    const productCombo = combo.classList.contains('cc-pos-product-search');
                    const filter = productCombo ? String(combo.filter || '').trim() : '';
                    const activeProduct = productCombo && combo.opened && Number(combo._focusedIndex) >= 0;
                    if (!productCombo || filter || activeProduct) { reset(); return; }
                    event.preventDefault();
                    event.stopImmediatePropagation();
                  } else if (textArea || editable) { reset(); return; }
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
        tecladoNumerico.clearTarget();
        filas.clear();
        clienteSeleccionado = null;
        clienteSlot.removeAll();
        clienteSlot.setVisible(false);
        observacion.clear();
        descuentoVenta = BigDecimal.ZERO;
        descuentoActual = null;
        recargoVenta = BigDecimal.ZERO;
        recargoActual = null;
        ventaGuardadaActivaId = null;
        ventaGuardadaActivaNumero = null;
        referenciaVentaGuardada = null;
        cotizacionOrigenId = null;
        idempotencia = UUID.randomUUID();
        actualizarTotales();
        actualizarEstadoLimpiar();
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
                return service.buscarProductos(token, terminal.almacenId(), query.getFilter().orElse(""), query.getOffset(), query.getLimit()).stream();
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
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.setEmptyStateText("No hay productos agregados a la venta");
        grid.setAllRowsVisible(false);
        grid.addColumn(f -> filas.indexOf(f) + 1).setHeader("#").setWidth("50px").setFlexGrow(0);
        grid.addColumn(new ComponentRenderer<>(this::productoConDescuento))
                .setHeader("Producto").setWidth("160px").setFlexGrow(1);
        grid.addColumn(new ComponentRenderer<>(this::campoCantidad)).setHeader("Cantidad").setWidth("150px")
                .setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(f -> importeCompleto(f.precio))).setHeader("Precio")
                .setWidth("132px").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(f -> importeCompleto(f.total()))).setHeader("Total")
                .setWidth("145px").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(this::accionesFila)).setHeader("Acc.").setWidth("44px").setFlexGrow(0);
        grid.setItems(filas);
    }

    private Component accionesFila(Fila fila) {
        Button eliminar = iconButton(VaadinIcon.TRASH, "Eliminar producto", e -> {
            tecladoNumerico.clearTarget(fila.lineaId);
            filas.remove(fila);
            ventaModificada();
        });
        eliminar.addClassName("cc-pos-row-action");
        return eliminar;
    }

    private Component productoConDescuento(Fila fila) {
        Span nombre = textoConTooltip(fila.nombre(), "cc-pos-product-name");
        if (fila.descuento.signum() <= 0 && fila.recargo.signum() <= 0) return nombre;
        Div contenido = new Div(nombre);
        if(fila.descuento.signum()>0){Span indicador=new Span("🏷");indicador.addClassName("cc-pos-discount-indicator");String motivo=descuentoActual==null||descuentoActual.detalle()==null?"":"\nDetalle: "+descuentoActual.detalle();indicador.getElement().setAttribute("title","Descuento aplicado\n"+terminal.monedaCodigo()+" "+dinero(fila.descuento)+motivo);contenido.add(indicador);}
        if(fila.recargo.signum()>0){Span indicador=new Span("⬆");indicador.addClassName("cc-pos-discount-indicator");String concepto=recargoActual==null?"":"\nConcepto configurado";String motivo=recargoActual==null||recargoActual.detalle()==null?"":"\nDetalle: "+recargoActual.detalle();indicador.getElement().setAttribute("title","Recargo aplicado\n+"+terminal.monedaCodigo()+" "+dinero(fila.recargo)+concepto+motivo);contenido.add(indicador);}
        contenido.addClassName("cc-pos-product-with-discount");
        return contenido;
    }

    private static Span textoConTooltip(String texto, String clase) {
        Span span = new Span(valor(texto, "—"));
        span.addClassName(clase);
        span.getElement().setAttribute("title", span.getText());
        return span;
    }

    private static Span importeCompleto(BigDecimal importe) {
        Span span = new Span(dinero(importe));
        span.addClassName("cc-pos-money-value");
        return span;
    }

    private Component campoCantidad(Fila fila) {
        BigDecimalField cantidad = new BigDecimalField();
        cantidad.setValue(PosNumberFormatter.quantityInput(fila.cantidad));
        cantidad.addClassName("cc-pos-quantity");
        cantidad.setAriaLabel("Cantidad de " + fila.nombre);
        tecladoNumerico.bind(cantidad, fila.lineaId,
                PosNumericKeypad.Options.quantity(fila.permiteFracciones,
                        () -> { reiniciarDobleEnter(); enfocarProducto(); }),
                value -> { reiniciarDobleEnter(); cambiarCantidad(fila, value, cantidad); });
        Button disminuir = iconButton(VaadinIcon.MINUS, "Disminuir cantidad",
                e -> { tecladoNumerico.clearTarget(fila.lineaId); cambiarCantidad(fila, CantidadPosCalculo.disminuir(fila.cantidad, BigDecimal.ONE), cantidad); });
        Button aumentar = iconButton(VaadinIcon.PLUS, "Aumentar cantidad",
                e -> { tecladoNumerico.clearTarget(fila.lineaId); cambiarCantidad(fila, CantidadPosCalculo.incrementar(fila.cantidad, BigDecimal.ONE), cantidad); });
        disminuir.addClassName("cc-pos-quantity-control");
        aumentar.addClassName("cc-pos-quantity-control");
        Div editor = new Div(disminuir, cantidad, aumentar);
        editor.addClassName("cc-pos-quantity-editor");
        return editor;
    }

    private void cambiarCantidad(Fila fila, BigDecimal nuevaCantidad, BigDecimalField campo) {
        if (!CantidadPosCalculo.valida(nuevaCantidad)) {
            campo.setValue(PosNumberFormatter.quantityInput(fila.cantidad));
            Notification.show("La cantidad mínima es 1.");
            return;
        }
        try {
            service.validarCantidad(token, terminal.almacenId(), fila.id, nuevaCantidad);
            fila.cantidad = nuevaCantidad;
            fila.recalcularDescuentoCotizado();
            campo.setValue(PosNumberFormatter.quantityInput(nuevaCantidad));
            ventaModificada();
        } catch (RuntimeException ex) {
            campo.setValue(PosNumberFormatter.quantityInput(fila.cantidad));
            error(ex, "No fue posible actualizar la cantidad.");
        }
    }

    private Component construirZonaInferior() {
        observacion.setPlaceholder("Agregar una observación a la venta...");
        observacion.setWidthFull();
        observacion.setMaxLength(1000);
        observacion.addClassName("cc-pos-observation");
        Div nota = new Div(observacion);
        nota.addClassName("cc-pos-bottom-card");
        resumen.addClassNames("cc-pos-summary", "cc-pos-bottom-card");
        actualizarResumen();
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
        Button agregar = tarjeta("+", "Producto", "F2", "Agregar producto (F2)", e -> enfocarProducto());
        Button descuentoRapido = tarjeta("%", "Descuento", "F3", "Aplicar descuento (F3)", e -> dialogoDescuento());
        Button recargoRapido = tarjeta("↑", "Recargo", "F4", "Aplicar recargo (F4)", e -> dialogoRecargo());
        Button retener = tarjeta("💾", "Guardar", "F5", "Guardar venta (F5)", e -> dialogoVentasGuardadas());
        retener.addClassName("cc-pos-save-sale");
        guardarVentaRapida = retener;
        Button limpiar = tarjeta("🗑", "Limpiar", "", "Limpiar venta", e -> dialogoLimpiarVenta());
        limpiar.addClassName("cc-pos-quick-clear");
        limpiar.getElement().setAttribute("aria-label", "Limpiar venta");
        limpiarVentaRapida = limpiar;
        Button devoluciones = tarjeta("↩", "Devoluciones", "", "Gestionar devoluciones de ventas", e -> dialogoDevoluciones());
        devoluciones.setVisible(puede("devoluciones_venta.ver"));
        Div tarjetas = new Div(
                agregar, descuentoRapido, recargoRapido, retener, limpiar,
                tarjeta("👥", "Clientes", "F6", "Seleccionar cliente (F6)", e -> dialogoClientes()),
                tarjeta("🧾", "Últimas", "F7", "Ver últimas facturas (F7)", e -> dialogoUltimasFacturas()),
                devoluciones);
        tarjetas.addClassName("cc-pos-quick-grid");
        Div bloque = new Div(cabecera, tarjetas);
        bloque.addClassName("cc-pos-quick-actions");
        actualizarIndicadorVentasGuardadas();
        actualizarEstadoLimpiar();
        return bloque;
    }

    private void dialogoLimpiarVenta() {
        reiniciarDobleEnter();
        if (!tieneVentaEnCurso()) return;
        if (dialogoCobroActivo != null && dialogoCobroActivo.isOpened()) return;
        if (dialogoLimpiarActivo != null && dialogoLimpiarActivo.isOpened()) return;
        suspenderTecladoNumerico();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Limpiar venta");
        dialog.setWidth("min(440px, 94vw)");
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.addClassName("cc-pos-clear-dialog");
        dialog.getElement().setAttribute("aria-label", "Confirmar limpieza de la venta");
        dialogoLimpiarActivo = dialog;

        Paragraph mensaje = new Paragraph("Se eliminarán los datos de la venta actual.");
        Div detalle = new Div(
                new Span("Productos: " + filas.size()),
                new Span("Total: " + moneda(totalActualVenta())));
        detalle.addClassName("cc-pos-clear-summary");
        Paragraph advertencia = new Paragraph("Esta acción no se puede deshacer.");
        advertencia.addClassName("cc-pos-clear-warning");
        dialog.add(mensaje, detalle, advertencia);

        AppActionButton limpiar = new AppActionButton(ActionType.DELETE, ButtonSize.MAIN,
                "Limpiar venta", null);
        AppActionButton cancelar = new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN,
                "Cancelar", e -> dialog.close());
        boolean[] completada = {false};
        limpiar.addClickListener(event -> {
            if (!limpiar.isEnabled()) return;
            limpiar.setEnabled(false);
            try {
                if (ventaGuardadaActivaId != null) {
                    ventasGuardadas.liberarRecuperacion(ventaGuardadaActivaId, token, terminal.almacenId());
                }
                completada[0] = true;
                prepararNuevaVenta();
                refrescar();
                dialog.close();
                actualizarIndicadorVentasGuardadas();
                enfocarProducto();
            } catch (RuntimeException ex) {
                limpiar.setEnabled(true);
                error(ex, "No fue posible limpiar la venta.");
            }
        });
        dialog.getFooter().add(limpiar, cancelar);
        dialog.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                cancelar.focus();
            } else {
                if (dialogoLimpiarActivo == dialog) dialogoLimpiarActivo = null;
                if (!completada[0] && limpiarVentaRapida != null) limpiarVentaRapida.focus();
            }
        });
        dialog.open();
    }

    private boolean tieneVentaEnCurso() {
        return tieneDatosVenta(filas.size(), clienteSeleccionado != null, descuentoVenta, recargoVenta,
                observacion.getValue(), ventaGuardadaActivaId) || cotizacionOrigenId != null;
    }

    static boolean tieneDatosVenta(int productos, boolean clienteSeleccionado, BigDecimal descuento,
                                   BigDecimal recargo, String observacion, UUID ventaGuardadaId) {
        return productos > 0 || clienteSeleccionado
                || (descuento != null && descuento.signum() != 0)
                || (recargo != null && recargo.signum() != 0)
                || (observacion != null && !observacion.isBlank())
                || ventaGuardadaId != null;
    }

    private BigDecimal totalActualVenta() {
        return filas.stream().map(Fila::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void actualizarEstadoLimpiar() {
        if (limpiarVentaRapida != null) limpiarVentaRapida.setEnabled(tieneVentaEnCurso());
    }

    private Component construirTeclado() {
        return tecladoNumerico;
    }

    private void configurarMasAcciones(Button target) {
        ContextMenu menu = new ContextMenu(target);
        menu.setOpenOnClick(true);
        if (puede("cotizaciones.ver")) menu.addItem("Cargar cotización", e -> dialogoCargarCotizacion());
        if (puede("recibos.ver")) menu.addItem("Recibos / Cobros", e -> dialogoRecibos());
        if (puede("ventas_guardadas.consultar")) menu.addItem("Ventas guardadas", e -> dialogoVentasGuardadas());
        menu.addItem("Consultar precio", e -> dialogoProductos(true));
        menu.addItem("Cerrar", e -> menu.close());
    }

    private void dialogoRecibos() {
        if (dialogoRecibosActivo != null && dialogoRecibosActivo.isOpened()) return;
        dialogoRecibosActivo = new RecibosCobrosDialog(cobrosCliente, service, terminal,
                this::abrirPdfRecibo);
        dialogoRecibosActivo.addOpenedChangeListener(e -> { if (!e.isOpened()) { dialogoRecibosActivo = null; enfocarProducto(); } });
        dialogoRecibosActivo.open();
    }

    private void abrirPdfRecibo(UUID reciboId) {
        try {
            ArchivoPdfDto archivo = cobrosPdf.generar(reciboId);
            StreamResource recurso = new StreamResource(archivo.nombreArchivo(),
                    () -> new ByteArrayInputStream(archivo.contenido()));
            recurso.setContentType("application/pdf"); recurso.setCacheTime(0);
            var registro = VaadinSession.getCurrent().getResourceRegistry().registerResource(recurso);
            UI.getCurrent().getPage().open(registro.getResourceUri().toString(), "_blank");
        } catch (RuntimeException ex) {
            Notification.show("El recibo fue guardado, pero no fue posible abrir el PDF. Puede reimprimirlo desde Recibos / Cobros.");
        }
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
            List<ProductoPosDto> resultados = service.buscarProductos(token, terminal.almacenId(), filtro, 0, 20);
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
        if (producto == null || agregandoProducto) return;
        agregandoProducto = true;
        try {
            agregar(producto);
        } finally {
            reiniciarBusquedaProducto();
            agregandoProducto = false;
        }
    }

    private void reiniciarBusquedaProducto() {
        reiniciarDobleEnter();
        productoBuscar.clear();
        productoBuscar.getElement().setProperty("filter", "");
        productoBuscar.getElement().setProperty("opened", false);
        UI.getCurrent().beforeClientResponse(productoBuscar, context -> productoBuscar.focus());
    }

    private void dialogoProductos(boolean soloConsulta) {
        if (terminal == null) return;
        suspenderTecladoNumerico();
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
        PosDialogPagination[] paginacion = new PosDialogPagination[1];
        Runnable cargar = () -> {
            try {
                var pagina = service.buscarProductosPagina(token, terminal.almacenId(), filtro.getValue(),
                        paginacion[0].currentRequest().page());
                resultados.setItems(pagina.getContent());
                paginacion[0].setTotal(pagina.getTotalElements());
            } catch (RuntimeException ex) {
                resultados.setItems(List.of());
                paginacion[0].setTotal(0);
                error(ex, "No fue posible buscar productos.");
            }
        };
        paginacion[0] = new PosDialogPagination(request -> cargar.run());
        filtro.addValueChangeListener(e -> { paginacion[0].reset(); cargar.run(); });
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
        dialog.add(filtro, resultados, paginacion[0]);
        cargar.run();
        dialog.open();
        filtro.focus();
    }

    private void seleccionarProducto(Dialog dialog, ProductoPosDto producto) {
        agregar(producto);
        dialog.close();
        reiniciarBusquedaProducto();
    }

    private void dialogoClientes() {
        if (terminal == null) return;
        if (dialogoClienteActivo != null && dialogoClienteActivo.isOpened()) return;
        suspenderTecladoNumerico();
        Dialog dialog = dialogo("SELECCIONAR CLIENTE");
        dialogoClienteActivo = dialog;
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
        Span refinar = new Span("Escriba más para refinar la búsqueda.");
        refinar.addClassName("cc-pos-customer-refine");
        refinar.setVisible(false);
        ClientePosDto[] enfocado = {null};
        List<ClientePosDto> visibles = new ArrayList<>();
        int[] indiceActivo = {-1};
        Runnable cargar = () -> {
            try {
                BusquedaClientesPosDto respuesta = service.buscarClientes(filtro.getValue());
                visibles.clear();
                visibles.addAll(respuesta.clientes());
                resultados.setItems(respuesta.clientes());
                refinar.setVisible(respuesta.hayMasResultados());
                enfocado[0] = null;
                indiceActivo[0] = -1;
                resultados.deselectAll();
            }
            catch (RuntimeException ex) { error(ex, "No fue posible buscar clientes."); }
        };
        filtro.addValueChangeListener(e -> cargar.run());
        Button seleccionar = new Button("Seleccionar", VaadinIcon.CHECK.create(), e -> {
            ClientePosDto seleccionado = clienteActivo(visibles, indiceActivo[0], enfocado[0], resultados);
            if (seleccionado == null) { Notification.show("Selecciona un cliente."); return; }
            seleccionarCliente(dialog, seleccionado);
        });
        seleccionar.addClassName("cc-pos-primary");
        resultados.addCellFocusListener(e -> e.getItem().ifPresent(item -> {
            enfocado[0] = item;
            indiceActivo[0] = visibles.indexOf(item);
            resultados.select(item);
        }));
        Shortcuts.addShortcutListener(resultados, () -> {
            ClientePosDto elegido = clienteActivo(visibles, indiceActivo[0], enfocado[0], resultados);
            if (elegido != null) seleccionarCliente(dialog, elegido);
        }, Key.ENTER);
        filtro.addKeyDownListener(Key.ARROW_DOWN,
                e -> moverSeleccionCliente(resultados, visibles, indiceActivo, enfocado, 1));
        filtro.addKeyDownListener(Key.ARROW_UP,
                e -> moverSeleccionCliente(resultados, visibles, indiceActivo, enfocado, -1));
        filtro.addKeyDownListener(Key.ENTER, e -> {
            ClientePosDto elegido = clienteActivo(visibles, indiceActivo[0], enfocado[0], resultados);
            if (elegido != null) seleccionarCliente(dialog, elegido);
        });
        Shortcuts.addShortcutListener(dialog, () -> cerrarSelectorClientes(dialog), Key.ESCAPE);
        resultados.addItemClickListener(e -> seleccionarCliente(dialog, e.getItem()));
        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened() && dialogoClienteActivo == dialog) dialogoClienteActivo = null;
        });
        dialog.add(filtro, resultados, refinar);
        dialog.getFooter().add(seleccionar);
        cargar.run();
        dialog.open();
        UI.getCurrent().beforeClientResponse(filtro, context -> filtro.focus());
    }

    private void seleccionarCliente(Dialog dialog, ClientePosDto cliente) {
        reiniciarDobleEnter();
        seleccionarCliente(cliente);
        dialog.close();
        enfocarProducto();
    }

    private void cerrarSelectorClientes(Dialog dialog) {
        reiniciarDobleEnter();
        dialog.close();
        enfocarProducto();
    }

    private static void moverSeleccionCliente(Grid<ClientePosDto> grid, List<ClientePosDto> visibles,
                                                int[] indiceActivo, ClientePosDto[] enfocado, int desplazamiento) {
        if (visibles.isEmpty()) return;
        int actual = indiceActivo[0];
        int siguiente = actual < 0
                ? (desplazamiento > 0 ? 0 : visibles.size() - 1)
                : Math.max(0, Math.min(visibles.size() - 1, actual + desplazamiento));
        ClientePosDto cliente = visibles.get(siguiente);
        indiceActivo[0] = siguiente;
        enfocado[0] = cliente;
        grid.select(cliente);
        grid.scrollToIndex(siguiente);
    }

    private static ClientePosDto clienteActivo(List<ClientePosDto> visibles, int indiceActivo,
                                                ClientePosDto enfocado, Grid<ClientePosDto> grid) {
        if (indiceActivo >= 0 && indiceActivo < visibles.size()) return visibles.get(indiceActivo);
        if (enfocado != null) return enfocado;
        return grid.asSingleSelect().getValue();
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
        actualizarEstadoLimpiar();
        enfocarProducto();
    }

    private void agregar(ProductoPosDto producto) {
        for (Fila fila : filas) {
            if (fila.id.equals(producto.id())) {
                BigDecimal nuevaCantidad = CantidadPosCalculo.incrementar(fila.cantidad, BigDecimal.ONE);
                try {
                    service.validarCantidad(token, terminal.almacenId(), fila.id, nuevaCantidad);
                    fila.cantidad = nuevaCantidad;
                    fila.recalcularDescuentoCotizado();
                    ventaModificada();
                } catch (RuntimeException ex) { error(ex, "No fue posible aumentar la cantidad."); }
                return;
            }
        }
        try {
            service.validarCantidad(token, terminal.almacenId(), producto.id(), BigDecimal.ONE);
            filas.add(new Fila(producto));
            ventaModificada();
        } catch (RuntimeException ex) { error(ex, "No fue posible agregar el producto."); }
    }

    private void dialogoDescuento() {
        reiniciarDobleEnter();
        if (dialogoDescuentoActivo != null && dialogoDescuentoActivo.isOpened()) return;
        if (dialogoCobroActivo != null && dialogoCobroActivo.isOpened()) return;
        if (filas.isEmpty()) { Notification.show("Agregue al menos un producto antes de aplicar un descuento."); return; }
        if (!puede("facturas.descuentos.aplicar")) { Notification.show("No tienes permiso para aplicar descuentos."); return; }
        suspenderTecladoNumerico();
        try {
            DescuentoVentaDialog dialog = new DescuentoVentaDialog(descuentosService, idempotencia, lineasDescuento(),
                    null, descuentoActual, aplicado -> {
                        descuentoActual = aplicado.descuento();
                        aplicarVistaDescuento(aplicado.vista());
                        refrescar();
                    }, () -> {
                        quitarDescuento();
                        refrescar();
                    }, () -> {
                        dialogoDescuentoActivo = null;
                        enfocarProducto();
                    });
            dialogoDescuentoActivo = dialog;
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible preparar el descuento."); }
    }

    private List<LineaVistaPreviaDescuentoInput> lineasDescuento() {
        return filas.stream().map(f -> new LineaVistaPreviaDescuentoInput(f.lineaId, f.id, f.nombre,
                f.cantidad, f.precio, f.tasa, f.permiteDescuentos)).toList();
    }

    private void dialogoRecargo() {
        reiniciarDobleEnter();
        if (dialogoRecargoActivo != null && dialogoRecargoActivo.isOpened()) return;
        if (dialogoCobroActivo != null && dialogoCobroActivo.isOpened()) return;
        if (filas.isEmpty()) { Notification.show("No hay productos en la venta para aplicar un recargo."); return; }
        if (!puede("facturas.recargos.aplicar")) { Notification.show("No tienes permiso para aplicar recargos."); return; }
        suspenderTecladoNumerico();
        try {
            RecargoVentaDialog dialog = new RecargoVentaDialog(recargosService, idempotencia, lineasRecargo(), recargoActual,
                    aplicado -> { recargoActual=aplicado.recargo(); aplicarVistaRecargo(aplicado.vista()); refrescar(); },
                    () -> { quitarRecargo(); refrescar(); },
                    () -> { dialogoRecargoActivo=null; enfocarProducto(); });
            dialogoRecargoActivo=dialog; dialog.open();
        } catch (RuntimeException ex) { error(ex,"No fue posible preparar el recargo."); }
    }

    private List<LineaVistaPreviaRecargoInput> lineasRecargo() {
        return filas.stream().map(f -> new LineaVistaPreviaRecargoInput(f.lineaId,f.id,f.nombre,f.cantidad,f.precio,f.tasa,f.descuento,f.permiteRecargos)).toList();
    }

    private void aplicarVistaRecargo(VistaPreviaRecargoDto vista) {
        Map<UUID,VistaPreviaRecargoDto.Asignacion> asignaciones=new HashMap<>();
        vista.asignaciones().forEach(x->asignaciones.put(x.lineaId(),x));
        for(Fila fila:filas)fila.recargo=Optional.ofNullable(asignaciones.get(fila.lineaId)).map(VistaPreviaRecargoDto.Asignacion::recargo).orElse(BigDecimal.ZERO);
        recargoVenta=vista.montoRecargo();
    }

    private void quitarRecargo(){recargoActual=null;recargoVenta=BigDecimal.ZERO;filas.forEach(f->f.recargo=BigDecimal.ZERO);}

    private void ventaModificada() {
        if (descuentoActual != null) try {
            DescuentoVentaInput sinAutorizacion = new DescuentoVentaInput(descuentoActual.alcance(), descuentoActual.tipo(),
                    descuentoActual.valor(), descuentoActual.lineaIds(), descuentoActual.motivoId(), descuentoActual.detalle(), null);
            VistaPreviaDescuentoDto vista = descuentosService.previsualizar(
                    new SolicitudVistaPreviaDescuentoInput(idempotencia, sinAutorizacion, lineasDescuento()));
            descuentoActual = sinAutorizacion;
            aplicarVistaDescuento(vista);
            if (vista.requiereAutorizacion()) Notification.show("La venta cambió. El descuento requiere una nueva autorización.");
        } catch (RuntimeException ex) {
            quitarDescuento();
            error(ex, "El descuento fue retirado porque la venta cambió.");
        }
        if(recargoActual!=null)try{RecargoVentaInput sinAutorizacion=new RecargoVentaInput(recargoActual.alcance(),recargoActual.tipo(),recargoActual.valor(),recargoActual.lineaIds(),recargoActual.conceptoId(),recargoActual.motivoId(),recargoActual.detalle(),null);VistaPreviaRecargoDto vista=recargosService.previsualizar(new SolicitudVistaPreviaRecargoInput(idempotencia,sinAutorizacion,lineasRecargo()));recargoActual=sinAutorizacion;aplicarVistaRecargo(vista);if(vista.requiereAutorizacion())Notification.show("La venta cambió. El recargo requiere una nueva autorización.");}catch(RuntimeException ex){quitarRecargo();error(ex,"El recargo fue retirado porque la venta cambió.");}
        refrescar();
    }

    private void aplicarVistaDescuento(VistaPreviaDescuentoDto vista) {
        Map<UUID, VistaPreviaDescuentoDto.Asignacion> asignaciones = new HashMap<>();
        vista.asignaciones().forEach(x -> asignaciones.put(x.lineaId(), x));
        for (Fila fila : filas) fila.descuento = Optional.ofNullable(asignaciones.get(fila.lineaId))
                .map(VistaPreviaDescuentoDto.Asignacion::descuento).orElse(BigDecimal.ZERO);
        descuentoVenta = vista.montoDescuento();
    }

    private void quitarDescuento() {
        descuentoActual = null;
        descuentoVenta = BigDecimal.ZERO;
        filas.forEach(f -> f.descuento = BigDecimal.ZERO);
    }

    private void refrescar() { grid.setItems(filas); actualizarTotales(); actualizarEstadoLimpiar(); }

    private void actualizarTotales() {
        BigDecimal base = BigDecimal.ZERO, tax = BigDecimal.ZERO, grand = BigDecimal.ZERO, discount = BigDecimal.ZERO;
        for (Fila fila : filas) {
            base = base.add(fila.base());
            discount = discount.add(fila.descuento);
            tax = tax.add(fila.impuesto());
            grand = grand.add(fila.total());
        }
        descuentoVenta = discount;
        subtotal.setText(moneda(base));
        descuento.setText(monedaNegativa(descuentoVenta));
        recargo.setText(moneda(recargoVenta));
        impuestos.setText(moneda(tax));
        total.setText(moneda(grand));
        actualizarResumen();
        if (grid.getDataProvider() != null) grid.getDataProvider().refreshAll();
    }

    private void actualizarResumen() {
        resumen.removeAll();
        resumen.add(filaResumen("Subtotal", subtotal));
        if (descuentoVenta.signum() > 0) {
            Div filaDescuento = filaResumen("Descuento", descuento);
            filaDescuento.addClassName("cc-pos-discount");
            resumen.add(filaDescuento);
        }
        if (recargoVenta.signum() > 0) resumen.add(filaResumen("Recargo", recargo));
        resumen.add(filaResumen("ITBIS", impuestos), filaResumen("TOTAL", total));
    }

    private void dialogoCobro() {
        reiniciarDobleEnter();
        if (dialogoCobroActivo != null && dialogoCobroActivo.isOpened()) return;
        if (terminal == null) return;
        if (filas.isEmpty()) { Notification.show("Agregue al menos un producto para cobrar."); return; }
        suspenderTecladoNumerico();
        BigDecimal totalVenta = filas.stream().map(Fila::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        try {
            CreditoVentaPosDto credito = service.creditoDisponible(token,terminal.almacenId(),
                    clienteSeleccionado==null?null:clienteSeleccionado.id(),totalVenta);
            List<MetodoPagoPosDto> metodos = service.metodosPago();
            if (metodos.isEmpty()&&credito==null) { Notification.show("No existen métodos de pago activos para esta empresa."); return; }
            List<CuentaBancariaPosDto> cuentas = metodos.stream().anyMatch(m -> m.requiereCuentaBancaria())
                    ? service.cuentasBancarias() : List.of();
            final PosPaymentDialog[] referencia = new PosPaymentDialog[1];
            PosPaymentDialog dialog = new PosPaymentDialog(totalVenta, terminal.monedaCodigo(), metodos,
                    cuentas,credito,resultado -> confirmarCobro(referencia[0], resultado));
            referencia[0] = dialog;
            dialogoCobroActivo = dialog;
            dialog.addOpenedChangeListener(event -> {
                if (!event.isOpened() && dialogoCobroActivo == dialog) dialogoCobroActivo = null;
            });
            dialog.open();
        } catch (RuntimeException ex) { error(ex, "No fue posible preparar el cobro."); }
    }

    private void dialogoGuardarVenta() {
        reiniciarDobleEnter();
        if (dialogoGuardarVentaActivo != null && dialogoGuardarVentaActivo.isOpened()) return;
        if (dialogoCobroActivo != null && dialogoCobroActivo.isOpened()) return;
        if (filas.isEmpty()) { Notification.show("No hay una venta para guardar."); return; }
        if (!puede("ventas_guardadas.guardar")) { Notification.show("No tienes permiso para guardar ventas."); return; }
        suspenderTecladoNumerico();
        Dialog dialog = dialogo("GUARDAR VENTA");
        dialog.addClassName("cc-save-sale-dialog");
        dialogoGuardarVentaActivo = dialog;
        Paragraph ayuda = new Paragraph(ventaGuardadaActivaNumero == null
                ? "Guarda esta venta para continuarla posteriormente."
                : "Actualiza la venta " + ventaGuardadaActivaNumero + " para continuarla posteriormente.");
        TextField referencia = new TextField("Referencia");
        referencia.setPlaceholder("Cliente regresa / Pedido mostrador...");
        referencia.setMaxLength(150);
        referencia.setWidthFull();
        referencia.setValue(valor(referenciaVentaGuardada, ""));
        TextArea nota = new TextArea("Observación");
        nota.setMaxLength(1000);
        nota.setWidthFull();
        nota.setValue(observacion.getValue());
        BigDecimal totalActual = filas.stream().map(Fila::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(recargoVenta);
        Div resumenGuardado = new Div(new Span(filas.size() + (filas.size() == 1 ? " producto" : " productos")),
                new Span(terminal.monedaCodigo() + " " + dinero(totalActual)));
        resumenGuardado.addClassName("cc-save-sale-summary");
        dialog.add(ayuda, referencia, nota, resumenGuardado);
        AppActionButton guardar = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN,
                "Guardar venta", null);
        AppActionButton cancelar = new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN,
                "Cancelar", e -> dialog.close());
        Runnable accion = () -> {
            guardar.setEnabled(false);
            try {
                List<LineaVentaGuardadaInput> detalle = filas.stream().map(f ->
                        new LineaVentaGuardadaInput(f.lineaId, f.id, f.cantidad, f.precio, f.tasa,
                                f.cotizacionLineaOrigenId,f.descuento)).toList();
                VentaGuardadaResultadoDto resultado = ventasGuardadas.guardar(new GuardarVentaInput(ventaGuardadaActivaId,
                        token, terminal.almacenId(), clienteSeleccionado == null ? null : clienteSeleccionado.id(),
                        referencia.getValue(), nota.getValue(), descuentoVenta, recargoVenta, descuentoActual, recargoActual,
                        cotizacionOrigenId, detalle));
                dialog.close();
                Notification.show("Venta " + resultado.numero() + " guardada.");
                prepararNuevaVenta();
                refrescar();
                if (dialogoVentasGuardadasActivo != null && dialogoVentasGuardadasActivo.isOpened()) {
                    dialogoVentasGuardadasActivo.ventaActualGuardada();
                } else {
                    actualizarIndicadorVentasGuardadas();
                    enfocarProducto();
                }
            } catch (RuntimeException ex) {
                guardar.setEnabled(true);
                error(ex, "No fue posible guardar la venta.");
            }
        };
        guardar.addClickListener(e -> accion.run());
        Shortcuts.addShortcutListener(dialog, () -> accion.run(), Key.ENTER);
        dialog.getFooter().add(guardar, cancelar);
        dialog.addOpenedChangeListener(e -> { if (e.isOpened()) referencia.focus(); });
        dialog.addClosedListener(e -> {
            if (dialogoGuardarVentaActivo == dialog) dialogoGuardarVentaActivo = null;
        });
        dialog.open();
    }

    private void dialogoVentasGuardadas() {
        reiniciarDobleEnter();
        if (!puede("ventas_guardadas.consultar") && !puede("ventas_guardadas.guardar")) {
            Notification.show("No tienes permiso para administrar ventas guardadas.");
            return;
        }
        if (dialogoVentasGuardadasActivo != null && dialogoVentasGuardadasActivo.isOpened()) return;
        suspenderTecladoNumerico();
        VentasGuardadasDialog dialog = new VentasGuardadasDialog(ventasGuardadas, token, !filas.isEmpty(),
                this::dialogoGuardarVenta, id -> recuperarVentaGuardada(id, dialogoVentasGuardadasActivo),
                this::actualizarIndicadorVentasGuardadas, () -> {
                    dialogoVentasGuardadasActivo = null;
                    actualizarIndicadorVentasGuardadas();
                    enfocarProducto();
                });
        dialogoVentasGuardadasActivo = dialog;
        dialog.open();
    }

    private void dialogoCargarCotizacion() {
        reiniciarDobleEnter();
        if (dialogoCotizacionesActivo != null && dialogoCotizacionesActivo.isOpened()) return;
        if (dialogoCobroActivo != null && dialogoCobroActivo.isOpened()) return;
        if (!puede("cotizaciones.ver")) {
            Notification.show("No tienes permiso para consultar cotizaciones.");
            return;
        }
        suspenderTecladoNumerico();
        CargarCotizacionDialog dialog = new CargarCotizacionDialog(cotizacionesService, this::cargarCotizacion);
        dialogoCotizacionesActivo = dialog;
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened() && dialogoCotizacionesActivo == dialog) {
                dialogoCotizacionesActivo = null;
                enfocarProducto();
            }
        });
        dialog.open();
    }

    private void cargarCotizacion(UUID id) {
        if (tieneVentaEnCurso()) {
            Dialog aviso=dialogo("No se puede cargar la cotización");
            aviso.add(new Paragraph("Hay una venta en curso. Guárdela o límpiela antes de cargar una cotización."));
            aviso.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->aviso.close()));
            aviso.open();
            return;
        }
        try {
            CotizacionPosCargaDto carga=cotizacionPosService.cargar(token,terminal.almacenId(),id);
            List<Fila> nuevas=new ArrayList<>();
            for(CotizacionPosCargaDto.Linea linea:carga.lineas()) {
                Fila fila=new Fila(UUID.randomUUID(),linea.producto(),linea.cotizacionLineaId(),linea.descuentoUnitario());
                fila.cantidad=linea.cantidad();
                fila.descuento=linea.descuentoUnitario().multiply(linea.cantidad()).setScale(4,RoundingMode.HALF_UP);
                nuevas.add(fila);
            }
            filas.clear();filas.addAll(nuevas);cotizacionOrigenId=carga.cotizacionId();
            seleccionarCliente(carga.cliente());idempotencia=UUID.randomUUID();
            descuentoVenta=filas.stream().map(f->f.descuento).reduce(BigDecimal.ZERO,BigDecimal::add);
            refrescar();
            if(dialogoCotizacionesActivo!=null)dialogoCotizacionesActivo.close();
            Notification.show("Cotización "+carga.numero()+" cargada.");
            enfocarProducto();
        } catch(RuntimeException ex) {
            error(ex,"No fue posible cargar la cotización.");
        }
    }

    private void recuperarVentaGuardada(UUID id, Dialog listado) {
        if (!filas.isEmpty()) {
            Notification.show("Hay una venta en curso. Guárdela o límpiela antes de recuperar otra venta.");
            return;
        }
        try {
            VentaGuardadaRecuperadaDto venta = ventasGuardadas.recuperar(id, token, terminal.almacenId());
            filas.clear();
            for (VentaGuardadaRecuperadaDto.Producto p : venta.productos()) {
                Fila fila = new Fila(p.lineaId(), new ProductoPosDto(p.id(), p.codigo(), p.nombre(), null, p.unidad(), p.precio(),
                        p.tasaImpuesto(), p.existencia(), p.controlaExistencia(), p.permiteNegativo(), p.permiteDescuentos(), p.permiteRecargos()),
                        p.cotizacionLineaOrigenId(),p.cantidad().signum()==0?BigDecimal.ZERO:p.descuento().divide(p.cantidad(),4,RoundingMode.HALF_UP));
                fila.cantidad = p.cantidad();
                fila.descuento = p.descuento();
                filas.add(fila);
            }
            ventaGuardadaActivaId = venta.id();
            ventaGuardadaActivaNumero = venta.numero();
            referenciaVentaGuardada = venta.referencia();
            cotizacionOrigenId = venta.cotizacionOrigenId();
            idempotencia = UUID.randomUUID();
            descuentoActual = venta.detalleDescuento();
            if (descuentoActual != null) {
                VistaPreviaDescuentoDto vista = descuentosService.previsualizar(
                        new SolicitudVistaPreviaDescuentoInput(idempotencia, descuentoActual, lineasDescuento()));
                aplicarVistaDescuento(vista);
                if (vista.requiereAutorizacion()) Notification.show("El descuento recuperado requiere una nueva autorización antes de cobrar.");
            } else descuentoVenta = BigDecimal.ZERO;
            recargoVenta = venta.recargo();
            recargoActual = venta.detalleRecargo();
            if(recargoActual!=null)aplicarVistaRecargo(recargosService.previsualizar(new SolicitudVistaPreviaRecargoInput(idempotencia,recargoActual,lineasRecargo())));
            observacion.setValue(valor(venta.observacion(), ""));
            seleccionarCliente(venta.cliente());
            refrescar();
            listado.close();
            Notification.show("Venta " + venta.numero() + " recuperada.");
        } catch (RuntimeException ex) {
            if (dialogoVentasGuardadasActivo != null && dialogoVentasGuardadasActivo.isOpened()) {
                dialogoVentasGuardadasActivo.refrescarListado();
            }
            error(ex, "No fue posible recuperar la venta.");
        }
    }

    private void actualizarIndicadorVentasGuardadas() {
        if (guardarVentaRapida == null) return;
        guardarVentaRapida.getElement().removeAttribute("data-saved-count");
        if (!puede("ventas_guardadas.consultar") || terminal == null) return;
        try {
            long cantidad = ventasGuardadas.contar(token);
            if (cantidad > 0) guardarVentaRapida.getElement().setAttribute("data-saved-count",
                    cantidad > 99 ? "99+" : Long.toString(cantidad));
        } catch (RuntimeException ignored) { }
    }

    private void dialogoUltimasFacturas() {
        reiniciarDobleEnter();
        if (dialogoUltimasActivo != null && dialogoUltimasActivo.isOpened()) return;
        if (dialogoCobroActivo != null && dialogoCobroActivo.isOpened()) return;
        if (dialogoClienteActivo != null && dialogoClienteActivo.isOpened()) return;
        suspenderTecladoNumerico();
        UltimasFacturasDialog dialog = new UltimasFacturasDialog(ultimasFacturas, this::abrirPdf, this::enfocarProducto);
        dialogoUltimasActivo = dialog;
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened() && dialogoUltimasActivo == dialog) dialogoUltimasActivo = null;
        });
        dialog.open();
    }

    private void dialogoDevoluciones() {
        reiniciarDobleEnter();
        if (dialogoDevolucionesActivo != null && dialogoDevolucionesActivo.isOpened()) return;
        if (dialogoCobroActivo != null && dialogoCobroActivo.isOpened()) return;
        if (!puede("devoluciones_venta.ver")) {
            Notification.show("No tienes permiso para consultar devoluciones de ventas.");
            return;
        }
        suspenderTecladoNumerico();
        DevolucionesVentasDialog dialog = new DevolucionesVentasDialog(devolucionesService,
                this::abrirPdfDevolucion, this::enfocarProducto);
        dialogoDevolucionesActivo = dialog;
        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened() && dialogoDevolucionesActivo == dialog) dialogoDevolucionesActivo = null;
        });
        dialog.open();
    }

    private void confirmarCobro(Dialog dialog, ResultadoCobroPosInput cobro) {
        try {
            List<LineaVentaInput> lineas = filas.stream().map(f -> new LineaVentaInput(f.lineaId, f.id, f.cantidad, f.precio,
                    f.cotizacionLineaOrigenId,f.descuento)).toList();
            FacturaVentaResultadoDto resultado = service.confirmar(new ConfirmarVentaInput(token, terminal.almacenId(),
                    clienteSeleccionado == null ? null : clienteSeleccionado.id(), observacion.getValue(),
                    idempotencia, ventaGuardadaActivaId, descuentoActual, recargoActual,cotizacionOrigenId,
                    cobro.ventaCredito(),lineas,cobro.aplicaciones()));
            dialog.close();
            Notification.show(cobro.ventaCredito()?"Factura "+resultado.numero()+" emitida a crédito.":
                    "Factura " + resultado.numero() + " confirmada. Cambio: " + dinero(resultado.cambio()));
            abrirPdf(resultado);
            construirPos();
        } catch (RuntimeException ex) {
            if (dialog instanceof PosPaymentDialog paymentDialog) paymentDialog.reactivarConfirmacion();
            error(ex, cobro.ventaCredito()?"No fue posible emitir la factura a crédito.":"No fue posible confirmar el cobro.");
        }
    }

    private void abrirPdf(FacturaVentaResultadoDto factura) {
        abrirPdf(factura.id());
    }

    private void abrirPdf(UUID facturaId) {
        try {
            ArchivoPdfDto archivo = documentos.generar(facturaId);
            StreamResource recurso = new StreamResource(archivo.nombreArchivo(),
                    () -> new ByteArrayInputStream(archivo.contenido()));
            recurso.setContentType("application/pdf");
            recurso.setCacheTime(0);
            var registro = VaadinSession.getCurrent().getResourceRegistry().registerResource(recurso);
            UI.getCurrent().getPage().open(registro.getResourceUri().toString(), "_blank");
        } catch (RuntimeException ex) {
            Notification.show("No fue posible abrir el PDF de la factura. Intente nuevamente.");
        }
    }

    private void abrirPdfDevolucion(UUID devolucionId) {
        try {
            ArchivoPdfDto archivo = devolucionesPdf.generar(devolucionId);
            StreamResource recurso = new StreamResource(archivo.nombreArchivo(),
                    () -> new ByteArrayInputStream(archivo.contenido()));
            recurso.setContentType("application/pdf");
            recurso.setCacheTime(0);
            var registro = VaadinSession.getCurrent().getResourceRegistry().registerResource(recurso);
            UI.getCurrent().getPage().open(registro.getResourceUri().toString(), "_blank");
            Notification.show("Devolución confirmada correctamente.");
        } catch (RuntimeException ex) {
            Notification.show("La devolución fue confirmada, pero no fue posible abrir el PDF. Puede reimprimirlo después.");
        }
    }

    private void suspenderTecladoNumerico() { tecladoNumerico.finishAndClear(); }

    private void enfocarProducto() { if (productoBuscar != null) productoBuscar.focus(); }

    private void pendiente(String funcion) {
        if ("Recuento de caja".equals(funcion)) {
            if (terminal == null || !terminal.cajaAbierta() || terminal.sesionCajaId() == null) {
                Notification.show("La caja debe estar abierta para realizar un recuento."); return;
            }
            if (!puede("recuentos_caja.crear")) { Notification.show("No tiene permisos para realizar recuentos de caja."); return; }
            new RecuentoCajaDialog(terminal.sesionCajaId(), recuentosCaja, this::abrirPdfRecuento).open(); return;
        }
        Notification.show(funcion + " estará disponible próximamente.");
    }

    private void abrirPdfRecuento(UUID id) {
        try {
            ArchivoPdfDto archivo = recuentosCajaPdf.generar(id);
            StreamResource recurso = new StreamResource(archivo.nombreArchivo(),
                    () -> new ByteArrayInputStream(archivo.contenido()));
            recurso.setContentType("application/pdf"); recurso.setCacheTime(0);
            var registro = VaadinSession.getCurrent().getResourceRegistry().registerResource(recurso);
            UI.getCurrent().getPage().open(registro.getResourceUri().toString(), "_blank");
        } catch (RuntimeException ex) { error(ex, "No fue posible abrir el PDF del recuento."); }
    }

    private static boolean puede(String permiso) {
        return TenantContext.principalActual().permisos().contains(permiso);
    }

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

    private String monedaNegativa(BigDecimal valor) {
        BigDecimal importe = valor == null ? BigDecimal.ZERO : valor.abs();
        return terminal == null ? "-" + dinero(importe) : "-" + terminal.monedaCodigo() + " " + dinero(importe);
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

    static String dinero(BigDecimal valor) {
        return PosNumberFormatter.money(valor);
    }

    private static String formatoCantidad(BigDecimal valor) {
        return PosNumberFormatter.quantity(valor);
    }

    private static String valor(String valor, String defecto) {
        return valor == null || valor.isBlank() ? defecto : valor;
    }

    private static void error(RuntimeException error, String fallback) {
        UiErrorHandler.show(error, fallback);
    }

    private static class Fila {
        private final UUID lineaId;
        private final UUID id;
        private final String codigo;
        private final String nombre;
        private final String unidad;
        private final BigDecimal precio;
        private final BigDecimal tasa;
        private final BigDecimal existencia;
        private final boolean controlaExistencia;
        private final boolean permiteDescuentos;
        private final boolean permiteRecargos;
        private final boolean permiteFracciones;
        private final UUID cotizacionLineaOrigenId;
        private final BigDecimal descuentoUnitarioCotizado;
        private BigDecimal cantidad = BigDecimal.ONE;
        private BigDecimal descuento = BigDecimal.ZERO;
        private BigDecimal recargo = BigDecimal.ZERO;

        private Fila(ProductoPosDto producto) {
            this(UUID.randomUUID(), producto);
        }

        private Fila(UUID lineaId, ProductoPosDto producto) {
            this(lineaId,producto,null,BigDecimal.ZERO);
        }

        private Fila(UUID lineaId, ProductoPosDto producto,UUID cotizacionLineaOrigenId,BigDecimal descuentoUnitarioCotizado) {
            this.lineaId = lineaId == null ? UUID.randomUUID() : lineaId;
            id = producto.id();
            codigo = producto.codigo();
            nombre = producto.nombre();
            unidad = valor(producto.unidad(), "—");
            precio = producto.precio();
            tasa = producto.tasaImpuesto();
            existencia = producto.existencia();
            controlaExistencia = producto.controlaExistencia();
            permiteDescuentos = producto.permiteDescuentos();
            permiteRecargos = producto.permiteRecargos();
            permiteFracciones = permiteFracciones(producto.unidad());
            this.cotizacionLineaOrigenId=cotizacionLineaOrigenId;
            this.descuentoUnitarioCotizado=descuentoUnitarioCotizado==null?BigDecimal.ZERO:descuentoUnitarioCotizado;
        }

        private String codigo() { return codigo; }
        private String nombre() { return nombre; }
        private String unidad() { return unidad; }
        private BigDecimal base() { return precio.multiply(cantidad).setScale(4, RoundingMode.HALF_UP); }
        private void recalcularDescuentoCotizado(){if(cotizacionLineaOrigenId!=null)descuento=descuentoUnitarioCotizado.multiply(cantidad).setScale(4,RoundingMode.HALF_UP);}
        private BigDecimal baseImponible() { return base().subtract(descuento).add(recargo).setScale(4, RoundingMode.HALF_UP); }
        private BigDecimal impuesto() { return baseImponible().multiply(tasa).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP); }
        private BigDecimal total() { return baseImponible().add(impuesto()); }

        private static boolean permiteFracciones(String unidad) {
            if (unidad == null) return false;
            return Set.of("kg", "lb", "m", "l", "gal", "h")
                    .contains(unidad.trim().toLowerCase(Locale.ROOT));
        }
    }
}
