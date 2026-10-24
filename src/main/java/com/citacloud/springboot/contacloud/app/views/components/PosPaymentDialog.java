package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.TipoMetodoPagoVenta;
import com.citacloud.springboot.contacloud.app.services.CobroVentaCalculo;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;

import java.math.*;
import java.util.*;
import java.util.function.Consumer;

public class PosPaymentDialog extends Dialog {
    private final BigDecimal total;
    private final String moneda;
    private final List<MetodoPagoPosDto> metodos;
    private final List<CuentaBancariaPosDto> cuentas;
    private final Consumer<List<AplicacionCobroInput>> onConfirmar;
    private final VerticalLayout contenido = new VerticalLayout();

    private final BigDecimalField recibidoSimple = new BigDecimalField("Recibido");
    private final TextField referenciaSimple = new TextField("Referencia / autorización");
    private final ComboBox<CuentaBancariaPosDto> cuentaSimple = new ComboBox<>("Cuenta destino");
    private final TextField bancoSimple = new TextField("Banco");
    private final Span cambioSimple = new Span();
    private final Div cambioSimpleFila = new Div();
    private final Div recibidoSimpleFila = new Div();
    private final Div importesRapidos = new Div();
    private final Button exacto = new Button();

    private final List<AplicacionTemporal> aplicaciones = new ArrayList<>();
    private final Grid<AplicacionTemporal> grid = new Grid<>();
    private final PosDialogPagination paginacionPagos = new PosDialogPagination(r -> cargarPaginaPagos());
    private final BigDecimalField monto = new BigDecimalField("Monto aplicado");
    private final BigDecimalField recibido = new BigDecimalField("Recibido");
    private final TextField referencia = new TextField("Referencia / autorización");
    private final ComboBox<CuentaBancariaPosDto> cuenta = new ComboBox<>("Cuenta destino");
    private final TextField banco = new TextField("Banco");
    private final Span cambio = new Span();
    private final Span aplicadoValor = new Span();
    private final Span pendienteValor = new Span();
    private final Span completo = new Span("✓ Cobro completo");
    private final Button aplicar = new Button("Aplicar", VaadinIcon.CHECK.create());
    private final Button confirmar = new Button("Confirmar cobro", VaadinIcon.MONEY.create());
    private final Button cancelar = new Button("Cancelar", VaadinIcon.CLOSE.create(), e -> cerrar());
    private final Div accionesFooter = new Div();
    private final Button alternarModo = new Button();
    private final DoubleSpaceDetector doubleSpace = new DoubleSpaceDetector();
    private final PosNumericKeypad keypad = new PosNumericKeypad(PosNumericKeypad.Mode.POPOVER);
    private Button tecladoRecibidoSimple;
    private Button tecladoMonto;
    private Button tecladoRecibido;
    private Div controlRecibidoSimple;
    private Div controlMonto;
    private Div controlRecibido;

    private MetodoPagoPosDto metodoSimple;
    private MetodoPagoPosDto metodoActual;
    private boolean modoMultiple;
    private boolean simpleConfigurado;
    private boolean editorMultipleConfigurado;

