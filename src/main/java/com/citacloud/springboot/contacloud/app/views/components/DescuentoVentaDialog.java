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

public class DescuentoVentaDialog extends Dialog {
    private final DescuentoVentaService service;
    private final UUID operacionId;
    private final List<LineaVistaPreviaDescuentoInput> lineas;
    private final Consumer<Aplicado> aplicar;
    private final Runnable quitar;
    private final Runnable cerrar;
    private final OpcionesDescuentoPosDto opciones;
    private final Set<UUID> seleccionadas = new LinkedHashSet<>();
    private final RadioButtonGroup<AlcanceDescuentoVenta> alcance = new RadioButtonGroup<>();
    private final RadioButtonGroup<TipoDescuentoVenta> tipo = new RadioButtonGroup<>();
    private final BigDecimalField valor = new BigDecimalField();
    private final ComboBox<ConfiguracionDescuentoDto.Motivo> motivo = new ComboBox<>();
    private final TextArea detalle = new TextArea("Detalle");
    private final VerticalLayout selector = new VerticalLayout();
    private final Grid<LineaVistaPreviaDescuentoInput> grid = new Grid<>();
    private final Checkbox seleccionarTodos = new Checkbox();
    private final Span contador = new Span();
    private final Span mensajeSeleccion = new Span();
    private final Span base = new Span("—"), descuento = new Span("—"), posterior = new Span("—"), cantidadSeleccionada = new Span("0");
    private final Div filaCantidad = metrica("SELECCIONADOS", cantidadSeleccionada);
    private final Div preview;
    private final AppActionButton confirmar;
    private VistaPreviaDescuentoDto ultimaVista;

