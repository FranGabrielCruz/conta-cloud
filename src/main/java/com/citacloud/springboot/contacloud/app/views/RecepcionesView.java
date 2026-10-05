package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value = "recepciones", layout = MainLayout.class)
@PageTitle("Recepciones | ContaCloud")
@PermitAll
public class RecepcionesView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final PurchaseReceiptService service;
    private final EmpresaModuloService modules;
    private final TextField search = new TextField("Buscar");
    private final DatePicker from = new DatePicker("Desde");
    private final DatePicker to = new DatePicker("Hasta");
    private final Select<EstadoRecepcionCompra> status = new Select<>();
    private final AppGrid<RecepcionCompraDto> grid = new AppGrid<>(RecepcionCompraDto.class);
    private final AppPagination pagination;

    public RecepcionesView(PurchaseReceiptService service, EmpresaModuloService modules) {
        this.service = service;
        this.modules = modules;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        Component action = can("recepciones.crear")
            ? new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Nueva recepción", event -> form(null)) : null;
        add(action == null
            ? new AppPageHeader("RECEPCIONES", "Confirma entradas de productos al inventario de forma separada de la factura.")
            : new AppPageHeader("RECEPCIONES", "Confirma entradas de productos al inventario de forma separada de la factura.", action));
        search.setPlaceholder("Número, referencia o proveedor...");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(350);
        status.setLabel("Estado");
        status.setItems(EstadoRecepcionCompra.values());
        status.setItemLabelGenerator(RecepcionesView::state);
        status.setEmptySelectionAllowed(true);
        status.setPlaceholder("Todos");
        search.addValueChangeListener(event -> { if (event.isFromClient()) first(); });
        from.addValueChangeListener(event -> { if (event.isFromClient()) first(); });
        to.addValueChangeListener(event -> { if (event.isFromClient()) first(); });
        status.addValueChangeListener(event -> { if (event.isFromClient()) first(); });
        HorizontalLayout filters = new HorizontalLayout(search, from, to, status);
        filters.addClassName("cc-filter-bar");
        filters.setAlignItems(Alignment.END);
        configureGrid();
        pagination = new AppPagination(request -> load());
        add(filters, grid, pagination);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!modules.habilitado("COMPRAS") || !can("recepciones.ver")) {
            Notification.show("No tienes permiso para acceder a esta opción.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        load();
    }

    private void configureGrid() {
        grid.addColumn(RecepcionCompraDto::numero).setHeader("Recepción").setAutoWidth(true);
        grid.addColumn(RecepcionCompraDto::proveedor).setHeader("Proveedor").setFlexGrow(1);
        grid.addColumn(item -> item.ordenCompra() == null ? "Sin orden" : item.ordenCompra()).setHeader("Orden").setAutoWidth(true);
        grid.addColumn(RecepcionCompraDto::almacen).setHeader("Almacén").setFlexGrow(1);
        grid.addColumn(item -> DATE.format(item.fecha())).setHeader("Fecha").setAutoWidth(true);
        grid.addComponentColumn(item -> badge(item.estado())).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(this::actionsFor).setHeader("Acciones").setAutoWidth(true);
        grid.setEmptyStateText("No hay recepciones registradas.");
    }

    private Component actionsFor(RecepcionCompraDto item) {
        HorizontalLayout actions = actions();
        actions.add(new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION, "Ver recepción", event -> detail(item.id())));
        if (item.estado() == EstadoRecepcionCompra.DRAFT && can("recepciones.editar"))
            actions.add(new AppActionButton(ActionType.EDIT, ButtonSize.GRID_ACTION, "Editar recepción", event -> form(item.id())));
        return actions;
    }

    private void load() {
        try {
            var request = pagination.currentRequest();
            var page = service.search(search.getValue(), from.getValue(), to.getValue(), status.getValue(), request.page(), request.size());
            grid.setItems(page.getContent());
            pagination.setTotal(page.getTotalElements());
        } catch (RuntimeException exception) {
            error(exception, "No fue posible cargar las recepciones.");
        }
    }

    private void first() { pagination.reset(); load(); }

    private void form(UUID id) {
        try {
            RecepcionCompraDto current = id == null ? null : service.get(id);
            var catalogs = service.catalogs(current == null ? null : current.proveedorId());
            Dialog dialog = dialog(current == null ? "NUEVA RECEPCIÓN" : "EDITAR RECEPCIÓN");
            ComboBox<ComprasCatalogosDto.Opcion> supplier = combo("Proveedor", catalogs.proveedores());
            ComboBox<ComprasCatalogosDto.Opcion> order = combo("Orden de compra (opcional)", catalogs.ordenes());
            ComboBox<FacturaRecepcionOpcionDto> invoice = new ComboBox<>("Factura de proveedor (opcional)");
            invoice.setItems(query -> service.searchRegisteredInvoices(id(supplier), query.getFilter().orElse(""),
                query.getOffset(), query.getLimit()).stream());
            invoice.setItemLabelGenerator(FacturaRecepcionOpcionDto::etiqueta);
            invoice.setClearButtonVisible(true);
            ComboBox<ComprasCatalogosDto.Opcion> warehouse = combo("Almacén", catalogs.almacenes());
            DatePicker date = new DatePicker("Fecha de recepción");
            TextField reference = new TextField("Referencia");
            TextArea notes = new TextArea("Notas");
            TextArea differenceNote = new TextArea("Motivo / Observación de diferencia");
            UUID idempotencyKey = current == null ? UUID.randomUUID() : null;
            Span differenceWarning = new Span("⚠ Esta recepción contiene diferencias respecto del documento de origen.");
            differenceWarning.getStyle().set("color", "var(--lumo-warning-text-color)").set("font-weight", "600");
            supplier.setRequiredIndicatorVisible(true);
            warehouse.setRequiredIndicatorVisible(true);
            date.setRequiredIndicatorVisible(true);
            reference.setMaxLength(100);
            notes.setMaxLength(1000);
            differenceNote.setMaxLength(1000);
            differenceNote.setWidthFull();

            List<LineDraft> lines = new ArrayList<>();
            Grid<LineDraft> lineGrid = lineGrid(lines, order, invoice, differenceWarning, differenceNote);
            Runnable refresh = () -> refreshLines(lineGrid, lines, order.getValue() != null,
                invoice.getValue() != null, differenceWarning, differenceNote);

            if (current == null) {
                date.setValue(LocalDate.now());
            } else {
                select(supplier, catalogs.proveedores(), current.proveedorId());
                select(order, catalogs.ordenes(), current.ordenCompraId());
                if (current.facturaProveedorId() != null)
                    invoice.setValue(service.registeredInvoiceOption(current.facturaProveedorId()));
                select(warehouse, catalogs.almacenes(), current.almacenId());
                date.setValue(current.fecha());
                reference.setValue(value(current.referencia()));
                notes.setValue(value(current.notas()));
                differenceNote.setValue(value(current.motivoDiferencia()));
                for (var line : current.lineas()) {
                    lines.add(new LineDraft(line.lineaOrdenId(), line.lineaFacturaId(), service.productOption(line.productoId()),
                        line.cantidad(), line.ordenada(), line.facturada(), line.recibidaAntesOrden(),
                        line.recibidaAntesFactura()));
                }
            }
            refresh.run();

            supplier.addValueChangeListener(event -> {
                if (event.getValue() == null) return;
                var updated = service.catalogs(event.getValue().id());
                order.setItems(updated.ordenes());
                invoice.getDataProvider().refreshAll();
                if (event.isFromClient()) {
                    order.clear();
                    invoice.clear();
                    lines.clear();
                    refresh.run();
                }
            });
            order.addValueChangeListener(event -> {
                if (!event.isFromClient()) return;
                if (event.getValue() == null) {
                    lines.removeIf(line -> line.orderLineId() != null);
                    refresh.run();
                    return;
                }
                Runnable change = () -> loadOrderLines(event.getValue().id(), lines, refresh);
                if (lines.isEmpty()) change.run();
                else confirmOrderChange(change, () -> order.setValue(event.getOldValue()));
            });
            invoice.addValueChangeListener(event -> {
                if (!event.isFromClient()) return;
                if (event.getValue() == null) {
                    lines.removeIf(line -> line.invoiceLineId() != null);
                    refresh.run();
                    return;
                }
                Runnable change = () -> {
                    FacturaRecepcionOpcionDto selected = event.getValue();
                    if (supplier.getValue() == null) {
                        select(supplier, catalogs.proveedores(), selected.proveedorId());
                    }
                    if (selected.ordenCompraId() != null) {
                        var updated = service.catalogs(selected.proveedorId());
                        order.setItems(updated.ordenes());
                        select(order, updated.ordenes(), selected.ordenCompraId());
                    }
                    loadInvoiceLines(selected.id(), lines, refresh);
                };
                if (lines.isEmpty()) change.run();
                else confirmInvoiceChange(change, () -> invoice.setValue(event.getOldValue()));
            });

            AppActionButton add = new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Agregar producto",
                event -> lineForm(null, lines, order, invoice, refresh));
            HorizontalLayout heading = new HorizontalLayout(new H3("PRODUCTOS"), add);
            heading.setWidthFull();
            heading.setJustifyContentMode(JustifyContentMode.BETWEEN);
            heading.setAlignItems(Alignment.CENTER);
            dialog.add(section("INFORMACIÓN GENERAL", supplier, order, invoice, warehouse, date, reference, notes),
                heading, lineGrid, differenceWarning, differenceNote);

            AppActionButton save = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, "Guardar", null);
            save.addClickListener(event -> {
                save.setEnabled(false);
                try {
                    RecepcionCompraInput input = receiptInput(supplier, order, invoice, warehouse, date, reference, notes,
                        differenceNote, lines, current, idempotencyKey);
                    if (current == null) service.create(input); else service.update(current.id(), input);
                    dialog.close();
                    load();
                    Notification.show("Recepción guardada correctamente.");
                } catch (RuntimeException exception) {
                    save.setEnabled(true);
                    error(exception, "No fue posible guardar la recepción.");
                }
            });

            if (can("recepciones.confirmar")) {
                AppActionButton confirm = new AppActionButton(ActionType.ISSUE, ButtonSize.MAIN, "Confirmar recepción", null);
                confirm.addClickListener(event -> showConfirmation(lines, order.getValue(), invoice.getValue(),
                    differenceNote.getValue(), () -> {
                    confirm.setEnabled(false);
                    save.setEnabled(false);
                    try {
                        RecepcionCompraInput input = receiptInput(supplier, order, invoice, warehouse, date, reference, notes,
                            differenceNote, lines, current, idempotencyKey);
                        if (current == null) service.createAndConfirm(input);
                        else service.updateAndConfirm(current.id(), input);
                        dialog.close();
                        load();
                        Notification.show("Recepción guardada y confirmada; inventario actualizado.");
                    } catch (RuntimeException exception) {
                        confirm.setEnabled(true);
                        save.setEnabled(true);
                        error(exception, "No fue posible guardar y confirmar la recepción.");
                    }
                }));
                dialog.getFooter().add(confirm);
            }
            dialog.getFooter().add(save,
                new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN, "Cancelar", event -> dialog.close()));
            dialog.open();
        } catch (RuntimeException exception) {
            error(exception, "No fue posible abrir la recepción.");
        }
    }

    private Grid<LineDraft> lineGrid(List<LineDraft> lines, ComboBox<ComprasCatalogosDto.Opcion> order,
            ComboBox<FacturaRecepcionOpcionDto> invoice, Span warning, TextArea differenceNote) {
        Grid<LineDraft> grid = new Grid<>(LineDraft.class, false);
        grid.setAllRowsVisible(true);
        grid.setWidthFull();
        grid.addColumn(item -> item.product().nombre()).setHeader("Producto").setFlexGrow(1);
        grid.addColumn(item -> number(item.ordered())).setHeader("Ordenado").setAutoWidth(true);
        grid.addColumn(item -> number(item.invoiced())).setHeader("Facturado").setAutoWidth(true);
        grid.addColumn(item -> number(item.previouslyReceivedOrder())).setHeader("Recibido OC").setAutoWidth(true);
        grid.addColumn(item -> number(item.previouslyReceivedInvoice())).setHeader("Recibido factura").setAutoWidth(true);
        grid.addColumn(item -> number(item.quantity())).setHeader("Recibir ahora").setAutoWidth(true);
        grid.addColumn(item -> number(item.pendingOrder())).setHeader("Pendiente OC").setAutoWidth(true);
        grid.addColumn(item -> number(item.pendingInvoice())).setHeader("Pendiente factura").setAutoWidth(true);
        grid.addColumn(item -> item.origin(order.getValue() != null, invoice.getValue() != null)).setHeader("Origen").setAutoWidth(true);
        grid.addComponentColumn(item -> {
            HorizontalLayout actions = actions();
            actions.add(new AppActionButton(ActionType.EDIT, ButtonSize.GRID_ACTION,
                item.orderLineId() == null && item.invoiceLineId() == null ? "Editar producto" : "Editar cantidad",
                event -> lineForm(item, lines, order, invoice, () -> refreshLines(grid, lines,
                    order.getValue() != null, invoice.getValue() != null, warning, differenceNote))));
            if (item.orderLineId() == null && item.invoiceLineId() == null)
                actions.add(new AppActionButton(ActionType.DELETE, ButtonSize.GRID_ACTION, "Eliminar producto", event -> {
                    lines.remove(item);
                    refreshLines(grid, lines, order.getValue() != null, invoice.getValue() != null, warning, differenceNote);
                }));
            return actions;
        }).setHeader("Acciones").setAutoWidth(true);
        return grid;
    }

    private void loadOrderLines(UUID orderId, List<LineDraft> lines, Runnable refresh) {
        try {
            List<LineDraft> manual = lines.stream().filter(line -> line.orderLineId() == null).toList();
            lines.clear();
            lines.addAll(manual);
            for (var line : service.pendingFromOrder(orderId)) {
                if (lines.stream().noneMatch(item -> Objects.equals(item.product().id(), line.productoId())))
                    lines.add(new LineDraft(line.lineaOrdenId(), null, service.productOption(line.productoId()), line.pendiente(),
                        line.ordenada(), null, line.recibidaAntes(), null));
            }
            refresh.run();
        } catch (RuntimeException exception) {
            error(exception, "No fue posible cargar la orden.");
        }
    }

    private void loadInvoiceLines(UUID invoiceId, List<LineDraft> lines, Runnable refresh) {
        try {
            List<LineDraft> manual = lines.stream().filter(line -> line.invoiceLineId() == null
                && line.orderLineId() == null).toList();
            lines.clear();
            lines.addAll(manual);
            for (var line : service.pendingFromInvoice(invoiceId)) {
                if (lines.stream().noneMatch(item -> Objects.equals(item.product().id(), line.productoId()))) {
                    LineaRecepcionPendienteDto orderLine = line.lineaOrdenId() == null ? null
                        : service.orderLineForProduct(service.registeredInvoiceOption(invoiceId).ordenCompraId(), line.productoId());
                    lines.add(new LineDraft(line.lineaOrdenId(), line.lineaFacturaId(),
                        service.productOption(line.productoId()), line.pendiente(),
                        orderLine == null ? null : orderLine.ordenada(), line.facturada(),
                        orderLine == null ? null : orderLine.recibidaAntes(), line.recibidaAntes()));
                }
            }
            refresh.run();
        } catch (RuntimeException exception) {
            error(exception, "No fue posible cargar la factura.");
        }
    }

    private void lineForm(LineDraft current, List<LineDraft> lines, ComboBox<ComprasCatalogosDto.Opcion> order,
            ComboBox<FacturaRecepcionOpcionDto> invoice, Runnable refresh) {
        Dialog dialog = dialog(current == null ? "AGREGAR PRODUCTO" : "EDITAR PRODUCTO");
        ComboBox<OrdenCompraCatalogosDto.ProductoOpcion> product = new ComboBox<>("Producto");
        product.setItems(query -> service.searchProducts(query.getFilter().orElse(""), query.getOffset(), query.getLimit()).stream());
        product.setItemLabelGenerator(item -> item.codigo() + " · " + item.nombre());
        BigDecimalField quantity = new BigDecimalField("Cantidad recibida");
        TextField unit = new TextField("Unidad");
        unit.setReadOnly(true);
        product.addValueChangeListener(event -> unit.setValue(event.getValue() == null ? "" : value(event.getValue().unidad())));
        quantity.setRequiredIndicatorVisible(true);
        if (current == null) quantity.setValue(BigDecimal.ONE);
        else {
            product.setValue(current.product());
            product.setReadOnly(current.orderLineId() != null || current.invoiceLineId() != null);
            quantity.setValue(current.quantity());
        }
        dialog.add(section("PRODUCTO", product, unit, quantity));
        dialog.getFooter().add(new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, "Guardar línea", event -> {
            try {
                if (product.getValue() == null) throw new ReglaNegocioException("Selecciona un producto.");
                if (quantity.getValue() == null || quantity.getValue().signum() <= 0)
                    throw new ReglaNegocioException("La cantidad debe ser mayor que cero.");
                if (lines.stream().anyMatch(item -> item != current && Objects.equals(item.product().id(), product.getValue().id())))
                    throw new ReglaNegocioException("El producto ya existe en la recepción.");
                LineaRecepcionPendienteDto matched = order.getValue() == null ? null
                    : service.orderLineForProduct(order.getValue().id(), product.getValue().id());
                LineaRecepcionFacturaPendienteDto matchedInvoice = invoice.getValue() == null ? null
                    : service.invoiceLineForProduct(invoice.getValue().id(), product.getValue().id());
                LineDraft updated = new LineDraft(matched == null ? null : matched.lineaOrdenId(),
                    matchedInvoice == null ? null : matchedInvoice.lineaFacturaId(), product.getValue(),
                    quantity.getValue(), matched == null ? null : matched.ordenada(),
                    matchedInvoice == null ? null : matchedInvoice.facturada(),
                    matched == null ? null : matched.recibidaAntes(),
                    matchedInvoice == null ? null : matchedInvoice.recibidaAntes());
                Runnable persist = () -> {
                    if (current == null) lines.add(updated); else lines.set(lines.indexOf(current), updated);
                    refresh.run();
                    dialog.close();
                };
                if (order.getValue() != null && matched == null) showUnorderedProductWarning(product.getValue(), order.getValue(), persist);
                else persist.run();
            } catch (RuntimeException exception) {
                error(exception, "No fue posible guardar la línea.");
            }
        }), new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN, "Cancelar", event -> dialog.close()));
        dialog.open();
    }

    private void showUnorderedProductWarning(OrdenCompraCatalogosDto.ProductoOpcion product,
            ComprasCatalogosDto.Opcion order, Runnable continueAction) {
        ConfirmDialog confirmation = new ConfirmDialog();
        confirmation.setHeader("PRODUCTO NO INCLUIDO EN LA ORDEN");
        confirmation.setText("El producto \"" + product.nombre() + "\" no está incluido en la orden " + order.nombre()
            + ". Puede agregarlo y quedará identificado como un producto recibido fuera de la orden de compra.");
        confirmation.setConfirmText("Agregar");
        confirmation.setCancelText("Cancelar");
        confirmation.setCancelable(true);
        confirmation.addConfirmListener(event -> continueAction.run());
        confirmation.open();
    }

    private void showConfirmation(List<LineDraft> lines, ComprasCatalogosDto.Opcion order,
            FacturaRecepcionOpcionDto invoice, String differenceNote, Runnable confirmAction) {
        boolean differences = hasDifferences(lines, order != null, invoice != null);
        String origin = invoice != null ? invoice.etiqueta() : order != null ? order.nombre() : "la recepción manual";
        StringBuilder text = new StringBuilder(differences
            ? "Esta recepción contiene diferencias respecto de " + origin + ".\n\n"
            : "La recepción generará las entradas correspondientes en el inventario.");
        if (differences) {
            for (LineDraft line : lines) {
                if (invoice == null && line.unordered(order != null)) text.append("\n• ").append(line.product().nombre()).append(": producto fuera de la orden.");
                if (line.uninvoiced(invoice != null)) text.append("\n• ").append(line.product().nombre()).append(": producto fuera de la factura.");
                if (invoice == null && line.excessOrder().signum() > 0) text.append("\n• ").append(line.product().nombre())
                    .append(": exceso sobre la orden de ").append(line.excessOrder().toPlainString()).append(".");
                if (line.excessInvoice().signum() > 0) text.append("\n• ").append(line.product().nombre())
                    .append(": exceso sobre la factura de ").append(line.excessInvoice().toPlainString()).append(".");
            }
            if (!value(differenceNote).isBlank()) text.append("\n\nMotivo: ").append(differenceNote.trim());
        }
        if (invoice != null) text.append("\n\nLa confirmación no modificará la cuenta por pagar de la factura.");
        ConfirmDialog confirmation = new ConfirmDialog();
        confirmation.setHeader("CONFIRMAR RECEPCIÓN");
        confirmation.setText(text.toString());
        confirmation.setConfirmText("Confirmar");
        confirmation.setCancelText("Cancelar");
        confirmation.setCancelable(true);
        confirmation.addConfirmListener(event -> confirmAction.run());
        confirmation.open();
    }

    private void confirmOrderChange(Runnable continueAction, Runnable cancelAction) {
        ConfirmDialog confirmation = new ConfirmDialog();
        confirmation.setHeader("CAMBIAR ORDEN DE COMPRA");
        confirmation.setText("Cambiar la orden modificará las líneas relacionadas con la orden actual. Los productos agregados manualmente se conservarán.");
        confirmation.setConfirmText("Continuar");
        confirmation.setCancelText("Cancelar");
        confirmation.setCancelable(true);
        confirmation.addConfirmListener(event -> continueAction.run());
        confirmation.addCancelListener(event -> cancelAction.run());
        confirmation.open();
    }

    private void confirmInvoiceChange(Runnable continueAction, Runnable cancelAction) {
        ConfirmDialog confirmation = new ConfirmDialog();
        confirmation.setHeader("CAMBIAR FACTURA DE PROVEEDOR");
        confirmation.setText("Cambiar la factura reemplazará las líneas relacionadas con la factura actual. "
            + "Los productos agregados manualmente se conservarán.");
        confirmation.setConfirmText("Continuar");
        confirmation.setCancelText("Cancelar");
        confirmation.setCancelable(true);
        confirmation.addConfirmListener(event -> continueAction.run());
        confirmation.addCancelListener(event -> cancelAction.run());
        confirmation.open();
    }

    private void detail(UUID id) {
        try {
            var receipt = service.get(id);
            Dialog dialog = dialog("RECEPCIÓN " + receipt.numero());
            Grid<LineaRecepcionCompraDto> lines = new Grid<>(LineaRecepcionCompraDto.class, false);
            lines.setAllRowsVisible(true);
            lines.setWidthFull();
            lines.addColumn(LineaRecepcionCompraDto::descripcion).setHeader("Producto").setFlexGrow(1);
            lines.addColumn(line -> number(line.ordenada())).setHeader("Ordenado");
            lines.addColumn(line -> number(line.facturada())).setHeader("Facturado");
            lines.addColumn(line -> number(line.recibidaAntesOrden())).setHeader("Recibido OC");
            lines.addColumn(line -> number(line.recibidaAntesFactura())).setHeader("Recibido factura");
            lines.addColumn(line -> number(line.cantidad())).setHeader("Esta recepción");
            lines.addColumn(line -> number(line.pendienteOrden())).setHeader("Pendiente OC");
            lines.addColumn(line -> number(line.pendienteFactura())).setHeader("Pendiente factura");
            lines.addColumn(line -> switch (line.origen()) {
                case PURCHASE_ORDER -> value(receipt.ordenCompra());
                case PURCHASE_INVOICE -> value(receipt.facturaProveedor());
                case MANUAL -> "Manual";
            }).setHeader("Origen");
            lines.setItems(receipt.lineas());
            AppDetailSection information = new AppDetailSection("INFORMACIÓN")
                .field("Proveedor", receipt.proveedor()).field("Orden", receipt.ordenCompra() == null ? "Sin orden" : receipt.ordenCompra())
                .field("Factura", receipt.facturaProveedor() == null ? "Sin factura" : receipt.facturaProveedor())
                .field("Almacén", receipt.almacen()).field("Fecha", DATE.format(receipt.fecha()))
                .field("Estado", state(receipt.estado())).field("Referencia", receipt.referencia());
            if (receipt.confirmadaEn() != null) information.field("Confirmada", DATE_TIME.format(receipt.confirmadaEn()));
            dialog.add(information, lines);
            if (receipt.lineas().stream().anyMatch(line -> line.diferencia() != TipoDiferenciaRecepcion.NONE))
                dialog.add(new AppDetailSection("DIFERENCIAS").field("Motivo", receipt.motivoDiferencia()));
            if (receipt.estado() == EstadoRecepcionCompra.DRAFT && can("recepciones.confirmar"))
                dialog.getFooter().add(new AppActionButton(ActionType.ISSUE, ButtonSize.MAIN, "Confirmar recepción", event -> {
                    try {
                        service.confirm(receipt.id(), receipt.version());
                        dialog.close();
                        load();
                        Notification.show("Recepción confirmada e inventario actualizado.");
                    } catch (RuntimeException exception) {
                        error(exception, "No fue posible confirmar la recepción.");
                    }
                }));
            if (receipt.estado() != EstadoRecepcionCompra.VOIDED && can("recepciones.anular"))
                dialog.getFooter().add(new AppActionButton(ActionType.VOID, ButtonSize.MAIN, "Anular recepción",
                    event -> voidDialog(receipt, dialog)));
            dialog.getFooter().add(new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN, "Cerrar", event -> dialog.close()));
            dialog.open();
        } catch (RuntimeException exception) {
            error(exception, "No fue posible consultar la recepción.");
        }
    }

    private void voidDialog(RecepcionCompraDto receipt, Dialog parent) {
        Dialog dialog = dialog("ANULAR RECEPCIÓN");
        TextArea reason = new TextArea("Motivo de anulación");
        reason.setRequiredIndicatorVisible(true);
        dialog.add(reason);
        dialog.getFooter().add(new AppActionButton(ActionType.VOID, ButtonSize.MAIN, "Anular", event -> {
            try {
                service.voidReceipt(receipt.id(), receipt.version(), reason.getValue());
                dialog.close(); parent.close(); load();
                Notification.show("Recepción anulada y movimiento inverso registrado.");
            } catch (RuntimeException exception) {
                error(exception, "No fue posible anular la recepción.");
            }
        }), new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN, "Cancelar", event -> dialog.close()));
        dialog.open();
    }

    private static void refreshLines(Grid<LineDraft> grid, List<LineDraft> lines, boolean hasOrder,
            boolean hasInvoice, Span warning, TextArea differenceNote) {
        grid.setItems(lines);
        boolean differences = hasDifferences(lines, hasOrder, hasInvoice);
        warning.setVisible(differences);
        differenceNote.setVisible(differences);
        differenceNote.setRequiredIndicatorVisible(differences);
    }

    private static boolean hasDifferences(List<LineDraft> lines, boolean hasOrder, boolean hasInvoice) {
        if (hasInvoice) return lines.stream().anyMatch(line -> line.uninvoiced(true) || line.excessInvoice().signum() > 0);
        return hasOrder && lines.stream().anyMatch(line -> line.unordered(true) || line.excessOrder().signum() > 0);
    }

    private static RecepcionCompraInput receiptInput(ComboBox<ComprasCatalogosDto.Opcion> supplier,
            ComboBox<ComprasCatalogosDto.Opcion> order, ComboBox<FacturaRecepcionOpcionDto> invoice,
            ComboBox<ComprasCatalogosDto.Opcion> warehouse,
            DatePicker date, TextField reference, TextArea notes, TextArea differenceNote,
            List<LineDraft> lines, RecepcionCompraDto current, UUID idempotencyKey) {
        return new RecepcionCompraInput(id(supplier), id(order), invoice.getValue() == null ? null : invoice.getValue().id(),
            id(warehouse), date.getValue(), reference.getValue(),
            notes.getValue(), differenceNote.getValue(), lines.stream()
                .map(line -> new LineaRecepcionCompraInput(line.orderLineId(), line.invoiceLineId(),
                    line.product().id(), line.quantity())).toList(),
            current == null ? null : current.version(), idempotencyKey);
    }

    private static ComboBox<ComprasCatalogosDto.Opcion> combo(String label, List<ComprasCatalogosDto.Opcion> items) {
        ComboBox<ComprasCatalogosDto.Opcion> combo = new ComboBox<>(label);
        combo.setItems(items);
        combo.setItemLabelGenerator(ComprasCatalogosDto.Opcion::nombre);
        combo.setClearButtonVisible(true);
        return combo;
    }
    private static void select(ComboBox<ComprasCatalogosDto.Opcion> combo,
            List<ComprasCatalogosDto.Opcion> items, UUID id) {
        combo.setValue(items.stream().filter(item -> Objects.equals(item.id(), id)).findFirst().orElse(null));
    }
    private static UUID id(ComboBox<ComprasCatalogosDto.Opcion> combo) {
        return combo.getValue() == null ? null : combo.getValue().id();
    }
    private static Div section(String title, Component... fields) {
        Div section = new Div();
        section.addClassName("cc-form-section");
        section.add(new H3(title));
        Div content = new Div(fields);
        content.addClassName("cc-dialog-form");
        section.add(content);
        return section;
    }
    private static Dialog dialog(String title) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setWidth("min(1200px,96vw)");
        return dialog;
    }
    private static HorizontalLayout actions() {
        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassName("cc-grid-actions");
        actions.setSpacing(false);
        return actions;
    }
    private static Span badge(EstadoRecepcionCompra status) {
        Span badge = new Span(state(status));
        badge.getElement().getThemeList().add("badge " + (status == EstadoRecepcionCompra.CONFIRMED
            ? "success" : status == EstadoRecepcionCompra.VOIDED ? "error" : "contrast"));
        return badge;
    }
    private static String state(EstadoRecepcionCompra status) {
        if (status == null) return "Todos";
        return switch (status) { case DRAFT -> "Borrador"; case CONFIRMED -> "Confirmada"; case VOIDED -> "Anulada"; };
    }
    private static String number(BigDecimal value) { return value == null ? "—" : value.toPlainString(); }
    private static String value(String value) { return value == null ? "" : value; }
    private static boolean can(String permission) { return TenantContext.principalActual().permisos().contains(permission); }
    private static void error(RuntimeException exception, String fallback) {
        Notification.show(exception instanceof ReglaNegocioException || exception instanceof RecursoNoEncontradoException
            ? exception.getMessage() : fallback);
    }

    private record LineDraft(UUID orderLineId, UUID invoiceLineId, OrdenCompraCatalogosDto.ProductoOpcion product,
                             BigDecimal quantity, BigDecimal ordered, BigDecimal invoiced,
                             BigDecimal previouslyReceivedOrder, BigDecimal previouslyReceivedInvoice) {
        BigDecimal pendingOrder() {
            return ordered == null ? null : ordered.subtract(zero(previouslyReceivedOrder))
                .subtract(quantity).max(BigDecimal.ZERO);
        }
        BigDecimal pendingInvoice() {
            return invoiced == null ? null : invoiced.subtract(zero(previouslyReceivedInvoice))
                .subtract(quantity).max(BigDecimal.ZERO);
        }
        BigDecimal excessOrder() {
            return ordered == null ? BigDecimal.ZERO : zero(previouslyReceivedOrder)
                .add(quantity).subtract(ordered).max(BigDecimal.ZERO);
        }
        BigDecimal excessInvoice() {
            return invoiced == null ? BigDecimal.ZERO : zero(previouslyReceivedInvoice)
                .add(quantity).subtract(invoiced).max(BigDecimal.ZERO);
        }
        boolean unordered(boolean hasOrder) { return hasOrder && orderLineId == null; }
        boolean uninvoiced(boolean hasInvoice) { return hasInvoice && invoiceLineId == null; }
        String origin(boolean hasOrder, boolean hasInvoice) {
            if (invoiceLineId != null) return "Factura";
            if (orderLineId != null) return "Orden de compra";
            if (hasInvoice) return "Fuera de factura";
            return hasOrder ? "Fuera de OC" : "Manual";
        }
        private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    }
}