    public PosPaymentDialog(BigDecimal total, String moneda, List<MetodoPagoPosDto> metodos,
                            List<CuentaBancariaPosDto> cuentas,
                            Consumer<List<AplicacionCobroInput>> onConfirmar) {
        this.total = escala(total);
        this.moneda = moneda;
        this.metodos = List.copyOf(metodos);
        this.cuentas = List.copyOf(cuentas);
        this.onConfirmar = onConfirmar;
        setHeaderTitle("COBRAR");
        setWidth("min(900px, 96vw)");
        addClassName("cc-pos-payment-dialog");
        getElement().setAttribute("aria-label", "Cobrar venta");
        contenido.setPadding(false);
        contenido.setSpacing(false);
        contenido.addClassName("cc-pos-payment-content");
        add(contenido);
        cancelar.addClassNames("cc-pos-payment-cancel", "cc-pos-payment-footer-action");
        cancelar.setTooltipText("Cancelar cobro");
        cancelar.getElement().setAttribute("aria-label", "Cancelar cobro");
        confirmar.addClassNames("cc-pos-charge-confirm", "cc-pos-payment-footer-action");
        confirmar.setTooltipText("Confirmar cobro");
        confirmar.getElement().setAttribute("aria-label", "Confirmar cobro");
        confirmar.addClickListener(e -> confirmar());
        alternarModo.addClickListener(e -> alternarModo());
        alternarModo.addClassNames("cc-pos-payment-mode", "cc-pos-payment-method");
        exacto.addClassNames("cc-pos-cash-exact", "cc-pos-cash-option");
        exacto.setTooltipText("Usar monto exacto (doble espacio)");
        exacto.addClickListener(e -> usarMontoExacto());
        configurarTecladoNumerico();
        normalizarImportesAlPerderFoco();
        registrarDobleEspacio();
        Shortcuts.addShortcutListener(this, () -> {
            if (!keypad.closeIfOpen()) cerrar();
        }, Key.ESCAPE);
        addOpenedChangeListener(e -> {
            if (e.isOpened()) mostrarSimple();
            else reiniciarDobleEspacio();
        });
    }

    private void configurarTecladoNumerico() {
        tecladoRecibidoSimple = keypad.bind(recibidoSimple, "CASH_RECEIVED", PosNumericKeypad.Options.moneyLive(confirmar::focus),
                value -> actualizarSimple());
        tecladoMonto = keypad.bind(monto, "MULTI_APPLIED", PosNumericKeypad.Options.moneyLive(() -> {
            if (recibido.isVisible()) recibido.focus();
            else aplicar.focus();
        }), value -> actualizarCambio());
        tecladoRecibido = keypad.bind(recibido, "MULTI_CASH_RECEIVED", PosNumericKeypad.Options.moneyLive(aplicar::focus),
                value -> actualizarCambio());
        keypad.closeOnFocus(referenciaSimple, cuentaSimple, bancoSimple, referencia, cuenta, banco);
    }

    private void registrarDobleEspacio() {
        getElement().addEventListener("cc-pos-space", event -> {
            long timestamp = Math.round(event.getEventData().get("event.detail.timestamp").asDouble());
            if (doubleSpace.register(timestamp, atajoExactoDisponible())) usarMontoExacto();
        }).addEventData("event.detail.timestamp");
        getElement().addEventListener("cc-pos-space-reset", event -> doubleSpace.reset());
        addAttachListener(event -> event.getUI().getPage().executeJs("""
                const host = $0;
                if (host.__ccPosSpaceHandler) document.removeEventListener('keydown', host.__ccPosSpaceHandler, true);
                const reset = () => {
                  host.dispatchEvent(new CustomEvent('cc-pos-space-reset'));
                };
                host.__ccPosSpaceHandler = event => {
                  if (event.code !== 'Space' || event.repeat) return;
                  if (!host.opened || !host.ccCashExactEnabled) { reset(); return; }
                  const overlays = document.querySelectorAll('vaadin-dialog-overlay[opened], vaadin-confirm-dialog-overlay[opened]');
                  if (overlays.length > 1) { reset(); return; }
                  const path = event.composedPath ? event.composedPath() : [event.target];
                  const decimalField = path.some(node => node?.tagName === 'VAADIN-BIG-DECIMAL-FIELD');
                  const incompatible = path.some(node => {
                    const tag = node?.tagName;
                    return node?.isContentEditable
                      || tag === 'BUTTON'
                      || tag === 'TEXTAREA'
                      || tag === 'VAADIN-TEXT-AREA'
                      || tag === 'VAADIN-TEXT-FIELD'
                      || tag === 'VAADIN-COMBO-BOX'
                      || (tag === 'INPUT' && !decimalField);
                  });
                  if (incompatible) { reset(); return; }
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  host.dispatchEvent(new CustomEvent('cc-pos-space', {detail: {timestamp: performance.now()}}));
                };
                document.addEventListener('keydown', host.__ccPosSpaceHandler, true);
                """, getElement()));
        addDetachListener(event -> event.getUI().getPage().executeJs("""
                const host = $0;
                if (host.__ccPosSpaceHandler) document.removeEventListener('keydown', host.__ccPosSpaceHandler, true);
                host.__ccPosSpaceHandler = null;
                """, getElement()));
    }

