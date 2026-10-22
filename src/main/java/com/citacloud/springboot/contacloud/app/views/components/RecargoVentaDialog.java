package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.services.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.*;
import java.math.*;
import java.util.*;
import java.util.function.Consumer;

public class RecargoVentaDialog extends Dialog {
    private final RecargoVentaService service;
    private final UUID operacion;
    private final List<LineaVistaPreviaRecargoInput> lineas;
    private final Consumer<Aplicado> aplicar;
    private final Runnable quitar;
    private final Runnable cerrar;
    private final OpcionesRecargoPosDto opciones;
    private final Set<UUID> seleccionadas = new LinkedHashSet<>();
    private final RadioButtonGroup<AlcanceRecargoVenta> alcance = new RadioButtonGroup<>();
    private final RadioButtonGroup<TipoRecargoVenta> tipo = new RadioButtonGroup<>();
    private final ComboBox<ConfiguracionRecargoDto.Tipo> concepto = new ComboBox<>();
    private final BigDecimalField valor = new BigDecimalField();
    private final ComboBox<ConfiguracionRecargoDto.Motivo> motivo = new ComboBox<>();
    private final TextArea detalle = new TextArea("Detalle");
    private final VerticalLayout selector = new VerticalLayout();
    private final Grid<LineaVistaPreviaRecargoInput> grid = new Grid<>();
    private final Checkbox todos = new Checkbox();
    private final Span contador = new Span();
    private final Span mensaje = new Span();
    private final Span base = new Span("—");
    private final Span recargo = new Span("—");
    private final Span posterior = new Span("—");
    private final Span cantidadSeleccionada = new Span("0");
    private final Div filaCantidad = metrica("SELECCIONADOS", cantidadSeleccionada);
    private final Div preview;
    private final AppActionButton confirmar;

    public RecargoVentaDialog(RecargoVentaService service, UUID operacion,
            List<LineaVistaPreviaRecargoInput> lineas, RecargoVentaInput actual,
            Consumer<Aplicado> aplicar, Runnable quitar, Runnable cerrar) {
        this.service = service;
        this.operacion = operacion;
        this.lineas = List.copyOf(lineas);
        this.aplicar = aplicar;
        this.quitar = quitar;
        this.cerrar = cerrar;
        this.opciones = service.opciones();
        if (actual != null) seleccionadas.addAll(actual.lineaIds());

        setHeaderTitle(actual == null ? "APLICAR RECARGO" : "RECARGO");
        setWidth("min(980px,96vw)");
        setMaxHeight("94vh");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);
        addClassNames("cc-sales-discount-dialog", "cc-sales-adjustment-dialog");

        alcance.setLabel("Aplicar a");
        alcance.setItems(alcances());
        alcance.setItemLabelGenerator(x -> x == AlcanceRecargoVenta.SALE ? "Venta completa" : "Productos");
        alcance.setValue(actual == null ? alcances().getFirst() : actual.alcance());
        tipo.setLabel("Tipo de recargo");
        tipo.setItems(tipos());
        tipo.setItemLabelGenerator(x -> x == TipoRecargoVenta.PERCENTAGE ? "Porcentaje %" : "Monto");
        tipo.setValue(actual == null ? tipos().getFirst() : actual.tipo());

        concepto.setItems(opciones.tipos());
        concepto.setItemLabelGenerator(ConfiguracionRecargoDto.Tipo::nombre);
        concepto.setAriaLabel("Concepto");
        concepto.setWidthFull();
        if (actual != null) concepto.setValue(opciones.tipos().stream()
                .filter(x -> x.id().equals(actual.conceptoId())).findFirst().orElse(null));
        else if (!opciones.tipos().isEmpty()) concepto.setValue(opciones.tipos().getFirst());

