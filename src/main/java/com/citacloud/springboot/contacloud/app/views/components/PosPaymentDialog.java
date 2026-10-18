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
    private final Div importesRapidos = new Div();
    private final Button exacto = new Button();

    private final List<AplicacionTemporal> aplicaciones = new ArrayList<>();
    private final Grid<AplicacionTemporal> grid = new Grid<>();
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
    private final Button confirmar = new Button("Confirmar cobro", VaadinIcon.CHECK_CIRCLE.create());
    private final Button cancelar = new Button("Cancelar", VaadinIcon.CLOSE.create(), e -> close());
    private final Button alternarModo = new Button();

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
        cancelar.addClassName("cc-pos-payment-cancel");
        confirmar.addClassName("cc-pos-charge-confirm");
        confirmar.addClickListener(e -> confirmar());
        alternarModo.addClickListener(e -> alternarModo());
        alternarModo.addClassName("cc-pos-payment-mode");
        exacto.addClassNames("cc-pos-cash-exact", "cc-pos-cash-option");
        exacto.setTooltipText("Usar monto exacto");
        exacto.addClickListener(e -> recibidoSimple.setValue(this.total));
        Shortcuts.addShortcutListener(this, this::close, Key.ESCAPE);
        addOpenedChangeListener(e -> {
            if (e.isOpened()) mostrarSimple();
        });
    }

    private void mostrarSimple() {
        modoMultiple = false;
        contenido.removeAll();
        contenido.add(totalBox(), titulo("MÉTODO DE PAGO"), accesosMetodos(this::seleccionarSimple), construirSimple());
        alternarModo.setText("Pago múltiple");
        alternarModo.setIcon(VaadinIcon.WALLET.create());
        actualizarFooter();
        MetodoPagoPosDto efectivo = metodos.stream().filter(MetodoPagoPosDto::efectivo).findFirst()
                .orElseGet(() -> metodos.stream().filter(MetodoPagoPosDto::predeterminado).findFirst()
                        .orElse(metodos.isEmpty() ? null : metodos.getFirst()));
        seleccionarSimple(efectivo);
    }

    private Component construirSimple() {
        cuentaSimple.setItems(cuentas);
        cuentaSimple.setItemLabelGenerator(CuentaBancariaPosDto::nombre);
        recibidoSimple.setPrefixComponent(new Span(moneda));
        recibidoSimple.setWidthFull();
        referenciaSimple.setWidthFull();
        cuentaSimple.setWidthFull();
        bancoSimple.setWidthFull();
        if (!simpleConfigurado) {
            recibidoSimple.addValueChangeListener(e -> actualizarSimple());
            referenciaSimple.addValueChangeListener(e -> actualizarSimple());
            cuentaSimple.addValueChangeListener(e -> actualizarSimple());
            recibidoSimple.addKeyPressListener(Key.ENTER, e -> exacto.focus());
            simpleConfigurado = true;
        }
        cambioSimple.addClassName("cc-pos-payment-change");
        importesRapidos.removeAll();
        importesRapidos.addClassName("cc-pos-cash-quick");
        exacto.setText(String.format(Locale.US, "%,.2f Exacto", total));
        importesRapidos.add(exacto);
        for (String valor : List.of("20", "50", "100", "200", "500", "1000")) {
            BigDecimal importe = new BigDecimal(valor);
            Button rapido = new Button(String.format(Locale.US, "%,.0f", importe),
                    e -> recibidoSimple.setValue(importe));
            rapido.addClassName("cc-pos-cash-option");
            rapido.setTooltipText("Recibido " + dinero(importe));
            importesRapidos.add(rapido);
        }
        VerticalLayout panel = new VerticalLayout(recibidoSimple, importesRapidos, referenciaSimple,
                cuentaSimple, bancoSimple, cambioSimple);
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.addClassName("cc-pos-simple-payment");
        return panel;
    }

    private void seleccionarSimple(MetodoPagoPosDto metodo) {
        if (metodo == null) return;
        metodoSimple = metodo;
        recibidoSimple.clear();
        referenciaSimple.clear();
        cuentaSimple.clear();
        bancoSimple.clear();
        recibidoSimple.setVisible(metodo.efectivo());
        cambioSimple.setVisible(metodo.efectivo());
        importesRapidos.setVisible(metodo.efectivo());
        referenciaSimple.setVisible(metodo.requiereReferencia() || metodo.electronico()
                || metodo.tipo() == TipoMetodoPagoVenta.BANK_TRANSFER);
        referenciaSimple.setRequiredIndicatorVisible(metodo.requiereReferencia());
        cuentaSimple.setVisible(metodo.requiereCuentaBancaria());
        bancoSimple.setVisible(metodo.tipo() == TipoMetodoPagoVenta.CHECK);
        actualizarSimple();
        if (metodo.efectivo()) recibidoSimple.focus();
        else if (metodo.requiereCuentaBancaria()) cuentaSimple.focus();
        else referenciaSimple.focus();
    }

    private void actualizarSimple() {
        BigDecimal cambioValor = metodoSimple != null && metodoSimple.efectivo()
                ? CobroVentaCalculo.cambio(total, escala(recibidoSimple.getValue())).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        cambioSimple.setText("Cambio  " + dinero(cambioValor));
        confirmar.setEnabled(simpleValido());
    }

    private boolean simpleValido() {
        if (metodoSimple == null) return false;
        if (metodoSimple.efectivo() && escala(recibidoSimple.getValue()).compareTo(total) < 0) return false;
        if (metodoSimple.requiereReferencia() && referenciaSimple.getValue().trim().isEmpty()) return false;
        return !metodoSimple.requiereCuentaBancaria() || cuentaSimple.getValue() != null;
    }

    private void mostrarMultiple() {
        modoMultiple = true;
        contenido.removeAll();
        contenido.add(totalBox(), titulo("DISTRIBUIR PAGO"), accesosMetodos(this::seleccionarMultiple),
                construirEditorMultiple(), titulo("PAGOS APLICADOS"), grid, construirResumenMultiple());
        alternarModo.setText("Volver a pago simple");
        alternarModo.setIcon(VaadinIcon.ARROW_BACKWARD.create());
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
        HorizontalLayout campos = new HorizontalLayout(monto, recibido, referencia, cuenta, banco);
        campos.setWidthFull();
        campos.setAlignItems(FlexComponent.Alignment.END);
        campos.addClassName("cc-pos-payment-fields");
        Div editor = new Div(campos, new Div(cambio, aplicar));
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

    private Component totalBox() {
        Span totalImporte = new Span(dinero(total));
        totalImporte.addClassName("cc-pos-payment-total-value");
        Div totalBox = new Div(new Span("TOTAL A COBRAR"), totalImporte);
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
        metodos.stream().limit(4).forEach(m -> {
            Button boton = new Button(etiquetaCorta(m), icono(m), e -> seleccionar.accept(m));
            boton.addClassName("cc-pos-payment-method");
            boton.setTooltipText(m.nombre());
            accesos.add(boton);
        });
        List<MetodoPagoPosDto> otros = metodos.stream().skip(4).toList();
        if (!otros.isEmpty()) {
            ComboBox<MetodoPagoPosDto> mas = new ComboBox<>();
            mas.setPlaceholder("Más métodos");
            mas.setItems(otros);
            mas.setItemLabelGenerator(MetodoPagoPosDto::nombre);
            mas.addValueChangeListener(e -> {
                if (e.getValue() != null) {
                    seleccionar.accept(e.getValue());
                    mas.clear();
                }
            });
            mas.addClassName("cc-pos-more-methods");
            accesos.add(mas);
        }
        return accesos;
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
                    grid.setItems(aplicaciones);
                    actualizarResumen();
                });
                boton.addClassName("cc-grid-icon-button");
                boton.setTooltipText("Quitar pago");
                boton.getElement().setAttribute("aria-label", "Quitar pago");
                return boton;
            }).setHeader("Acción").setAutoWidth(true);
        }
        grid.setItems(aplicaciones);
        grid.setAllRowsVisible(false);
        grid.addClassName("cc-pos-payment-grid");
    }

    private void seleccionarMultiple(MetodoPagoPosDto metodo) {
        if (metodo == null) return;
        metodoActual = metodo;
        BigDecimal restante = pendiente().max(BigDecimal.ZERO);
        AplicacionTemporal efectivoExistente = metodo.efectivo()
                ? aplicaciones.stream().filter(a -> a.metodo.efectivo()).findFirst().orElse(null)
                : null;
        monto.setValue(efectivoExistente == null ? restante : efectivoExistente.monto);
        recibido.clear();
        referencia.clear();
        cuenta.clear();
        banco.clear();
        if (metodo.efectivo()) recibido.setValue(efectivoExistente == null ? restante : efectivoExistente.recibido);
        recibido.setVisible(metodo.efectivo());
        cuenta.setVisible(metodo.requiereCuentaBancaria());
        referencia.setVisible(metodo.requiereReferencia() || metodo.electronico()
                || metodo.tipo() == TipoMetodoPagoVenta.BANK_TRANSFER);
        referencia.setRequiredIndicatorVisible(metodo.requiereReferencia());
        banco.setVisible(metodo.tipo() == TipoMetodoPagoVenta.CHECK);
        actualizarCambio();
        if (metodo.efectivo()) recibido.focus(); else monto.focus();
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
        grid.setItems(aplicaciones);
        actualizarResumen();
        seleccionarMultiple(metodoActual);
    }

    private void alternarModo() {
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
        getFooter().add(alternarModo, cancelar, confirmar);
        if (modoMultiple) actualizarResumen(); else actualizarSimple();
    }

    private void confirmar() {
        if (modoMultiple) {
            if (pendiente().compareTo(BigDecimal.ZERO) != 0 || aplicaciones.size() < 2) return;
            onConfirmar.accept(aplicaciones.stream().map(AplicacionTemporal::input).toList());
            return;
        }
        if (!simpleValido()) return;
        BigDecimal recibidoValor = metodoSimple.efectivo() ? escala(recibidoSimple.getValue()) : null;
        onConfirmar.accept(List.of(new AplicacionCobroInput(metodoSimple.id(), total, recibidoValor,
                cuentaSimple.getValue() == null ? null : cuentaSimple.getValue().id(),
                referenciaSimple.getValue().trim(), bancoSimple.getValue().trim())));
    }

    private void actualizarCambio() {
        BigDecimal valor = metodoActual != null && metodoActual.efectivo()
                ? CobroVentaCalculo.cambio(escala(monto.getValue()), escala(recibido.getValue())).max(BigDecimal.ZERO)
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
        return moneda + " " + String.format(Locale.US, "%,.2f", valor == null ? BigDecimal.ZERO : valor);
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