    private void mostrarSimple() {
        reiniciarDobleEspacio();
        setHeaderTitle("COBRAR");
        modoMultiple = false;
        contenido.removeAll();
        contenido.add(totalBox(true), titulo("MÉTODO DE PAGO"), accesosMetodos(this::seleccionarSimple), construirSimple());
        alternarModo.setText("Pago múltiple");
        alternarModo.setIcon(VaadinIcon.CREDIT_CARD.create());
        alternarModo.setTooltipText("Dividir el cobro entre varios métodos de pago");
        actualizarFooter();
        MetodoPagoPosDto efectivo = metodos.stream().filter(MetodoPagoPosDto::efectivo).findFirst()
                .orElseGet(() -> metodos.stream().filter(MetodoPagoPosDto::predeterminado).findFirst()
                        .orElse(metodos.isEmpty() ? null : metodos.getFirst()));
        seleccionarSimple(efectivo);
    }

    private Component construirSimple() {
        cuentaSimple.setItems(cuentas);
        cuentaSimple.setItemLabelGenerator(CuentaBancariaPosDto::nombre);
        recibidoSimple.setLabel("");
        recibidoSimple.getElement().setAttribute("aria-label", "Recibido");
        recibidoSimple.setPrefixComponent(new Span(moneda));
        recibidoSimple.setWidthFull();
        referenciaSimple.setWidthFull();
        cuentaSimple.setWidthFull();
        bancoSimple.setWidthFull();
        if (!simpleConfigurado) {
            recibidoSimple.addValueChangeListener(e -> actualizarSimple());
            referenciaSimple.addValueChangeListener(e -> actualizarSimple());
            cuentaSimple.addValueChangeListener(e -> actualizarSimple());
            recibidoSimple.addKeyPressListener(Key.ENTER, e -> confirmar.focus());
            simpleConfigurado = true;
        }
        importesRapidos.removeAll();
        importesRapidos.addClassName("cc-pos-cash-quick");
        exacto.setText(PosNumberFormatter.money(total) + " Exacto");
        importesRapidos.add(exacto);
        for (String valor : List.of("20", "50", "100", "200", "500", "1000")) {
            BigDecimal importe = new BigDecimal(valor);
            Button rapido = new Button(String.format(Locale.US, "%,.0f", importe), e -> {
                recibidoSimple.setValue(PosNumberFormatter.moneyInput(importe));
                recibidoSimple.focus();
                keypad.close();
            });
            rapido.addClassName("cc-pos-cash-option");
            rapido.setTooltipText("Recibido " + dinero(importe));
            importesRapidos.add(rapido);
        }
        Span recibidoEtiqueta = new Span("Recibido");
        recibidoEtiqueta.addClassName("cc-pos-received-label");
        controlRecibidoSimple = controlNumerico(recibidoSimple, tecladoRecibidoSimple);
        recibidoSimpleFila.removeAll();
        recibidoSimpleFila.add(recibidoEtiqueta, controlRecibidoSimple);
        recibidoSimpleFila.addClassName("cc-pos-received-row");
        VerticalLayout panel = new VerticalLayout(recibidoSimpleFila, importesRapidos,
                referenciaSimple, cuentaSimple, bancoSimple);
        panel.add(keypad);
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.addClassName("cc-pos-simple-payment");
        return panel;
    }