        if (actual != null) valor.setValue(actual.valor());
        valor.setAriaLabel("Valor");
        valor.setWidthFull();
        motivo.setItems(opciones.motivos());
        motivo.setItemLabelGenerator(ConfiguracionRecargoDto.Motivo::nombre);
        motivo.setClearButtonVisible(!opciones.requiereMotivo());
        motivo.setRequiredIndicatorVisible(false);
        motivo.setAriaLabel("Motivo");
        motivo.setWidthFull();
        if (actual != null && actual.motivoId() != null) motivo.setValue(opciones.motivos().stream()
                .filter(x -> x.id().equals(actual.motivoId())).findFirst().orElse(null));

        ConfiguracionRecargoDto.Tipo conceptoInicial = concepto.getValue();
        if (conceptoInicial != null) {
            if (actual == null) {
                tipo.setValue(conceptoInicial.tipoPredeterminado());
                if (conceptoInicial.valorPredeterminado() == null) valor.clear();
                else valor.setValue(conceptoInicial.valorPredeterminado());
            }
            valor.setReadOnly(!conceptoInicial.valorEditable());
        }

        detalle.setMaxLength(500);
        detalle.setWidthFull();
        detalle.setVisible(motivo.getValue() != null && motivo.getValue().otro());
        if (actual != null && actual.detalle() != null) detalle.setValue(actual.detalle());

        configurarSelector();
        preview = new Div(filaCantidad, metrica("BASE APLICABLE", base),
                metrica("RECARGO", recargo), metrica("IMPORTE FINAL", posterior));
        preview.addClassNames("cc-discount-preview", "cc-adjustment-summary");
        Div campos = new Div(
                grupoCampo("Concepto", true, concepto),
                grupoCampo("Valor", false, valor),
                grupoCampo("Motivo", opciones.requiereMotivo(), motivo));
        campos.addClassNames("cc-adjustment-fields", "cc-surcharge-inline-fields");

        HorizontalLayout opcionesLayout = new HorizontalLayout(alcance, tipo);
        opcionesLayout.setWidthFull();
        opcionesLayout.setFlexGrow(1, alcance, tipo);
        add(opcionesLayout, selector, campos, detalle, preview);