    public DescuentoVentaDialog(DescuentoVentaService service, UUID operacionId,
            List<LineaVistaPreviaDescuentoInput> lineas, UUID seleccionAnteriorIgnorada,
            DescuentoVentaInput actual, Consumer<Aplicado> aplicar, Runnable quitar, Runnable cerrar) {
        this.service = service; this.operacionId = operacionId; this.lineas = List.copyOf(lineas);
        this.aplicar = aplicar; this.quitar = quitar; this.cerrar = cerrar; this.opciones = service.opciones();
        if (actual != null) seleccionadas.addAll(actual.lineaIds());
        setHeaderTitle(actual == null ? "APLICAR DESCUENTO" : "DESCUENTO"); setWidth("min(980px,96vw)");
        setMaxHeight("94vh"); setCloseOnEsc(true); setCloseOnOutsideClick(false); addClassNames("cc-sales-discount-dialog", "cc-sales-adjustment-dialog");
        alcance.setLabel("Aplicar a"); alcance.setItems(disponiblesAlcance());
        alcance.setItemLabelGenerator(x -> x == AlcanceDescuentoVenta.SALE ? "Venta completa" : "Productos");
        alcance.setValue(actual == null ? AlcanceDescuentoVenta.SALE : actual.alcance());
        tipo.setLabel("Tipo de descuento"); tipo.setItems(disponiblesTipo());
        tipo.setItemLabelGenerator(x -> x == TipoDescuentoVenta.PERCENTAGE ? "Porcentaje %" : "Monto");
        tipo.setValue(actual == null ? disponiblesTipo().getFirst() : actual.tipo());
        valor.setWidthFull(); valor.setAriaLabel("Valor"); if (actual != null) valor.setValue(actual.valor());
        motivo.setItems(opciones.motivos()); motivo.setItemLabelGenerator(ConfiguracionDescuentoDto.Motivo::nombre);
        motivo.setClearButtonVisible(!opciones.requiereMotivo()); motivo.setRequiredIndicatorVisible(false); motivo.setAriaLabel("Motivo"); motivo.setWidthFull();
        if (actual != null && actual.motivoId() != null) motivo.setValue(opciones.motivos().stream().filter(x -> x.id().equals(actual.motivoId())).findFirst().orElse(null));
        detalle.setMaxLength(500); detalle.setWidthFull(); detalle.setVisible(motivo.getValue() != null && motivo.getValue().otro());
        if (actual != null && actual.detalle() != null) detalle.setValue(actual.detalle());
        configurarSelector();
        preview = new Div(filaCantidad, metrica("BASE APLICABLE", base), metrica("DESCUENTO", descuento), metrica("IMPORTE FINAL", posterior));
        preview.addClassNames("cc-discount-preview", "cc-adjustment-summary");
        Div campos = new Div(grupoCampo("Valor", false, valor), grupoCampo("Motivo", opciones.requiereMotivo(), motivo));
        campos.addClassNames("cc-adjustment-fields", "cc-discount-inline-fields");
        HorizontalLayout opcionesLayout = new HorizontalLayout(alcance, tipo); opcionesLayout.setWidthFull(); opcionesLayout.setFlexGrow(1, alcance, tipo);
        add(opcionesLayout, selector, campos, detalle, preview);
        AppActionButton eliminar = new AppActionButton(ActionType.DELETE, ButtonSize.MAIN, "Quitar descuento", e -> { quitar.run(); close(); }); eliminar.setVisible(actual != null);
        confirmar = new AppActionButton(ActionType.ACCEPT, ButtonSize.MAIN, actual == null ? "Aplicar descuento" : "Aplicar cambios", e -> confirmar());
        AppActionButton cancelar = new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN, "Cancelar", e -> close());
        eliminar.getStyle().set("margin-right", "auto"); getFooter().add(eliminar, confirmar, cancelar);
        actualizarFormatoValor(); registrarCambios(); actualizarSelector();
        addOpenedChangeListener(e -> { if (e.isOpened()) { valor.focus(); actualizarVista(); } });
        addClosedListener(e -> cerrar.run());
    }

    private void configurarSelector() {
        selector.setPadding(false); selector.setSpacing(false); selector.addClassName("cc-discount-products");
        H4 titulo = new H4("SELECCIONAR PRODUCTOS");
        seleccionarTodos.setAriaLabel("Seleccionar todos los productos elegibles");
        seleccionarTodos.addValueChangeListener(e -> { if (e.isFromClient()) { seleccionadas.clear(); if (Boolean.TRUE.equals(e.getValue())) lineas.stream().filter(LineaVistaPreviaDescuentoInput::permiteDescuentos).map(LineaVistaPreviaDescuentoInput::lineaId).forEach(seleccionadas::add); refrescarSeleccion(); } });
        Grid.Column<LineaVistaPreviaDescuentoInput> seleccion = grid.addComponentColumn(linea -> {
            Checkbox check = new Checkbox(seleccionadas.contains(linea.lineaId())); check.setEnabled(linea.permiteDescuentos()); check.setAriaLabel("Seleccionar " + nombre(linea));
            if (!linea.permiteDescuentos()) check.setTooltipText("Este producto no admite descuentos.");
            check.addValueChangeListener(e -> { if (!e.isFromClient()) return; if (Boolean.TRUE.equals(e.getValue())) seleccionadas.add(linea.lineaId()); else seleccionadas.remove(linea.lineaId()); refrescarSeleccion(); });
            return check;
        }).setKey("seleccion").setWidth("calc(1lh + var(--vaadin-padding-inline-container) + var(--vaadin-padding-inline-container) + 4px)").setFlexGrow(0); seleccion.setHeader(seleccionarTodos);
        grid.addComponentColumn(linea -> {
            Span texto = new Span(nombre(linea)); texto.addClassName("cc-discount-product-name"); texto.getElement().setAttribute("title", nombre(linea));
            if (linea.permiteDescuentos()) return texto;
            Icon candado = VaadinIcon.LOCK.create(); candado.setSize("14px");
            HorizontalLayout bloqueado = new HorizontalLayout(texto, candado); bloqueado.setPadding(false); bloqueado.setSpacing(true); bloqueado.setAlignItems(FlexComponent.Alignment.CENTER);
            bloqueado.getElement().setAttribute("title", "Este producto no admite descuentos."); bloqueado.addClassName("cc-discount-product-disabled"); return bloqueado;
        }).setKey("producto").setHeader("Producto").setWidth("auto").setFlexGrow(1);
        grid.addColumn(x -> cantidad(x.cantidad())).setKey("cantidad").setHeader("Cant.").setTextAlign(ColumnTextAlign.END).setWidth("90px").setFlexGrow(0);
        grid.addColumn(x -> moneda(importe(x))).setKey("importe").setHeader("Importe").setTextAlign(ColumnTextAlign.END).setWidth("150px").setFlexGrow(0);
        grid.setItems(lineas); grid.addClassNames("cc-discount-products-grid", "cc-adjustment-products-grid");
        if (lineas.size() <= 6) grid.setAllRowsVisible(true); else grid.setHeight("300px");
        contador.addClassName("cc-discount-selected-count"); mensajeSeleccion.addClassName("cc-discount-selection-message");
        selector.add(titulo, grid, contador, mensajeSeleccion);
    }

    private void registrarCambios() {
        alcance.addValueChangeListener(e -> { actualizarSelector(); actualizarVista(); }); tipo.addValueChangeListener(e -> { actualizarFormatoValor(); actualizarVista(); });
        valor.addValueChangeListener(e -> actualizarVista()); motivo.addValueChangeListener(e -> { detalle.setVisible(e.getValue() != null && e.getValue().otro()); actualizarVista(); });
        detalle.addValueChangeListener(e -> actualizarVista());
    }

    private void actualizarSelector() { boolean productos = alcance.getValue() == AlcanceDescuentoVenta.LINE; selector.setVisible(productos); filaCantidad.setVisible(productos); preview.setClassName("cc-summary-sale", !productos); refrescarSeleccion(); }
    private void refrescarSeleccion() {
        Set<UUID> actuales = lineas.stream().map(LineaVistaPreviaDescuentoInput::lineaId).collect(java.util.stream.Collectors.toSet()); seleccionadas.retainAll(actuales);
        long elegibles = lineas.stream().filter(LineaVistaPreviaDescuentoInput::permiteDescuentos).count(); int cantidad = seleccionadas.size();
        seleccionarTodos.setValue(elegibles > 0 && cantidad == elegibles); seleccionarTodos.setIndeterminate(cantidad > 0 && cantidad < elegibles); seleccionarTodos.setEnabled(elegibles > 0);
        contador.setText(cantidad + (cantidad == 1 ? " producto seleccionado" : " productos seleccionados")); cantidadSeleccionada.setText(Integer.toString(cantidad));
        boolean sinElegibles = elegibles == 0; mensajeSeleccion.setText(sinElegibles ? "No existen productos elegibles para aplicar descuentos." : cantidad == 0 ? "Seleccione al menos un producto." : ""); mensajeSeleccion.setVisible(sinElegibles || cantidad == 0);
        grid.getDataProvider().refreshAll(); actualizarVista();
    }

    private void actualizarVista() {
        try {
            boolean faltaMotivo = opciones.requiereMotivo() && motivo.getValue() == null;
            boolean faltaDetalle = motivo.getValue() != null && motivo.getValue().otro() && detalle.getValue().isBlank();
            if (valor.getValue() == null || valor.getValue().signum() <= 0 || faltaMotivo || faltaDetalle || alcance.getValue() == AlcanceDescuentoVenta.LINE && seleccionadas.isEmpty()) { limpiarVista(); return; }
            ultimaVista = service.previsualizar(solicitud(null)); base.setText(moneda(ultimaVista.baseElegible())); descuento.setText("-" + moneda(ultimaVista.montoDescuento())); posterior.setText(moneda(ultimaVista.importePosterior())); confirmar.setEnabled(true);
        } catch (RuntimeException ex) { limpiarVista(); }
    }

    private void confirmar() {
        try {
            SolicitudVistaPreviaDescuentoInput solicitud = solicitud(null); VistaPreviaDescuentoDto vista = service.previsualizar(solicitud);
            if (vista.requiereAutorizacion()) { if (!opciones.permiteAutorizacion()) throw new ReglaNegocioException("El descuento supera el límite permitido."); dialogoAutorizacion(solicitud, vista); return; }
            aplicar.accept(new Aplicado(solicitud.descuento(), vista)); close();
        } catch (RuntimeException ex) { error(ex, "No fue posible aplicar el descuento."); }
    }

    private void dialogoAutorizacion(SolicitudVistaPreviaDescuentoInput solicitud, VistaPreviaDescuentoDto vista) {
        Dialog d = new Dialog(); d.setHeaderTitle("AUTORIZAR DESCUENTO"); d.setWidth("min(560px,94vw)"); d.setCloseOnOutsideClick(false);
        TextField usuario = new TextField("Usuario autorizador"); usuario.setWidthFull(); PasswordField clave = new PasswordField("Contraseña / PIN"); clave.setWidthFull();
        Div resumen = new Div(fila("Descuento solicitado", porcentajeMonto(vista)), fila("Máximo permitido", limite(vista)), fila("Importe del descuento", moneda(vista.montoDescuento()))); resumen.addClassName("cc-discount-preview"); d.add(resumen, usuario, clave);
        AppActionButton autorizar = new AppActionButton(ActionType.ACCEPT, ButtonSize.MAIN, "Autorizar descuento", e -> { try { AutorizacionDescuentoDto autorizada = service.autorizar(new AutorizarDescuentoInput(solicitud, usuario.getValue(), clave.getValue())); DescuentoVentaInput x = solicitud.descuento(); aplicar.accept(new Aplicado(new DescuentoVentaInput(x.alcance(), x.tipo(), x.valor(), x.lineaIds(), x.motivoId(), x.detalle(), autorizada.id()), vista)); d.close(); close(); Notification.show("Descuento autorizado por " + autorizada.autorizadaPor() + "."); } catch (RuntimeException ex) { error(ex, "No fue posible autorizar el descuento."); } });
        d.getFooter().add(autorizar, new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN, "Cancelar", e -> d.close())); d.addOpenedChangeListener(e -> { if (e.isOpened()) usuario.focus(); }); d.open();
    }

    private SolicitudVistaPreviaDescuentoInput solicitud(UUID autorizacion) { ConfiguracionDescuentoDto.Motivo m = motivo.getValue(); List<UUID> ids = alcance.getValue() == AlcanceDescuentoVenta.LINE ? List.copyOf(seleccionadas) : List.of(); DescuentoVentaInput d = new DescuentoVentaInput(alcance.getValue(), tipo.getValue(), valor.getValue(), ids, m == null ? null : m.id(), detalle.isVisible() ? detalle.getValue() : null, autorizacion); return new SolicitudVistaPreviaDescuentoInput(operacionId, d, lineas); }
    private List<AlcanceDescuentoVenta> disponiblesAlcance() { List<AlcanceDescuentoVenta> x = new ArrayList<>(); if (opciones.permiteVenta()) x.add(AlcanceDescuentoVenta.SALE); if (opciones.permiteLinea()) x.add(AlcanceDescuentoVenta.LINE); if (x.isEmpty()) throw new ReglaNegocioException("No hay alcances de descuento habilitados."); return x; }
    private List<TipoDescuentoVenta> disponiblesTipo() { List<TipoDescuentoVenta> x = new ArrayList<>(); if (opciones.permitePorcentaje()) x.add(TipoDescuentoVenta.PERCENTAGE); if (opciones.permiteMonto()) x.add(TipoDescuentoVenta.AMOUNT); if (x.isEmpty()) throw new ReglaNegocioException("No hay tipos de descuento habilitados."); return x; }
    private void actualizarFormatoValor() { boolean porcentaje = tipo.getValue() == TipoDescuentoVenta.PERCENTAGE; valor.setSuffixComponent(porcentaje ? new Span("%") : null); valor.setPrefixComponent(porcentaje ? null : new Span(opciones.moneda())); }
    private void limpiarVista() { ultimaVista = null; base.setText("—"); descuento.setText("—"); posterior.setText("—"); if (confirmar != null) confirmar.setEnabled(false); }
    private BigDecimal importe(LineaVistaPreviaDescuentoInput l) { return (l.precioEsperado() == null ? BigDecimal.ZERO : l.precioEsperado()).multiply(l.cantidad() == null ? BigDecimal.ZERO : l.cantidad()); }
    private String nombre(LineaVistaPreviaDescuentoInput l) { return l.productoNombre() == null || l.productoNombre().isBlank() ? "Producto" : l.productoNombre(); }
    private String moneda(BigDecimal v) { return opciones.moneda() + " " + String.format(Locale.US, "%,.2f", v == null ? BigDecimal.ZERO : v); }
    private String cantidad(BigDecimal v) { return v == null ? "0" : v.stripTrailingZeros().toPlainString(); }
    private String porcentajeMonto(VistaPreviaDescuentoDto v) { return String.format(Locale.US, "%.2f%% · %s", v.porcentajeEfectivo(), moneda(v.montoDescuento())); }
    private String limite(VistaPreviaDescuentoDto v) { String p = v.porcentajeMaximoUsuario() == null ? "Sin límite" : String.format(Locale.US, "%.2f%%", v.porcentajeMaximoUsuario()); String m = v.montoMaximoUsuario() == null ? "Sin límite" : moneda(v.montoMaximoUsuario()); return p + " · " + m; }
    private static Div grupoCampo(String etiqueta, boolean requerido, Component control) { Span label = new Span(etiqueta + (requerido ? " *" : "")); label.addClassName("cc-adjustment-inline-label"); Div grupo = new Div(label, control); grupo.addClassName("cc-adjustment-field-group"); return grupo; }
    private static Div metrica(String etiqueta, Span valor) { Span label = new Span(etiqueta); label.addClassName("cc-adjustment-summary-label"); Div d = new Div(label, valor); d.addClassName("cc-adjustment-summary-metric"); return d; }
    private static Div fila(String etiqueta, Span valor) { Div d = new Div(new Span(etiqueta), valor); d.addClassName("cc-discount-preview-row"); return d; }
    private static Div fila(String etiqueta, String valor) { return fila(etiqueta, new Span(valor)); }
    private static void error(RuntimeException ex, String fallback) { Notification.show(ex.getMessage() == null || ex.getMessage().isBlank() ? fallback : ex.getMessage()); }
    public record Aplicado(DescuentoVentaInput descuento, VistaPreviaDescuentoDto vista) {}
}