    private void seleccionarSimple(MetodoPagoPosDto metodo) {
        if (metodo == null) return;
        reiniciarDobleEspacio();
        keypad.clearTarget();
        metodoSimple = metodo;
        recibidoSimple.clear();
        referenciaSimple.clear();
        cuentaSimple.clear();
        bancoSimple.clear();
        recibidoSimpleFila.setVisible(metodo.efectivo());
        cambioSimpleFila.setVisible(metodo.efectivo());
        importesRapidos.setVisible(metodo.efectivo());
        referenciaSimple.setVisible(metodo.requiereReferencia() || metodo.electronico()
                || metodo.tipo() == TipoMetodoPagoVenta.BANK_TRANSFER);
        referenciaSimple.setRequiredIndicatorVisible(metodo.requiereReferencia());
        cuentaSimple.setVisible(metodo.requiereCuentaBancaria());
        bancoSimple.setVisible(metodo.tipo() == TipoMetodoPagoVenta.CHECK);
        actualizarSimple();
        keypad.refresh(recibidoSimple);
        if (metodo.efectivo()) recibidoSimple.focus();
        else if (metodo.requiereCuentaBancaria()) cuentaSimple.focus();
        else referenciaSimple.focus();
    }

    private void actualizarSimple() {
        BigDecimal cambioValor = metodoSimple != null && metodoSimple.efectivo()
                ? CobroVentaCalculo.cambioNoNegativo(total, escala(recibidoSimple.getValue()))
                : BigDecimal.ZERO;
        cambioSimple.setText(dinero(cambioValor));
        cambioSimple.removeClassName("cc-pos-payment-change-positive");
        if (cambioValor.signum() > 0) cambioSimple.addClassName("cc-pos-payment-change-positive");
        confirmar.setEnabled(simpleValido());
        actualizarDisponibilidadAtajoExacto();
    }

    private boolean simpleValido() {
        if (metodoSimple == null) return false;
        if (metodoSimple.efectivo() && escala(recibidoSimple.getValue()).compareTo(total) < 0) return false;
        if (metodoSimple.requiereReferencia() && referenciaSimple.getValue().trim().isEmpty()) return false;
        return !metodoSimple.requiereCuentaBancaria() || cuentaSimple.getValue() != null;
    }

    private void mostrarMultiple() {
        reiniciarDobleEspacio();
        setHeaderTitle("COBRAR · PAGO MÚLTIPLE");
        modoMultiple = true;
        actualizarDisponibilidadAtajoExacto();
        contenido.removeAll();
        contenido.add(totalBox(false), titulo("DISTRIBUIR PAGO"), accesosMetodos(this::seleccionarMultiple),
                construirEditorMultiple(), titulo("PAGOS APLICADOS"), grid, paginacionPagos, construirResumenMultiple());
        alternarModo.setText("Volver a pago simple");
        alternarModo.setIcon(VaadinIcon.ARROW_BACKWARD.create());
        alternarModo.setTooltipText("Volver a pago simple");
        configurarGrid();
        actualizarFooter();
        seleccionarMultiple(metodos.stream().filter(MetodoPagoPosDto::efectivo).findFirst()
                .orElse(metodos.isEmpty() ? null : metodos.getFirst()));
    }

    private Component construirEditorMultiple() {
        cuenta.setItems(cuentas);
        cuenta.setItemLabelGenerator(CuentaBancariaPosDto::nombre);
        monto.setPrefixComponent(new Span(moneda));
        recibido.setPrefixComponent(new Span(moneda));
        monto.setWidthFull();
        recibido.setWidthFull();
        referencia.setWidthFull();
        cuenta.setWidthFull();
        banco.setWidthFull();
        cambio.addClassName("cc-pos-payment-change");
        aplicar.addClassName("cc-pos-payment-apply");
        if (!editorMultipleConfigurado) {
            aplicar.addClickListener(e -> aplicar());
            monto.addValueChangeListener(e -> actualizarCambio());
            recibido.addValueChangeListener(e -> actualizarCambio());
            editorMultipleConfigurado = true;
        }
        controlMonto = controlNumerico(monto, tecladoMonto);
        controlRecibido = controlNumerico(recibido, tecladoRecibido);
        HorizontalLayout campos = new HorizontalLayout(controlMonto, controlRecibido, referencia, cuenta, banco);
        campos.setWidthFull();
        campos.setAlignItems(FlexComponent.Alignment.END);
        campos.addClassName("cc-pos-payment-fields");
        Div editor = new Div(campos, keypad, new Div(cambio, aplicar));
        editor.addClassName("cc-pos-payment-editor");
        return editor;
    }