        AppActionButton eliminar = new AppActionButton(ActionType.DELETE, ButtonSize.MAIN,
                "Quitar recargo", e -> { quitar.run(); close(); });
        eliminar.setVisible(actual != null);
        eliminar.getStyle().set("margin-right", "auto");
        confirmar = new AppActionButton(ActionType.ACCEPT, ButtonSize.MAIN,
                "Aplicar recargo", e -> confirmar());
        AppActionButton cancelar = new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN,
                "Cancelar", e -> close());
        getFooter().add(eliminar, confirmar, cancelar);

        actualizarFormatoValor();
        registrar();
        actualizarSelector();
        addOpenedChangeListener(e -> { if (e.isOpened()) valor.focus(); });
        addClosedListener(e -> cerrar.run());
    }

    private void configurarSelector() {
        selector.setPadding(false);
        selector.setSpacing(false);
        selector.addClassName("cc-discount-products");
        todos.setAriaLabel("Seleccionar todos los productos elegibles");
        todos.addValueChangeListener(e -> {
            if (!e.isFromClient()) return;
            seleccionadas.clear();
            if (Boolean.TRUE.equals(e.getValue())) lineas.stream()
                    .filter(LineaVistaPreviaRecargoInput::permiteRecargos)
                    .map(LineaVistaPreviaRecargoInput::lineaId).forEach(seleccionadas::add);
            refrescarSeleccion();
        });
        Grid.Column<LineaVistaPreviaRecargoInput> seleccion = grid.addComponentColumn(linea -> {
            Checkbox check = new Checkbox(seleccionadas.contains(linea.lineaId()));
            check.setEnabled(linea.permiteRecargos());
            check.setAriaLabel("Seleccionar " + nombre(linea));
            if (!linea.permiteRecargos()) check.setTooltipText("Este producto no admite recargos.");
            check.addValueChangeListener(e -> {
                if (!e.isFromClient()) return;
                if (Boolean.TRUE.equals(e.getValue())) seleccionadas.add(linea.lineaId());
                else seleccionadas.remove(linea.lineaId());
                refrescarSeleccion();
            });
            return check;
        }).setKey("seleccion").setWidth("calc(1lh + var(--vaadin-padding-inline-container) + var(--vaadin-padding-inline-container) + 4px)").setFlexGrow(0);
        seleccion.setHeader(todos);
        grid.addComponentColumn(linea -> {
            Span nombre = new Span(nombre(linea));
            nombre.addClassName("cc-discount-product-name");
            nombre.getElement().setAttribute("title", nombre(linea));
            if (linea.permiteRecargos()) return nombre;
            Icon candado = VaadinIcon.LOCK.create();
            candado.setSize("14px");
            HorizontalLayout bloqueado = new HorizontalLayout(nombre, candado);
            bloqueado.setPadding(false);
            bloqueado.setSpacing(true);
            bloqueado.setAlignItems(FlexComponent.Alignment.CENTER);
            bloqueado.addClassName("cc-discount-product-disabled");
            bloqueado.getElement().setAttribute("title", "Este producto no admite recargos.");
            return bloqueado;
        }).setKey("producto").setHeader("Producto").setWidth("auto").setFlexGrow(1);
        grid.addColumn(linea -> cantidad(linea.cantidad())).setHeader("Cant.")
                .setKey("cantidad").setTextAlign(ColumnTextAlign.END).setWidth("90px").setFlexGrow(0);
        grid.addColumn(linea -> moneda(importe(linea))).setHeader("Importe")
                .setKey("importe").setTextAlign(ColumnTextAlign.END).setWidth("150px").setFlexGrow(0);
        grid.setItems(lineas);
        grid.addClassNames("cc-discount-products-grid", "cc-adjustment-products-grid");
        if (lineas.size() <= 6) grid.setAllRowsVisible(true);
        else grid.setHeight("300px");
        contador.addClassName("cc-discount-selected-count");
        mensaje.addClassName("cc-discount-selection-message");
        selector.add(new H4("SELECCIONAR PRODUCTOS"), grid, contador, mensaje);
    }

    private void registrar() {
        alcance.addValueChangeListener(e -> { actualizarSelector(); actualizarVista(); });
        tipo.addValueChangeListener(e -> { actualizarFormatoValor(); actualizarVista(); });
        valor.addValueChangeListener(e -> actualizarVista());
        motivo.addValueChangeListener(e -> {
            detalle.setVisible(e.getValue() != null && e.getValue().otro());
            actualizarVista();
        });
        detalle.addValueChangeListener(e -> actualizarVista());
        concepto.addValueChangeListener(e -> {
            ConfiguracionRecargoDto.Tipo seleccionado = e.getValue();
            if (seleccionado != null) {
                tipo.setValue(seleccionado.tipoPredeterminado());
                if (seleccionado.valorPredeterminado() == null) valor.clear();
                else valor.setValue(seleccionado.valorPredeterminado());
                valor.setReadOnly(!seleccionado.valorEditable());
            }
            actualizarVista();
        });
    }

    private void actualizarSelector() {
        boolean productos = alcance.getValue() == AlcanceRecargoVenta.LINE;
        selector.setVisible(productos);
        filaCantidad.setVisible(productos);
        preview.setClassName("cc-summary-sale", !productos);
        refrescarSeleccion();
    }

    private void refrescarSeleccion() {
        Set<UUID> actuales = lineas.stream().map(LineaVistaPreviaRecargoInput::lineaId)
                .collect(java.util.stream.Collectors.toSet());
        seleccionadas.retainAll(actuales);
        long elegibles = lineas.stream().filter(LineaVistaPreviaRecargoInput::permiteRecargos).count();
        int cantidad = seleccionadas.size();
        todos.setValue(elegibles > 0 && cantidad == elegibles);
        todos.setIndeterminate(cantidad > 0 && cantidad < elegibles);
        todos.setEnabled(elegibles > 0);
        contador.setText(cantidad + (cantidad == 1 ? " producto seleccionado" : " productos seleccionados"));
        cantidadSeleccionada.setText(Integer.toString(cantidad));
        boolean sinElegibles = elegibles == 0;
        mensaje.setText(sinElegibles ? "No existen productos elegibles para aplicar recargos."
                : cantidad == 0 ? "Seleccione al menos un producto." : "");
        mensaje.setVisible(sinElegibles || cantidad == 0);
        grid.getDataProvider().refreshAll();
        actualizarVista();
    }

    private void actualizarVista() {
        try {
            boolean faltaMotivo = opciones.requiereMotivo() && motivo.getValue() == null;
            boolean faltaDetalle = motivo.getValue() != null && motivo.getValue().otro() && detalle.getValue().isBlank();
            if (valor.getValue() == null || valor.getValue().signum() <= 0 || concepto.getValue() == null
                    || faltaMotivo || faltaDetalle
                    || alcance.getValue() == AlcanceRecargoVenta.LINE && seleccionadas.isEmpty()) {
                limpiar();
                return;
            }
            VistaPreviaRecargoDto vista = service.previsualizar(solicitud());
            base.setText(moneda(vista.baseElegible()));
            recargo.setText("+" + moneda(vista.montoRecargo()));
            posterior.setText(moneda(vista.importePosterior()));
            confirmar.setEnabled(true);
            confirmar.setTooltipText(vista.requiereAutorizacion()
                    ? "Solicitar autorización de recargo" : "Aplicar recargo");
        } catch (RuntimeException ex) {
            limpiar();
        }
    }

    private void confirmar() {
        try {
            SolicitudVistaPreviaRecargoInput solicitud = solicitud();
            VistaPreviaRecargoDto vista = service.previsualizar(solicitud);
            if (vista.requiereAutorizacion()) {
                dialogoAutorizacion(solicitud, vista);
                return;
            }
            aplicar.accept(new Aplicado(solicitud.recargo(), vista));
            close();
        } catch (RuntimeException ex) {
            com.citacloud.springboot.contacloud.app.errors.UiErrorHandler.show(ex, "No fue posible aplicar el recargo.");
        }
    }

    private void dialogoAutorizacion(SolicitudVistaPreviaRecargoInput solicitud, VistaPreviaRecargoDto vista) {
        Dialog dialogo = new Dialog();
        dialogo.setHeaderTitle("AUTORIZAR RECARGO");
        dialogo.setWidth("min(560px,94vw)");
        dialogo.setCloseOnOutsideClick(false);
        TextField usuario = new TextField("Usuario autorizador");
        usuario.setWidthFull();
        PasswordField clave = new PasswordField("Contraseña / PIN");
        clave.setWidthFull();
        Div resumen = new Div(
                fila("Recargo solicitado", new Span(moneda(vista.montoRecargo()))),
                fila("Máximo porcentaje", new Span(vista.porcentajeMaximoUsuario() == null
                        ? "Sin límite" : vista.porcentajeMaximoUsuario() + "%")));
        resumen.addClassName("cc-discount-preview");
        dialogo.add(resumen, usuario, clave);
        AppActionButton autorizar = new AppActionButton(ActionType.ACCEPT, ButtonSize.MAIN,
                "Autorizar recargo", e -> {
                    try {
                        AutorizacionRecargoDto autorizada = service.autorizar(
                                new AutorizarRecargoInput(solicitud, usuario.getValue(), clave.getValue()));
                        RecargoVentaInput actual = solicitud.recargo();
                        aplicar.accept(new Aplicado(new RecargoVentaInput(actual.alcance(), actual.tipo(),
                                actual.valor(), actual.lineaIds(), actual.conceptoId(), actual.motivoId(),
                                actual.detalle(), autorizada.id()), vista));
                        dialogo.close();
                        close();
                        Notification.show("Recargo autorizado por " + autorizada.autorizadaPor() + ".");
                    } catch (RuntimeException ex) {
                        com.citacloud.springboot.contacloud.app.errors.UiErrorHandler.show(ex, "No fue posible autorizar el recargo.");
                    }
                });
        dialogo.getFooter().add(autorizar, new AppActionButton(ActionType.CANCEL,
                ButtonSize.MAIN, "Cancelar", e -> dialogo.close()));
        dialogo.open();
    }

    private SolicitudVistaPreviaRecargoInput solicitud() {
        ConfiguracionRecargoDto.Motivo motivoSeleccionado = motivo.getValue();
        ConfiguracionRecargoDto.Tipo conceptoSeleccionado = concepto.getValue();
        List<UUID> ids = alcance.getValue() == AlcanceRecargoVenta.LINE
                ? List.copyOf(seleccionadas) : List.of();
        return new SolicitudVistaPreviaRecargoInput(operacion,
                new RecargoVentaInput(alcance.getValue(), tipo.getValue(), valor.getValue(), ids,
                        conceptoSeleccionado == null ? null : conceptoSeleccionado.id(),
                        motivoSeleccionado == null ? null : motivoSeleccionado.id(),
                        detalle.isVisible() ? detalle.getValue() : null, null), lineas);
    }

    private List<AlcanceRecargoVenta> alcances() {
        List<AlcanceRecargoVenta> disponibles = new ArrayList<>();
        if (opciones.permiteVenta()) disponibles.add(AlcanceRecargoVenta.SALE);
        if (opciones.permiteLinea()) disponibles.add(AlcanceRecargoVenta.LINE);
        if (disponibles.isEmpty()) throw new ReglaNegocioException("No hay alcances de recargo habilitados.");
        return disponibles;
    }

    private List<TipoRecargoVenta> tipos() {
        List<TipoRecargoVenta> disponibles = new ArrayList<>();
        if (opciones.permitePorcentaje()) disponibles.add(TipoRecargoVenta.PERCENTAGE);
        if (opciones.permiteMonto()) disponibles.add(TipoRecargoVenta.AMOUNT);
        if (disponibles.isEmpty()) throw new ReglaNegocioException("No hay tipos de recargo habilitados.");
        return disponibles;
    }

    private void actualizarFormatoValor() {
        boolean porcentaje = tipo.getValue() == TipoRecargoVenta.PERCENTAGE;
        valor.setSuffixComponent(porcentaje ? new Span("%") : null);
        valor.setPrefixComponent(porcentaje ? null : new Span(opciones.moneda()));
    }

    private void limpiar() {
        base.setText("—");
        recargo.setText("—");
        posterior.setText("—");
        if (confirmar != null) confirmar.setEnabled(false);
    }

    private BigDecimal importe(LineaVistaPreviaRecargoInput linea) {
        return linea.precioEsperado().multiply(linea.cantidad());
    }

    private String moneda(BigDecimal valor) {
        return opciones.moneda() + " " + String.format(Locale.US, "%,.2f",
                valor == null ? BigDecimal.ZERO : valor);
    }

    private static String cantidad(BigDecimal valor) {
        return valor == null ? "0" : valor.stripTrailingZeros().toPlainString();
    }

    private static String nombre(LineaVistaPreviaRecargoInput linea) {
        return linea.productoNombre() == null || linea.productoNombre().isBlank()
                ? "Producto" : linea.productoNombre();
    }

    private static Div grupoCampo(String etiqueta, boolean requerido, Component control) {
        Span label = new Span(etiqueta + (requerido ? " *" : ""));
        label.addClassName("cc-adjustment-inline-label");
        Div grupo = new Div(label, control);
        grupo.addClassName("cc-adjustment-field-group");
        return grupo;
    }

    private static Div metrica(String etiqueta, Span valor) {
        Span label = new Span(etiqueta);
        label.addClassName("cc-adjustment-summary-label");
        Div metrica = new Div(label, valor);
        metrica.addClassName("cc-adjustment-summary-metric");
        return metrica;
    }

    private static Div fila(String etiqueta, Span valor) {
        Div fila = new Div(new Span(etiqueta), valor);
        fila.addClassName("cc-discount-preview-row");
        return fila;
    }

    public record Aplicado(RecargoVentaInput recargo, VistaPreviaRecargoDto vista) {}
}
