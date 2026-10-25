package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import com.citacloud.springboot.contacloud.app.services.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.springframework.data.domain.Page;

import java.math.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Gestión paginada de recibos y cobros sin abandonar el punto de venta. */
public final class RecibosCobrosDialog extends Dialog {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CobroClienteService service;
    private final FacturacionPosService pos;
    private final TerminalPosDto terminal;
    private final Consumer<UUID> print;
    private final Grid<CobroClienteDto> grid = new Grid<>();
    private final TextField search = new TextField("Buscar");
    private final DatePicker from = new DatePicker("Desde");
    private final DatePicker to = new DatePicker("Hasta");
    private final ComboBox<EstadoReciboCliente> status = new ComboBox<>("Estado");
    private final PosDialogPagination pagination = new PosDialogPagination(request -> load());

    public RecibosCobrosDialog(CobroClienteService service, FacturacionPosService pos,
                               TerminalPosDto terminal, Consumer<UUID> print) {
        this.service = service;
        this.pos = pos;
        this.terminal = terminal;
        this.print = print;
        setHeaderTitle("RECIBOS / COBROS");
        setWidth("min(1180px, 97vw)");
        addClassName("cc-pos-receipts-dialog");
        configureFilters();
        configureGrid();
        VerticalLayout body = new VerticalLayout(filters(), grid, pagination);
        body.setPadding(false);
        body.setSpacing(false);
        add(body);
        Button create = new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Nuevo cobro", e -> openNew());
        create.setVisible(hasPermission("recibos.crear"));
        Button close = new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN, "Cerrar", e -> close());
        getFooter().add(create, close);
        addOpenedChangeListener(event -> { if (event.isOpened()) { pagination.reset(); load(); } });
    }

    private void configureFilters() {
        search.setPlaceholder("Recibo, cliente, identificación o factura");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(300);
        status.setItems(EstadoReciboCliente.values());
        status.setItemLabelGenerator(RecibosCobrosDialog::statusLabel);
        status.setClearButtonVisible(true);
        search.addValueChangeListener(e -> reloadFirst());
        from.addValueChangeListener(e -> reloadFirst());
        to.addValueChangeListener(e -> reloadFirst());
        status.addValueChangeListener(e -> reloadFirst());
    }

    private Component filters() {
        HorizontalLayout row = new HorizontalLayout(search, from, to, status);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.END);
        row.expand(search);
        row.addClassName("cc-pos-dialog-filters");
        return row;
    }

    private void configureGrid() {
        grid.addColumn(CobroClienteDto::numero).setHeader("Recibo").setAutoWidth(true);
        grid.addColumn(x -> x.confirmadoEn() == null ? DATE.format(x.fecha()) : DATE_TIME.format(x.confirmadoEn()))
                .setHeader("Fecha/Hora").setAutoWidth(true);
        grid.addComponentColumn(x -> { Span value = new Span(x.cliente()); value.getElement().setAttribute("title", x.cliente()); return value; })
                .setHeader("Cliente").setFlexGrow(1);
        grid.addColumn(x -> money(x.moneda(), x.recibido())).setHeader("Total").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(x -> money(x.moneda(), x.aplicado())).setHeader("Aplicado").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(x -> money(x.moneda(), x.disponible())).setHeader("Disponible").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(x -> statusLabel(x.estado())).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(x -> action(VaadinIcon.EYE, "Ver recibo", e -> openDetail(x.id())))
                .setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        grid.setAllRowsVisible(true);
        grid.setEmptyStateText("No hay recibos para mostrar.");
        grid.addClassName("cc-pos-dialog-grid");
    }

    private void load() {
        try {
            Page<CobroClienteDto> page = service.buscar(search.getValue(), from.getValue(), to.getValue(),
                    status.getValue(), pagination.currentRequest().page());
            grid.setItems(page.getContent());
            pagination.setTotal(page.getTotalElements());
        } catch (RuntimeException ex) { notifyError("No fue posible cargar los recibos."); }
    }

    private void reloadFirst() { pagination.reset(); load(); }

    private void openNew() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("NUEVO RECIBO / COBRO");
        dialog.setWidth("min(1100px, 97vw)");
        ComboBox<ClienteCobroDto> customer = new ComboBox<>("Cliente");
        customer.setRequiredIndicatorVisible(true);
        customer.setWidthFull();
        customer.setPageSize(5);
        customer.setItemLabelGenerator(x -> x.codigo() + " · " + x.nombre());
        customer.setItems(query -> service.buscarClientes(query.getFilter().orElse(""), query.getPage()).stream());
        DatePicker date = new DatePicker("Fecha", terminal.fechaOperativa());
        TextField currency = new TextField("Moneda");
        currency.setValue(terminal.monedaCodigo());
        currency.setReadOnly(true);
        TextField reference = new TextField("Referencia");
        TextArea observation = new TextArea("Observación");
        UUID idempotency = UUID.randomUUID();
        BigDecimalField received = new BigDecimalField("Total recibido");
        received.setRequiredIndicatorVisible(true);
        received.setPrefixComponent(new Span(terminal.monedaCodigo()));
        received.setValue(BigDecimal.ZERO);
        received.addBlurListener(e -> PosNumberFormatter.showMoney(received));
        received.addFocusListener(e -> PosNumberFormatter.showQuantity(received));

        Grid<PendingLine> invoices = new Grid<>();
        List<PendingLine> lines = new ArrayList<>();
        PosDialogPagination[] invoicePaginationHolder = new PosDialogPagination[1];
        PosDialogPagination invoicePagination = PosDialogPagination.fixed(request -> {
            if (customer.getValue() == null) return;
            loadInvoices(customer.getValue(), request.page(), invoices, lines, received, invoicePaginationHolder[0]);
        });
        invoicePaginationHolder[0] = invoicePagination;
        invoices.addColumn(x -> x.invoice.numero()).setHeader("Factura").setAutoWidth(true);
        invoices.addColumn(x -> DATE.format(x.invoice.fecha())).setHeader("Fecha").setAutoWidth(true);
        invoices.addColumn(x -> x.invoice.vencimiento() == null ? "—" : DATE.format(x.invoice.vencimiento()))
                .setHeader("Vencimiento").setAutoWidth(true);
        invoices.addColumn(x -> money(x.invoice.moneda(), x.invoice.saldo())).setHeader("Saldo").setAutoWidth(true);
        invoices.addComponentColumn(x -> {
            BigDecimalField amount = new BigDecimalField();
            amount.setWidth("150px");
            amount.setValue(x.amount);
            amount.addValueChangeListener(e -> { x.amount = scale(e.getValue()); initializeReceivedWhenEmpty(lines, received); });
            return amount;
        }).setHeader("Aplicar").setAutoWidth(true);
        invoices.setSelectionMode(Grid.SelectionMode.MULTI);
        invoices.addSelectionListener(e -> {
            BigDecimal objetivo = scale(received.getValue());
            BigDecimal asignado = lines.stream().filter(line -> e.getAllSelectedItems().contains(line))
                    .map(line -> line.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (PendingLine line : lines) {
                if (e.getAllSelectedItems().contains(line) && line.amount.signum() == 0) {
                    line.amount = suggestedApplication(line.invoice.saldo(), objetivo, asignado);
                    asignado = asignado.add(line.amount);
                }
                if (!e.getAllSelectedItems().contains(line)) line.amount = BigDecimal.ZERO.setScale(4);
            }
            invoices.getDataProvider().refreshAll(); initializeReceivedWhenEmpty(lines, received);
        });
        invoices.setAllRowsVisible(true);
        invoices.setEmptyStateText("Selecciona un cliente para consultar sus facturas pendientes.");
        customer.addValueChangeListener(e -> { invoicePagination.reset(); lines.clear(); invoices.setItems(lines);
            if (e.getValue() != null) loadInvoices(e.getValue(), 0, invoices, lines, received, invoicePagination); });

        HorizontalLayout top = new HorizontalLayout(customer, date, currency);
        top.setWidthFull(); top.expand(customer); top.setAlignItems(FlexComponent.Alignment.END);
        HorizontalLayout extra = new HorizontalLayout(received, reference); extra.setWidthFull(); extra.expand(reference);
        VerticalLayout content = new VerticalLayout(top, new H4("FACTURAS PENDIENTES"), invoices,
                invoicePagination, extra, observation);
        content.setPadding(false); content.setSpacing(false);
        dialog.add(content);
        Button confirm = new AppActionButton(ActionType.COLLECT, ButtonSize.MAIN, "Cobrar", e -> {
            if (customer.getValue() == null || date.getValue() == null || scale(received.getValue()).signum() <= 0) {
                Notification.show("Selecciona cliente, fecha e indica un total recibido mayor que cero."); return;
            }
            List<CobroClienteInput.AplicacionFactura> apps = lines.stream().filter(x -> x.amount.signum() > 0)
                    .map(x -> new CobroClienteInput.AplicacionFactura(x.invoice.cuentaCobrarId(), x.invoice.facturaId(), x.amount)).toList();
            PosPaymentDialog[] paymentHolder = new PosPaymentDialog[1];
            PosPaymentDialog payment = new PosPaymentDialog(scale(received.getValue()), terminal.monedaCodigo(),
                    pos.metodosPago(), pos.cuentasBancarias(), methods -> {
                try {
                    CobroClienteResultadoDto result = service.confirmar(new CobroClienteInput(customer.getValue().id(),
                            service.monedaBase(), terminal.sucursalId(), date.getValue(), scale(received.getValue()), reference.getValue(),
                            observation.getValue(), terminal.cajaId(), terminal.sesionCajaId(), idempotency, apps, methods));
                    paymentHolder[0].close(); dialog.close(); reloadFirst(); print.accept(result.id());
                    Notification.show("Recibo " + result.numero() + " confirmado.");
                } catch (RuntimeException ex) { paymentHolder[0].reactivarConfirmacion(); UiErrorHandler.show(ex,"No fue posible confirmar el recibo."); }
            });
            paymentHolder[0] = payment;
            payment.open();
        });
        Button cancel = new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN, "Cancelar", e -> dialog.close());
        dialog.getFooter().add(confirm, cancel);
        dialog.open();
    }

    private void loadInvoices(ClienteCobroDto customer, int page, Grid<PendingLine> grid,
                              List<PendingLine> lines, BigDecimalField received, PosDialogPagination pagination) {
        try {
            UUID currency = service.monedaBase();
            Page<FacturaPendienteCobroDto> data = service.facturasPendientes(customer.id(), currency, page);
            lines.clear(); data.getContent().forEach(x -> lines.add(new PendingLine(x)));
            grid.setItems(lines); pagination.setTotal(data.getTotalElements()); initializeReceivedWhenEmpty(lines, received);
        } catch (RuntimeException ex) { notifyError("No fue posible cargar las facturas pendientes."); }
    }

    private void openDetail(UUID id) {
        try {
            CobroClienteDto x = service.detalle(id);
            Dialog dialog = new Dialog(); dialog.setHeaderTitle("RECIBO " + x.numero()); dialog.setWidth("min(980px, 96vw)");
            Grid<CobroClienteDto.Aplicacion> apps = new Grid<>();
            apps.addColumn(CobroClienteDto.Aplicacion::factura).setHeader("Factura");
            apps.addColumn(a -> money(x.moneda(), a.monto())).setHeader("Aplicado");
            apps.addColumn(a -> money(x.moneda(), a.saldoRestante())).setHeader("Saldo restante");
            apps.setItems(x.aplicaciones()); apps.setAllRowsVisible(true);
            Grid<CobroClienteDto.Medio> methods = new Grid<>();
            methods.addColumn(CobroClienteDto.Medio::medio).setHeader("Medio");
            methods.addColumn(CobroClienteDto.Medio::destino).setHeader("Destino");
            methods.addColumn(CobroClienteDto.Medio::referencia).setHeader("Referencia");
            methods.addColumn(m -> money(x.moneda(), m.monto())).setHeader("Monto");
            methods.setItems(x.medios()); methods.setAllRowsVisible(true);
            Div summary = new Div(new Div(new Span(x.cliente() + " · " + DATE.format(x.fecha()))),
                    new Div(new Span("Recibido: " + money(x.moneda(), x.recibido()) + " · Aplicado: " + money(x.moneda(), x.aplicado())
                            + " · Disponible: " + money(x.moneda(), x.disponible()))));
            dialog.add(new VerticalLayout(summary, new H4("APLICACIONES"), apps, new H4("MEDIOS DE PAGO"), methods));
            Button pdf = new AppActionButton(ActionType.PRINT, ButtonSize.MAIN, "Imprimir recibo", e -> print.accept(x.id()));
            pdf.setVisible(hasPermission("recibos.imprimir"));
            Button apply = new AppActionButton(ActionType.APPLY_CREDIT, ButtonSize.MAIN, "Aplicar saldo disponible", e -> openApply(dialog, x));
            apply.setVisible(hasPermission("recibos.aplicar") && x.estado() != EstadoReciboCliente.VOIDED
                    && x.disponible().signum() > 0);
            Button voidButton = new AppActionButton(ActionType.VOID, ButtonSize.MAIN, "Anular recibo", e -> askVoid(dialog, x.id()));
            voidButton.setVisible(hasPermission("recibos.anular") && x.estado() != EstadoReciboCliente.VOIDED);
            dialog.getFooter().add(apply, pdf, voidButton,
                    new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN, "Cerrar", e -> dialog.close()));
            dialog.open();
        } catch (RuntimeException ex) { notifyError("No fue posible abrir el recibo."); }
    }

    private void openApply(Dialog detail, CobroClienteDto receipt) {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle("APLICAR SALDO DISPONIBLE"); dialog.setWidth("min(900px, 96vw)");
        Span available = new Span("Disponible: " + money(receipt.moneda(), receipt.disponible()));
        Grid<PendingLine> invoices = new Grid<>(); List<PendingLine> lines = new ArrayList<>();
        PosDialogPagination[] holder = new PosDialogPagination[1];
        PosDialogPagination pagination = new PosDialogPagination(request ->
                loadReceiptInvoices(receipt, request.page(), invoices, lines, holder[0]));
        holder[0] = pagination;
        invoices.addColumn(x -> x.invoice.numero()).setHeader("Factura").setAutoWidth(true);
        invoices.addColumn(x -> DATE.format(x.invoice.fecha())).setHeader("Fecha").setAutoWidth(true);
        invoices.addColumn(x -> money(x.invoice.moneda(), x.invoice.saldo())).setHeader("Saldo").setAutoWidth(true);
        invoices.addComponentColumn(x -> {
            BigDecimalField amount = new BigDecimalField(); amount.setWidth("160px"); amount.setValue(x.amount);
            amount.addValueChangeListener(e -> x.amount = scale(e.getValue())); return amount;
        }).setHeader("Aplicar").setAutoWidth(true);
        invoices.setSelectionMode(Grid.SelectionMode.MULTI);
        invoices.addSelectionListener(e -> {
            for (PendingLine line : lines) {
                if (e.getAllSelectedItems().contains(line) && line.amount.signum() == 0) line.amount = scale(line.invoice.saldo().min(receipt.disponible()));
                if (!e.getAllSelectedItems().contains(line)) line.amount = BigDecimal.ZERO.setScale(4);
            }
            invoices.getDataProvider().refreshAll();
        });
        invoices.setAllRowsVisible(true); invoices.setEmptyStateText("No hay facturas pendientes.");
        dialog.add(new VerticalLayout(available, invoices, pagination));
        Button confirm = action(VaadinIcon.CHECK, "Aplicar saldo", e -> {
            List<CobroClienteInput.AplicacionFactura> applications = lines.stream().filter(x -> x.amount.signum() > 0)
                    .map(x -> new CobroClienteInput.AplicacionFactura(x.invoice.cuentaCobrarId(), x.invoice.facturaId(), x.amount)).toList();
            try { service.aplicarDisponible(receipt.id(), new AplicarSaldoClienteInput(applications));
                dialog.close(); detail.close(); reloadFirst(); openDetail(receipt.id()); Notification.show("Saldo aplicado correctamente.");
            } catch (RuntimeException ex) { notifyError("No fue posible aplicar el saldo disponible."); }
        });
        dialog.getFooter().add(confirm, action(VaadinIcon.CLOSE, "Cancelar", e -> dialog.close()));
        loadReceiptInvoices(receipt, 0, invoices, lines, pagination); dialog.open();
    }

    private void loadReceiptInvoices(CobroClienteDto receipt, int page, Grid<PendingLine> grid,
                                     List<PendingLine> lines, PosDialogPagination pagination) {
        try {
            Page<FacturaPendienteCobroDto> data = service.facturasPendientes(receipt.clienteId(), receipt.monedaId(), page);
            lines.clear(); data.getContent().forEach(x -> lines.add(new PendingLine(x)));
            grid.setItems(lines); pagination.setTotal(data.getTotalElements());
        } catch (RuntimeException ex) { notifyError("No fue posible cargar las facturas pendientes."); }
    }

    private void askVoid(Dialog parent, UUID id) {
        Dialog reasonDialog = new Dialog(); reasonDialog.setHeaderTitle("ANULAR RECIBO");
        TextArea reason = new TextArea("Motivo de anulación"); reason.setRequiredIndicatorVisible(true); reason.setWidthFull();
        reasonDialog.add(reason);
        reasonDialog.getFooter().add(action(VaadinIcon.BAN, "Anular recibo", e -> {
            try { service.anular(id, reason.getValue()); reasonDialog.close(); parent.close(); reloadFirst(); Notification.show("Recibo anulado."); }
            catch (RuntimeException ex) { notifyError("No fue posible anular el recibo."); }
        }), action(VaadinIcon.CLOSE, "Cancelar", e -> reasonDialog.close()));
        reasonDialog.open();
    }

    private static void initializeReceivedWhenEmpty(List<PendingLine> lines, BigDecimalField field) {
        BigDecimal total = lines.stream().map(x -> x.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if ((field.getValue() == null || field.getValue().signum() == 0) && total.signum() > 0) field.setValue(scale(total));
    }

    private static Button action(VaadinIcon icon, String tooltip,
                                 com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button button = new Button(icon.create(), listener); button.setTooltipText(tooltip);
        button.getElement().setAttribute("aria-label", tooltip); button.addClassName("cc-grid-icon-button"); return button;
    }
    private static String statusLabel(EstadoReciboCliente value) { return switch (value) {
        case AVAILABLE -> "Disponible"; case PARTIALLY_APPLIED -> "Parcialmente aplicado";
        case APPLIED -> "Aplicado"; case VOIDED -> "Anulado"; }; }
    private static String money(String currency, BigDecimal amount) { return currency + " " + PosNumberFormatter.money(amount); }
    private static BigDecimal scale(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP); }
    static BigDecimal suggestedApplication(BigDecimal balance,BigDecimal receiptTotal,BigDecimal alreadyAssigned){
        BigDecimal pending=scale(balance);BigDecimal target=scale(receiptTotal);BigDecimal assigned=scale(alreadyAssigned);
        if(target.signum()<=0)return pending;
        return pending.min(target.subtract(assigned).max(BigDecimal.ZERO)).setScale(4,RoundingMode.HALF_UP);
    }
    private static void notifyError(String message) { Notification.show(message); }
    private static boolean hasPermission(String permission) {
        return com.citacloud.springboot.contacloud.app.security.TenantContext.principalActual().permisos().contains(permission);
    }
    private static final class PendingLine {
        private final FacturaPendienteCobroDto invoice; private BigDecimal amount = BigDecimal.ZERO.setScale(4);
        private PendingLine(FacturaPendienteCobroDto invoice) { this.invoice = invoice; }
    }
}