    private Component construirResumenMultiple() {
        Div resumen = new Div(filaResumen("Total aplicado", aplicadoValor),
                filaResumen("Pendiente", pendienteValor), completo);
        resumen.addClassName("cc-pos-payment-summary");
        actualizarResumen();
        return resumen;
    }

    private Component totalBox(boolean incluirCambio) {
        Span totalImporte = new Span(dinero(total));
        totalImporte.addClassName("cc-pos-payment-total-value");
        Span totalEtiqueta = new Span("TOTAL A COBRAR");
        totalEtiqueta.addClassName("cc-pos-payment-metric-label");
        Div totalFila = new Div(totalEtiqueta, totalImporte);
        totalFila.addClassName("cc-pos-payment-total-row");
        Div totalBox = new Div(totalFila);
        if (incluirCambio) {
            Span cambioEtiqueta = new Span("CAMBIO");
            cambioEtiqueta.addClassName("cc-pos-payment-metric-label");
            cambioSimple.addClassName("cc-pos-payment-change-value");
            cambioSimpleFila.removeAll();
            cambioSimpleFila.add(cambioEtiqueta, cambioSimple);
            cambioSimpleFila.addClassName("cc-pos-payment-change-row");
            totalBox.add(cambioSimpleFila);
        }
        totalBox.addClassName("cc-pos-payment-total");
        return totalBox;
    }

    private H4 titulo(String texto) {
        H4 titulo = new H4(texto);
        titulo.addClassName("cc-pos-payment-heading");
        return titulo;
    }

    private Component accesosMetodos(Consumer<MetodoPagoPosDto> seleccionar) {
        HorizontalLayout accesos = new HorizontalLayout();
        accesos.addClassName("cc-pos-payment-methods");
        accesos.setPadding(false);
        accesos.setSpacing(true);
        List<MetodoPagoPosDto> principales = metodosPrincipales(metodos);
        principales.forEach(m -> {
            Button boton = new Button(etiquetaCorta(m), icono(m), e -> seleccionar.accept(m));
            boton.addClassName("cc-pos-payment-method");
            boton.setTooltipText(m.nombre());
            accesos.add(boton);
        });
        accesos.add(alternarModo);
        return accesos;
    }

    static List<MetodoPagoPosDto> metodosPrincipales(List<MetodoPagoPosDto> metodos) {
        List<MetodoPagoPosDto> resultado = new ArrayList<>();
        for (TipoMetodoPagoVenta tipo : List.of(TipoMetodoPagoVenta.CASH, TipoMetodoPagoVenta.CREDIT_CARD,
                TipoMetodoPagoVenta.DEBIT_CARD, TipoMetodoPagoVenta.BANK_TRANSFER)) {
            metodos.stream().filter(m -> m.tipo() == tipo).findFirst().ifPresent(resultado::add);
        }
        return List.copyOf(resultado);
    }

    private void configurarGrid() {
        if (grid.getColumns().isEmpty()) {
            grid.addColumn(AplicacionTemporal::nombre).setHeader("Método").setFlexGrow(1);
            grid.addColumn(a -> dinero(a.monto)).setHeader("Monto").setAutoWidth(true);
            grid.addColumn(a -> a.referencia == null || a.referencia.isBlank() ? "—" : a.referencia)
                    .setHeader("Referencia").setFlexGrow(1);
            grid.addComponentColumn(a -> {
                Button boton = new Button(VaadinIcon.TRASH.create(), e -> {
                    aplicaciones.remove(a);
                    refrescarPagos();
                    actualizarResumen();
                });
                boton.addClassName("cc-grid-icon-button");
                boton.setTooltipText("Quitar pago");
                boton.getElement().setAttribute("aria-label", "Quitar pago");
                return boton;
            }).setHeader("Acción").setAutoWidth(true);
        }
        grid.setAllRowsVisible(true);
        grid.addClassName("cc-pos-payment-grid");
        refrescarPagos();
    }

    private void refrescarPagos() {
        paginacionPagos.setTotal(aplicaciones.size());
        paginacionPagos.setVisible(aplicaciones.size() > PosDialogPagination.DEFAULT_PAGE_SIZE);
        cargarPaginaPagos();
    }

    private void cargarPaginaPagos() {
        AppPagination.PageRequest pagina = paginacionPagos.currentRequest();
        int desde = Math.min(pagina.page() * pagina.size(), aplicaciones.size());
        int hasta = Math.min(desde + pagina.size(), aplicaciones.size());
        grid.setItems(aplicaciones.subList(desde, hasta));
    }

    private void seleccionarMultiple(MetodoPagoPosDto metodo) {
        if (metodo == null) return;
        keypad.clearTarget();
        metodoActual = metodo;
        keypad.setOwner(monto, targetMonto(metodo));
        BigDecimal restante = pendiente().max(BigDecimal.ZERO);
        AplicacionTemporal efectivoExistente = metodo.efectivo()
                ? aplicaciones.stream().filter(a -> a.metodo.efectivo()).findFirst().orElse(null)
                : null;
        monto.setValue(PosNumberFormatter.moneyInput(efectivoExistente == null ? restante : efectivoExistente.monto));
        recibido.clear();
        referencia.clear();
        cuenta.clear();
        banco.clear();
        if (metodo.efectivo()) recibido.setValue(PosNumberFormatter.moneyInput(efectivoExistente == null ? restante : efectivoExistente.recibido));
        recibido.setVisible(metodo.efectivo());
        cuenta.setVisible(metodo.requiereCuentaBancaria());
        referencia.setVisible(metodo.requiereReferencia() || metodo.electronico()
                || metodo.tipo() == TipoMetodoPagoVenta.BANK_TRANSFER);
        referencia.setRequiredIndicatorVisible(metodo.requiereReferencia());
        banco.setVisible(metodo.tipo() == TipoMetodoPagoVenta.CHECK);
        actualizarCambio();
        if (controlRecibido != null) controlRecibido.setVisible(metodo.efectivo());
        keypad.refresh(recibido);
        keypad.refresh(monto);
        if (metodo.efectivo()) recibido.focus();
        else monto.focus();
    }

    private void aplicar() {
        if (metodoActual == null) {
            Notification.show("Selecciona un método de pago.");
            return;
        }
        BigDecimal valor = escala(monto.getValue());
        if (valor.signum() <= 0) {
            Notification.show("El monto aplicado debe ser mayor que cero.");
            return;
        }
        AplicacionTemporal efectivoExistente = metodoActual.efectivo()
                ? aplicaciones.stream().filter(a -> a.metodo.efectivo()).findFirst().orElse(null)
                : null;
        BigDecimal permitido = pendiente().add(efectivoExistente == null ? BigDecimal.ZERO : efectivoExistente.monto);
        if (valor.compareTo(permitido) > 0) {
            Notification.show("El monto aplicado no puede superar el pendiente.");
            return;
        }
        BigDecimal recibidoValor = null;
        BigDecimal cambioValor = BigDecimal.ZERO.setScale(4);
        if (metodoActual.efectivo()) {
            recibidoValor = escala(recibido.getValue());
            if (recibidoValor.compareTo(valor) < 0) {
                Notification.show("El efectivo recibido no puede ser menor que el monto aplicado.");
                return;
            }
            cambioValor = CobroVentaCalculo.cambio(valor, recibidoValor);
        }
        if (metodoActual.requiereReferencia() && referencia.getValue().trim().isEmpty()) {
            Notification.show("Indica la referencia del pago.");
            return;
        }
        if (metodoActual.requiereCuentaBancaria() && cuenta.getValue() == null) {
            Notification.show("Selecciona la cuenta destino.");
            return;
        }
        if (efectivoExistente != null) aplicaciones.remove(efectivoExistente);
        aplicaciones.add(new AplicacionTemporal(metodoActual, valor, recibidoValor, cambioValor,
                cuenta.getValue(), referencia.getValue().trim(), banco.getValue().trim()));
        refrescarPagos();
        actualizarResumen();
        seleccionarMultiple(metodoActual);
    }

    private void alternarModo() {
        reiniciarDobleEspacio();
        if (!modoMultiple) {
            mostrarMultiple();
            return;
        }
        if (aplicaciones.isEmpty()) {
            mostrarSimple();
            return;
        }
        ConfirmDialog confirmacion = new ConfirmDialog();
        confirmacion.setHeader("Volver a pago simple");
        confirmacion.setText("Se eliminará la distribución actual de pagos. ¿Desea continuar?");
        confirmacion.setCancelable(true);
        confirmacion.setCancelText("Cancelar");
        confirmacion.setConfirmText("Continuar");
        confirmacion.addConfirmListener(e -> {
            aplicaciones.clear();
            mostrarSimple();
        });
        confirmacion.open();
    }

    private void actualizarFooter() {
        getFooter().removeAll();
        accionesFooter.removeAll();
        accionesFooter.add(confirmar, cancelar);
        accionesFooter.addClassName("cc-pos-payment-action-group");
        getFooter().add(accionesFooter);
        if (modoMultiple) actualizarResumen(); else actualizarSimple();
    }

    private void confirmar() {
        reiniciarDobleEspacio();
        confirmar.setEnabled(false);
        if (modoMultiple) {
            if (pendiente().compareTo(BigDecimal.ZERO) != 0 || aplicaciones.size() < 2) {
                actualizarResumen();
                return;
            }
            onConfirmar.accept(aplicaciones.stream().map(AplicacionTemporal::input).toList());
            return;
        }
        if (!simpleValido()) {
            actualizarSimple();
            return;
        }
        BigDecimal recibidoValor = metodoSimple.efectivo() ? escala(recibidoSimple.getValue()) : null;
        onConfirmar.accept(List.of(new AplicacionCobroInput(metodoSimple.id(), total, recibidoValor,
                cuentaSimple.getValue() == null ? null : cuentaSimple.getValue().id(),
                referenciaSimple.getValue().trim(), bancoSimple.getValue().trim())));
    }

    public void reactivarConfirmacion() {
        if (modoMultiple) actualizarResumen(); else actualizarSimple();
    }

    private void usarMontoExacto() {
        if (!atajoExactoDisponible()) return;
        reiniciarDobleEspacio();
        recibidoSimple.setValue(PosNumberFormatter.moneyInput(total));
        actualizarSimple();
        keypad.clearTarget();
        confirmar.focus();
    }

    private boolean atajoExactoDisponible() {
        return isOpened() && !modoMultiple && metodoSimple != null && metodoSimple.efectivo();
    }

    private void actualizarDisponibilidadAtajoExacto() {
        getElement().setProperty("ccCashExactEnabled", atajoExactoDisponible());
    }

    private void reiniciarDobleEspacio() {
        doubleSpace.reset();
    }

    private void cerrar() {
        reiniciarDobleEspacio();
        keypad.clearTarget();
        close();
    }

    private static Div controlNumerico(BigDecimalField field, Button trigger) {
        Div control = new Div(field, trigger);
        control.addClassName("cc-numeric-field-control");
        return control;
    }

    private static String targetMonto(MetodoPagoPosDto metodo) {
        return switch (metodo.tipo()) {
            case CASH -> "MULTI_CASH_APPLIED";
            case CREDIT_CARD -> "MULTI_CREDIT_AMOUNT";
            case DEBIT_CARD -> "MULTI_DEBIT_AMOUNT";
            case BANK_TRANSFER -> "MULTI_TRANSFER_AMOUNT";
            case CHECK -> "MULTI_CHECK_AMOUNT";
            case OTHER -> "MULTI_OTHER_AMOUNT";
        };
    }

    private void actualizarCambio() {
        BigDecimal valor = metodoActual != null && metodoActual.efectivo()
                ? CobroVentaCalculo.cambioNoNegativo(escala(monto.getValue()), escala(recibido.getValue()))
                : BigDecimal.ZERO;
        cambio.setText("Cambio  " + dinero(valor));
    }

    private void actualizarResumen() {
        BigDecimal aplicado = aplicado();
        BigDecimal restante = total.subtract(aplicado);
        aplicadoValor.setText(dinero(aplicado));
        pendienteValor.setText(dinero(restante.max(BigDecimal.ZERO)));
        boolean completoPago = aplicaciones.size() >= 2 && restante.compareTo(BigDecimal.ZERO) == 0;
        completo.setVisible(completoPago);
        confirmar.setEnabled(completoPago);
    }

    private BigDecimal aplicado() {
        return CobroVentaCalculo.totalAplicado(aplicaciones.stream().map(a -> a.monto).toList());
    }

    private BigDecimal pendiente() {
        return total.subtract(aplicado()).setScale(4, RoundingMode.HALF_UP);
    }

    private Div filaResumen(String texto, Span valor) {
        Div fila = new Div(new Span(texto), valor);
        fila.addClassName("cc-pos-payment-summary-row");
        return fila;
    }

    private String dinero(BigDecimal valor) {
        return formatearDinero(moneda, valor);
    }

    static String formatearDinero(String moneda, BigDecimal valor) {
        return moneda + " " + PosNumberFormatter.money(valor);
    }

    private void normalizarImportesAlPerderFoco() {
        for (BigDecimalField campo : List.of(recibidoSimple, monto, recibido)) {
            campo.addBlurListener(e -> { normalizarImporte(campo); PosNumberFormatter.showMoney(campo); });
            campo.addFocusListener(e -> PosNumberFormatter.showQuantity(campo));
        }
    }

    private static void normalizarImporte(BigDecimalField campo) {
        BigDecimal actual = campo.getValue();
        if (actual == null) return;
        BigDecimal normalizado = PosNumberFormatter.moneyInput(actual);
        if (!actual.equals(normalizado)) campo.setValue(normalizado);
    }

    private static BigDecimal escala(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(4, RoundingMode.HALF_UP);
    }

    private static String etiquetaCorta(MetodoPagoPosDto metodo) {
        return switch (metodo.tipo()) {
            case CREDIT_CARD -> "Crédito";
            case DEBIT_CARD -> "Débito";
            case BANK_TRANSFER -> "Transferencia";
            default -> metodo.nombre();
        };
    }

    private static Component icono(MetodoPagoPosDto metodo) {
        return switch (metodo.tipo()) {
            case CASH -> VaadinIcon.MONEY.create();
            case CREDIT_CARD, DEBIT_CARD -> VaadinIcon.CREDIT_CARD.create();
            case BANK_TRANSFER -> VaadinIcon.INSTITUTION.create();
            case CHECK -> VaadinIcon.CLIPBOARD_CHECK.create();
            default -> VaadinIcon.WALLET.create();
        };
    }

    private record AplicacionTemporal(MetodoPagoPosDto metodo, BigDecimal monto, BigDecimal recibido,
                                      BigDecimal cambio, CuentaBancariaPosDto cuenta,
                                      String referencia, String banco) {
        String nombre() { return metodo.nombre(); }
        AplicacionCobroInput input() {
            return new AplicacionCobroInput(metodo.id(), monto, recibido,
                    cuenta == null ? null : cuenta.id(), referencia, banco);
        }
    }
}
