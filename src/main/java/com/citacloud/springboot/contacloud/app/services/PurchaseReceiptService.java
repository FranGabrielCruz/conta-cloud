package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.RecepcionCompraMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseReceiptService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final Set<EstadoOrdenCompra> RECEIVABLE = EnumSet.of(
        EstadoOrdenCompra.ISSUED, EstadoOrdenCompra.PARTIALLY_RECEIVED, EstadoOrdenCompra.RECEIVED);
    private final PurchaseReceiptRepository receipts;
    private final PurchaseReceiptLineRepository receiptLines;
    private final SupplierRepository suppliers;
    private final AlmacenRepository warehouses;
    private final ProductRepository products;
    private final PurchaseOrderRepository orders;
    private final PurchaseInvoiceRepository invoices;
    private final PurchaseReceiptNumberService numbers;
    private final InventoryMovementService inventory;
    private final RecepcionCompraMapper mapper;
    private final AuditoriaService audit;

    public PurchaseReceiptService(PurchaseReceiptRepository receipts, PurchaseReceiptLineRepository receiptLines,
            SupplierRepository suppliers, AlmacenRepository warehouses, ProductRepository products,
            PurchaseOrderRepository orders, PurchaseInvoiceRepository invoices, PurchaseReceiptNumberService numbers, InventoryMovementService inventory,
            RecepcionCompraMapper mapper, AuditoriaService audit) {
        this.receipts = receipts;
        this.receiptLines = receiptLines;
        this.suppliers = suppliers;
        this.warehouses = warehouses;
        this.products = products;
        this.orders = orders;
        this.invoices = invoices;
        this.numbers = numbers;
        this.inventory = inventory;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('recepciones.ver')")
    public Page<RecepcionCompraDto> search(String text, LocalDate from, LocalDate to,
            EstadoRecepcionCompra status, int page, int size) {
        if (page < 0 || !PAGE_SIZES.contains(size)) throw new ReglaNegocioException("Paginación inválida.");
        if (from != null && to != null && to.isBefore(from))
            throw new ReglaNegocioException("La fecha final no puede ser anterior a la inicial.");
        var principal = TenantContext.principalActual();
        return receipts.buscar(principal.tenantId(), principal.empresaId(), clean(text), from, to, status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fecha"))).map(mapper::toSummaryDto);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('recepciones.ver')")
    public RecepcionCompraDto get(UUID id) { return fullDto(safe(id)); }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('recepciones.crear','recepciones.editar')")
    public ComprasCatalogosDto catalogs(UUID proveedorId) {
        var principal = TenantContext.principalActual();
        var providers = suppliers.buscar(principal.tenantId(), principal.empresaId(), "", "", true,
            PageRequest.of(0, 100, Sort.by("nombreComercial"))).stream()
            .map(item -> option(item.getId(), item.getNombreComercial())).toList();
        var stores = warehouses.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(
            principal.tenantId(), principal.empresaId()).stream()
            .map(item -> option(item.getId(), item.getCodigo() + " · " + item.getNombre())).toList();
        var available = proveedorId == null ? List.<ComprasCatalogosDto.Opcion>of()
            : orders.disponibles(principal.tenantId(), principal.empresaId(), proveedorId, RECEIVABLE).stream()
                .filter(this::hasPending).map(item -> option(item.getId(), item.getNumero())).toList();
        return new ComprasCatalogosDto(providers, List.of(), List.of(), List.of(), stores, available, null, null,
            Map.of(),Map.of());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('recepciones.crear','recepciones.editar')")
    public List<LineaRecepcionPendienteDto> pendingFromOrder(UUID orderId) {
        var principal = TenantContext.principalActual();
        OrdenCompra order = order(orderId, principal);
        List<LineaRecepcionPendienteDto> result = new ArrayList<>();
        for (var line : order.getLineas()) {
            Producto product = products.findByIdAndTenantIdAndEmpresaId(
                line.getProductoId(), principal.tenantId(), principal.empresaId()).orElse(null);
            if (!receivableProduct(product)) continue;
            BigDecimal previous = received(line.getId(), principal);
            BigDecimal pending = line.getCantidad().subtract(previous).max(BigDecimal.ZERO);
            if (pending.signum() > 0)
                result.add(new LineaRecepcionPendienteDto(line.getId(), line.getProductoId(),
                    line.getCantidad(), previous, pending));
        }
        return result;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('recepciones.crear','recepciones.editar')")
    public List<FacturaRecepcionOpcionDto> searchRegisteredInvoices(UUID supplierId, String filter, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 50) throw new ReglaNegocioException("Paginación de facturas inválida.");
        var principal = TenantContext.principalActual();
        return invoices.registradasParaRecepcion(principal.tenantId(), principal.empresaId(), supplierId, clean(filter),
            PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "fecha"))).stream()
            .map(PurchaseReceiptService::invoiceOption).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('recepciones.crear','recepciones.editar')")
    public FacturaRecepcionOpcionDto registeredInvoiceOption(UUID id) {
        return invoiceOption(registeredInvoice(id, TenantContext.principalActual()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('recepciones.crear','recepciones.editar')")
    public List<LineaRecepcionFacturaPendienteDto> pendingFromInvoice(UUID invoiceId) {
        var principal = TenantContext.principalActual();
        FacturaProveedor invoice = registeredInvoice(invoiceId, principal);
        List<LineaRecepcionFacturaPendienteDto> result = new ArrayList<>();
        for (var line : invoice.getLineas()) {
            Producto product = products.findByIdAndTenantIdAndEmpresaId(
                line.getProductoId(), principal.tenantId(), principal.empresaId()).orElse(null);
            if (!receivableProduct(product)) continue;
            BigDecimal previous = receivedInvoice(line.getId(), principal);
            BigDecimal pending = line.getCantidad().subtract(previous).max(BigDecimal.ZERO);
            if (pending.signum() <= 0) continue;
            UUID orderLineId = matchingOrderLine(invoice, line.getProductoId());
            result.add(new LineaRecepcionFacturaPendienteDto(line.getId(), orderLineId, line.getProductoId(),
                line.getCantidad(), previous, pending));
        }
        return result;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('recepciones.crear','recepciones.editar')")
    public LineaRecepcionFacturaPendienteDto invoiceLineForProduct(UUID invoiceId, UUID productId) {
        var principal = TenantContext.principalActual();
        FacturaProveedor invoice = registeredInvoice(invoiceId, principal);
        return invoice.getLineas().stream().filter(line -> Objects.equals(line.getProductoId(), productId)).findFirst()
            .map(line -> {
                BigDecimal previous = receivedInvoice(line.getId(), principal);
                return new LineaRecepcionFacturaPendienteDto(line.getId(), matchingOrderLine(invoice, productId),
                    productId, line.getCantidad(), previous, line.getCantidad().subtract(previous).max(BigDecimal.ZERO));
            }).orElse(null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('recepciones.crear','recepciones.editar')")
    public LineaRecepcionPendienteDto orderLineForProduct(UUID orderId, UUID productId) {
        var principal = TenantContext.principalActual();
        return order(orderId, principal).getLineas().stream()
            .filter(line -> Objects.equals(line.getProductoId(), productId))
            .findFirst()
            .map(line -> {
                BigDecimal previous = received(line.getId(), principal);
                return new LineaRecepcionPendienteDto(line.getId(), line.getProductoId(), line.getCantidad(),
                    previous, line.getCantidad().subtract(previous).max(BigDecimal.ZERO));
            }).orElse(null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('recepciones.crear','recepciones.editar')")
    public List<OrdenCompraCatalogosDto.ProductoOpcion> searchProducts(String filter, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 50)
            throw new ReglaNegocioException("Paginación de productos inválida.");
        var principal = TenantContext.principalActual();
        return products.buscar(principal.tenantId(), principal.empresaId(), clean(filter), null,
            TipoProducto.PRODUCT, true, PageRequest.of(offset / limit, limit, Sort.by("nombre"))).stream()
            .filter(PurchaseReceiptService::receivableProduct).map(PurchaseReceiptService::productOption).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('recepciones.crear','recepciones.editar')")
    public OrdenCompraCatalogosDto.ProductoOpcion productOption(UUID id) {
        var principal = TenantContext.principalActual();
        Producto product = product(id, principal);
        if (!receivableProduct(product)) throw new ReglaNegocioException("El producto no está disponible para recepción.");
        return productOption(product);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('recepciones.crear')")
    public RecepcionCompraDto create(RecepcionCompraInput input) {
        var principal = TenantContext.principalActual();
        RecepcionCompra existing = existing(input, principal);
        if (existing != null) return fullDto(existing);
        Validated validated = validate(input, principal);
        RecepcionCompra receipt = mapper.toEntity(normalizedInput(input, validated), principal.tenantId(), principal.empresaId(),
            numbers.next(principal.tenantId(), principal.empresaId()), principal.usuarioId());
        receipt.establecerMotivoDiferencia(optional(input.motivoDiferencia(), 1000), principal.usuarioId());
        apply(receipt, validated, principal);
        receipt = receipts.saveAndFlush(receipt);
        audit.registrar("PURCHASE_RECEIPT_CREATED", "RecepcionCompra", receipt.getId(), detail(receipt, null));
        return fullDto(receipt);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('recepciones.crear') and hasAuthority('recepciones.confirmar')")
    public RecepcionCompraDto createAndConfirm(RecepcionCompraInput input) {
        var principal = TenantContext.principalActual();
        RecepcionCompra existing = existing(input, principal);
        if (existing != null) {
            if (existing.getEstado() == EstadoRecepcionCompra.CONFIRMED) return fullDto(existing);
            draft(existing);
            return confirmNew(locked(existing.getId()), principal);
        }
        Validated validated = validate(input, principal);
        RecepcionCompra receipt = mapper.toEntity(normalizedInput(input, validated), principal.tenantId(), principal.empresaId(),
            numbers.next(principal.tenantId(), principal.empresaId()), principal.usuarioId());
        receipt.establecerMotivoDiferencia(optional(input.motivoDiferencia(), 1000), principal.usuarioId());
        apply(receipt, validated, principal);
        receipt = receipts.saveAndFlush(receipt);
        audit.registrar("PURCHASE_RECEIPT_CREATED", "RecepcionCompra", receipt.getId(), detail(receipt, null));
        return confirmNew(receipt, principal);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('recepciones.editar')")
    public RecepcionCompraDto update(UUID id, RecepcionCompraInput input) {
        RecepcionCompra receipt = safe(id);
        draft(receipt);
        expected(receipt, input == null ? null : input.version());
        var principal = TenantContext.principalActual();
        Validated validated = validate(input, principal);
        RecepcionCompraInput clean = normalizedInput(input, validated);
        flushExistingLines(receipt);
        receipt.actualizar(clean.proveedorId(), clean.ordenCompraId(), clean.facturaProveedorId(), clean.almacenId(), clean.fecha(),
            clean.referencia(), clean.notas(), clean.motivoDiferencia(), principal.usuarioId());
        apply(receipt, validated, principal);
        receipt = receipts.saveAndFlush(receipt);
        audit.registrar("PURCHASE_RECEIPT_UPDATED", "RecepcionCompra", receipt.getId(), detail(receipt, null));
        return fullDto(receipt);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('recepciones.editar') and hasAuthority('recepciones.confirmar')")
    public RecepcionCompraDto updateAndConfirm(UUID id, RecepcionCompraInput input) {
        RecepcionCompra receipt = locked(id);
        draft(receipt);
        expected(receipt, input == null ? null : input.version());
        var principal = TenantContext.principalActual();
        Validated validated = validate(input, principal);
        RecepcionCompraInput clean = normalizedInput(input, validated);
        flushExistingLines(receipt);
        receipt.actualizar(clean.proveedorId(), clean.ordenCompraId(), clean.facturaProveedorId(), clean.almacenId(), clean.fecha(),
            clean.referencia(), clean.notas(), clean.motivoDiferencia(), principal.usuarioId());
        apply(receipt, validated, principal);
        receipt = receipts.saveAndFlush(receipt);
        audit.registrar("PURCHASE_RECEIPT_UPDATED", "RecepcionCompra", receipt.getId(), detail(receipt, null));
        return confirmNew(receipt, principal);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('recepciones.confirmar')")
    public RecepcionCompraDto confirm(UUID id, long version) {
        RecepcionCompra receipt = locked(id);
        if (receipt.getEstado() == EstadoRecepcionCompra.CONFIRMED) return fullDto(receipt);
        expected(receipt, version);
        draft(receipt);
        return confirmNew(receipt, TenantContext.principalActual());
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('recepciones.anular')")
    public RecepcionCompraDto voidReceipt(UUID id, long version, String reason) {
        RecepcionCompra receipt = locked(id);
        if (receipt.getEstado() == EstadoRecepcionCompra.VOIDED) return fullDto(receipt);
        expected(receipt, version);
        String motive = required(reason, 500, "El motivo de anulación es obligatorio.");
        var principal = TenantContext.principalActual();
        OrdenCompra order = receipt.getOrdenCompraId() == null ? null : lockOrder(receipt, principal);
        if (receipt.getEstado() == EstadoRecepcionCompra.CONFIRMED) {
            for (var movement : inventory.receiptEntries(receipt))
                inventory.reverse(receipt, product(movement.getProductoId(), principal), movement, principal.usuarioId());
        }
        receipt.anular(motive, principal.usuarioId());
        receipt = receipts.saveAndFlush(receipt);
        if (order != null) updateOrder(order, principal);
        audit.registrar("PURCHASE_RECEIPT_VOIDED", "RecepcionCompra", receipt.getId(), detail(receipt, null));
        return fullDto(receipt);
    }

    private Validated validate(RecepcionCompraInput input, TenantPrincipal principal) {
        if (input == null) throw new ReglaNegocioException("Los datos de la recepción son obligatorios.");
        if (input.fecha() == null) throw new ReglaNegocioException("La fecha de recepción es obligatoria.");
        Proveedor supplier = suppliers.findByIdAndTenantIdAndEmpresaId(input.proveedorId(), principal.tenantId(), principal.empresaId())
            .filter(Proveedor::isActivo).orElseThrow(() -> new ReglaNegocioException("El proveedor seleccionado no está disponible."));
        Almacen warehouse = warehouses.findByIdAndTenantIdAndEmpresaId(input.almacenId(), principal.tenantId(), principal.empresaId())
            .filter(Almacen::isActivo).orElseThrow(() -> new ReglaNegocioException("El almacén seleccionado no está disponible."));
        FacturaProveedor invoice = input.facturaProveedorId() == null ? null : registeredInvoice(input.facturaProveedorId(), principal);
        if (invoice != null && !Objects.equals(invoice.getProveedorId(), supplier.getId()))
            throw new ReglaNegocioException("La factura seleccionada no pertenece al proveedor de la recepción.");
        UUID orderId = input.ordenCompraId();
        if (invoice != null && invoice.getOrdenCompraId() != null) {
            if (orderId != null && !Objects.equals(orderId, invoice.getOrdenCompraId()))
                throw new ReglaNegocioException("La orden seleccionada no corresponde a la factura de proveedor.");
            orderId = invoice.getOrdenCompraId();
        }
        OrdenCompra order = orderId == null ? null
            : orders.findByIdAndTenantIdAndEmpresaId(orderId, principal.tenantId(), principal.empresaId())
                .filter(item -> item.getProveedorId().equals(supplier.getId()) && RECEIVABLE.contains(item.getEstado()))
                .orElseThrow(() -> new ReglaNegocioException("La orden seleccionada no está disponible para ese proveedor."));
        if (input.lineas() == null || input.lineas().isEmpty())
            throw new ReglaNegocioException("Agrega al menos una línea a la recepción.");

        Map<UUID, LineaOrdenCompra> orderLines = order == null ? Map.of()
            : order.getLineas().stream().collect(Collectors.toMap(LineaOrdenCompra::getId, Function.identity()));
        Map<UUID, LineaFacturaProveedor> invoiceLines = invoice == null ? Map.of()
            : invoice.getLineas().stream().collect(Collectors.toMap(LineaFacturaProveedor::getId, Function.identity()));
        Set<UUID> seen = new HashSet<>();
        List<ValidLine> lines = new ArrayList<>();
        for (var inputLine : input.lineas()) {
            if (inputLine == null || inputLine.productoId() == null || inputLine.cantidad() == null
                    || inputLine.cantidad().signum() <= 0)
                throw new ReglaNegocioException("Cada línea debe tener producto y una cantidad mayor que cero.");
            if (!seen.add(inputLine.productoId())) throw new ReglaNegocioException("El producto ya existe en la recepción.");
            Producto product = product(inputLine.productoId(), principal);
            if (!receivableProduct(product)) throw new ReglaNegocioException("Solo se pueden recibir productos activos; los servicios no generan recepción física.");
            LineaOrdenCompra orderLine = resolveOrderLine(inputLine, order, orderLines);
            LineaFacturaProveedor invoiceLine = resolveInvoiceLine(inputLine, invoice, invoiceLines);
            BigDecimal quantity = inputLine.cantidad().setScale(4, RoundingMode.HALF_UP);
            TipoDiferenciaRecepcion difference = classify(order, orderLine, invoice, invoiceLine, quantity, principal);
            lines.add(new ValidLine(product, orderLine, invoiceLine, quantity, difference));
        }
        return new Validated(supplier, warehouse, order, invoice, lines);
    }

    private LineaOrdenCompra resolveOrderLine(LineaRecepcionCompraInput inputLine, OrdenCompra order,
            Map<UUID, LineaOrdenCompra> orderLines) {
        if (order == null) {
            if (inputLine.lineaOrdenId() != null) throw new ReglaNegocioException("La línea de orden no corresponde a una recepción sin orden.");
            return null;
        }
        if (inputLine.lineaOrdenId() != null) {
            LineaOrdenCompra line = orderLines.get(inputLine.lineaOrdenId());
            if (line == null || !Objects.equals(line.getProductoId(), inputLine.productoId()))
                throw new ReglaNegocioException("La línea no pertenece a la orden seleccionada.");
            return line;
        }
        return order.getLineas().stream().filter(line -> Objects.equals(line.getProductoId(), inputLine.productoId())).findFirst().orElse(null);
    }

    private LineaFacturaProveedor resolveInvoiceLine(LineaRecepcionCompraInput inputLine, FacturaProveedor invoice,
            Map<UUID, LineaFacturaProveedor> invoiceLines) {
        if (invoice == null) {
            if (inputLine.lineaFacturaId() != null) throw new ReglaNegocioException("La línea de factura no corresponde a una recepción sin factura.");
            return null;
        }
        if (inputLine.lineaFacturaId() != null) {
            LineaFacturaProveedor line = invoiceLines.get(inputLine.lineaFacturaId());
            if (line == null || !Objects.equals(line.getProductoId(), inputLine.productoId()))
                throw new ReglaNegocioException("La línea no pertenece a la factura seleccionada.");
            return line;
        }
        return invoice.getLineas().stream().filter(line -> Objects.equals(line.getProductoId(), inputLine.productoId())).findFirst().orElse(null);
    }

    private TipoDiferenciaRecepcion classify(OrdenCompra order, LineaOrdenCompra orderLine, FacturaProveedor invoice,
            LineaFacturaProveedor invoiceLine, BigDecimal quantity, TenantPrincipal principal) {
        if (invoice != null) {
            if (invoiceLine == null) return TipoDiferenciaRecepcion.UNINVOICED_PRODUCT;
            return receivedInvoice(invoiceLine.getId(), principal).add(quantity).compareTo(invoiceLine.getCantidad()) > 0
                ? TipoDiferenciaRecepcion.OVER_INVOICED_QUANTITY : TipoDiferenciaRecepcion.NONE;
        }
        if (order != null && orderLine == null) return TipoDiferenciaRecepcion.UNORDERED_PRODUCT;
        if (orderLine != null && received(orderLine.getId(), principal).add(quantity).compareTo(orderLine.getCantidad()) > 0)
            return TipoDiferenciaRecepcion.OVER_ORDERED_QUANTITY;
        return TipoDiferenciaRecepcion.NONE;
    }

    private DifferenceSummary validateConfirmation(RecepcionCompra receipt, OrdenCompra order,
            FacturaProveedor invoice, TenantPrincipal principal) {
        Map<UUID, LineaOrdenCompra> orderLines = order == null ? Map.of()
            : order.getLineas().stream().collect(Collectors.toMap(LineaOrdenCompra::getId, Function.identity()));
        Map<UUID, LineaFacturaProveedor> invoiceLines = invoice == null ? Map.of()
            : invoice.getLineas().stream().collect(Collectors.toMap(LineaFacturaProveedor::getId, Function.identity()));
        List<String> differences = new ArrayList<>();
        for (var line : receipt.getLineas()) {
            Producto product = product(line.getProductoId(), principal);
            if (!receivableProduct(product)) throw new ReglaNegocioException("La recepción contiene un producto que ya no está disponible.");
            LineaOrdenCompra orderLine = line.getLineaOrdenId() == null ? null : orderLines.get(line.getLineaOrdenId());
            LineaFacturaProveedor invoiceLine = line.getLineaFacturaId() == null ? null : invoiceLines.get(line.getLineaFacturaId());
            if (line.getLineaOrdenId() != null && (orderLine == null || !Objects.equals(orderLine.getProductoId(), line.getProductoId())))
                throw new ReglaNegocioException("La recepción contiene una línea ajena a la orden.");
            if (line.getLineaFacturaId() != null && (invoiceLine == null || !Objects.equals(invoiceLine.getProductoId(), line.getProductoId())))
                throw new ReglaNegocioException("La recepción contiene una línea ajena a la factura.");
            TipoDiferenciaRecepcion difference = classify(order, orderLine, invoice, invoiceLine, line.getCantidad(), principal);
            OrigenLineaRecepcion origin = invoiceLine != null ? OrigenLineaRecepcion.PURCHASE_INVOICE
                : orderLine != null ? OrigenLineaRecepcion.PURCHASE_ORDER : OrigenLineaRecepcion.MANUAL;
            line.reclasificar(origin, difference);
            if (invoice == null && order != null && orderLine == null) differences.add(product.getNombre() + ": fuera de la orden");
            if (invoice != null && invoiceLine == null) differences.add(product.getNombre() + ": fuera de la factura");
            if (invoice == null && orderLine != null) {
                BigDecimal excess = received(orderLine.getId(), principal).add(line.getCantidad()).subtract(orderLine.getCantidad()).max(BigDecimal.ZERO);
                if (excess.signum() > 0) differences.add(product.getNombre() + ": exceso sobre orden de " + excess.toPlainString());
            }
            if (invoiceLine != null) {
                BigDecimal excess = receivedInvoice(invoiceLine.getId(), principal).add(line.getCantidad()).subtract(invoiceLine.getCantidad()).max(BigDecimal.ZERO);
                if (excess.signum() > 0) differences.add(product.getNombre() + ": exceso sobre factura de " + excess.toPlainString());
            }
        }
        if (!differences.isEmpty()) {
            if (!principal.permisos().contains("recepciones.recibir_diferencias"))
                throw new ReglaNegocioException("La recepción contiene diferencias y requiere autorización para ser confirmada.");
            required(receipt.getMotivoDiferencia(), 1000, "El motivo de la diferencia es obligatorio para confirmar la recepción.");
        }
        return new DifferenceSummary(differences);
    }

    private RecepcionCompraDto confirmNew(RecepcionCompra receipt, TenantPrincipal principal) {
        if (receipt.getEstado() == EstadoRecepcionCompra.CONFIRMED) return fullDto(receipt);
        if (receipt.getLineas().isEmpty()) throw new ReglaNegocioException("La recepción debe tener al menos una línea.");
        OrdenCompra order = receipt.getOrdenCompraId() == null ? null : lockOrder(receipt, principal);
        FacturaProveedor invoice = receipt.getFacturaProveedorId() == null ? null : lockInvoice(receipt, principal);
        DifferenceSummary summary = validateConfirmation(receipt, order, invoice, principal);
        for (var line : receipt.getLineas()) {
            Producto product = product(line.getProductoId(), principal);
            inventory.receive(receipt, line, product, principal.usuarioId());
        }
        receipt.confirmar(principal.usuarioId());
        receipt = receipts.saveAndFlush(receipt);
        if (order != null) updateOrder(order, principal);
        audit.registrar("PURCHASE_RECEIPT_CONFIRMED", "RecepcionCompra", receipt.getId(), detail(receipt, summary));
        if (summary.hasDifferences())
            audit.registrar("PURCHASE_RECEIPT_DIFFERENCE_CONFIRMED", "RecepcionCompra", receipt.getId(), detail(receipt, summary));
        return fullDto(receipt);
    }

    private void apply(RecepcionCompra receipt, Validated validated, TenantPrincipal principal) {
        List<LineaRecepcionCompra> result = new ArrayList<>();
        int number = 1;
        for (var item : validated.lines()) {
            Producto product = item.product();
            var unit = product.getUnidadMedida();
            UUID orderLineId = item.orderLine() == null ? null : item.orderLine().getId();
            UUID invoiceLineId = item.invoiceLine() == null ? null : item.invoiceLine().getId();
            OrigenLineaRecepcion origin = invoiceLineId != null ? OrigenLineaRecepcion.PURCHASE_INVOICE
                : orderLineId != null ? OrigenLineaRecepcion.PURCHASE_ORDER : OrigenLineaRecepcion.MANUAL;
            result.add(new LineaRecepcionCompra(principal.tenantId(), principal.empresaId(), orderLineId, invoiceLineId,
                product.getId(), product.getCodigo(), product.getNombre(), unit == null ? null : unit.getNombre(),
                item.quantity(), origin, item.difference(), number++));
        }
        receipt.reemplazarLineas(result);
    }

    private void flushExistingLines(RecepcionCompra receipt) {
        if (receipt.getId() == null || receipt.getLineas().isEmpty()) return;
        receipt.reemplazarLineas(List.of());
        receipts.flush();
    }

    private void updateOrder(OrdenCompra order, TenantPrincipal principal) {
        boolean any = false;
        boolean complete = true;
        for (var line : order.getLineas()) {
            Producto product = products.findByIdAndTenantIdAndEmpresaId(
                line.getProductoId(), principal.tenantId(), principal.empresaId()).orElse(null);
            if (!receivableProduct(product)) continue;
            BigDecimal received = received(line.getId(), principal);
            if (received.signum() > 0) any = true;
            if (received.compareTo(line.getCantidad()) < 0) complete = false;
        }
        order.restaurarEstadoRecepcion(any, complete, principal.usuarioId());
        orders.save(order);
    }

    private boolean hasPending(OrdenCompra order) {
        var principal = TenantContext.principalActual();
        for (var line : order.getLineas()) {
            Producto product = products.findByIdAndTenantIdAndEmpresaId(
                line.getProductoId(), principal.tenantId(), principal.empresaId()).orElse(null);
            if (receivableProduct(product) && received(line.getId(), principal).compareTo(line.getCantidad()) < 0) return true;
        }
        return false;
    }

    private RecepcionCompraDto fullDto(RecepcionCompra receipt) {
        var principal = TenantContext.principalActual();
        Map<UUID, BigDecimal> ordered = new HashMap<>(), priorOrder = new HashMap<>();
        Map<UUID, BigDecimal> invoiced = new HashMap<>(), priorInvoice = new HashMap<>();
        if (receipt.getOrdenCompra() != null) {
            for (var line : receipt.getOrdenCompra().getLineas()) {
                ordered.put(line.getId(), line.getCantidad());
                BigDecimal received = received(line.getId(), principal);
                if (receipt.getEstado() == EstadoRecepcionCompra.CONFIRMED) {
                    BigDecimal own = receipt.getLineas().stream()
                        .filter(item -> Objects.equals(item.getLineaOrdenId(), line.getId()))
                        .map(LineaRecepcionCompra::getCantidad).findFirst().orElse(BigDecimal.ZERO);
                    received = received.subtract(own);
                }
                priorOrder.put(line.getId(), received.max(BigDecimal.ZERO));
            }
        }
        if (receipt.getFacturaProveedor() != null) {
            for (var line : receipt.getFacturaProveedor().getLineas()) {
                invoiced.put(line.getId(), line.getCantidad());
                BigDecimal received = receivedInvoice(line.getId(), principal);
                if (receipt.getEstado() == EstadoRecepcionCompra.CONFIRMED) {
                    BigDecimal own = receipt.getLineas().stream()
                        .filter(item -> Objects.equals(item.getLineaFacturaId(), line.getId()))
                        .map(LineaRecepcionCompra::getCantidad).findFirst().orElse(BigDecimal.ZERO);
                    received = received.subtract(own);
                }
                priorInvoice.put(line.getId(), received.max(BigDecimal.ZERO));
            }
        }
        return mapper.toDto(receipt, ordered, priorOrder, invoiced, priorInvoice);
    }

    private OrdenCompra order(UUID id, TenantPrincipal principal) {
        return orders.findByIdAndTenantIdAndEmpresaId(id, principal.tenantId(), principal.empresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Orden de compra no encontrada."));
    }
    private OrdenCompra lockOrder(RecepcionCompra receipt, TenantPrincipal principal) {
        return orders.bloquear(receipt.getOrdenCompraId(), principal.tenantId(), principal.empresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Orden de compra no encontrada."));
    }
    private FacturaProveedor registeredInvoice(UUID id, TenantPrincipal principal) {
        return invoices.findByIdAndTenantIdAndEmpresaId(id, principal.tenantId(), principal.empresaId())
            .filter(invoice -> invoice.getEstado() == EstadoFacturaProveedor.REGISTERED)
            .orElseThrow(() -> new ReglaNegocioException("La factura de proveedor no está registrada o no está disponible."));
    }
    private FacturaProveedor lockInvoice(RecepcionCompra receipt, TenantPrincipal principal) {
        FacturaProveedor invoice = invoices.bloquear(receipt.getFacturaProveedorId(), principal.tenantId(), principal.empresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Factura de proveedor no encontrada."));
        if (invoice.getEstado() != EstadoFacturaProveedor.REGISTERED)
            throw new ReglaNegocioException("La factura de proveedor debe estar registrada para confirmar la recepción.");
        if (!Objects.equals(invoice.getProveedorId(), receipt.getProveedorId()))
            throw new ReglaNegocioException("La factura no pertenece al proveedor de la recepción.");
        if (invoice.getOrdenCompraId() != null && !Objects.equals(invoice.getOrdenCompraId(), receipt.getOrdenCompraId()))
            throw new ReglaNegocioException("La orden de la recepción no corresponde a la factura.");
        return invoice;
    }
    private static UUID matchingOrderLine(FacturaProveedor invoice, UUID productId) {
        if (invoice.getOrdenCompra() == null) return null;
        return invoice.getOrdenCompra().getLineas().stream().filter(line -> Objects.equals(line.getProductoId(), productId))
            .map(LineaOrdenCompra::getId).findFirst().orElse(null);
    }
    private BigDecimal received(UUID lineId, TenantPrincipal principal) {
        return receiptLines.recibidoConfirmado(lineId, principal.tenantId(), principal.empresaId());
    }
    private BigDecimal receivedInvoice(UUID lineId, TenantPrincipal principal) {
        return receiptLines.recibidoConfirmadoFactura(lineId, principal.tenantId(), principal.empresaId());
    }
    private Producto product(UUID id, TenantPrincipal principal) {
        return products.findByIdAndTenantIdAndEmpresaId(id, principal.tenantId(), principal.empresaId())
            .orElseThrow(() -> new ReglaNegocioException("El producto seleccionado no está disponible."));
    }
    private static boolean receivableProduct(Producto product) {
        return product != null && product.isActivo() && product.getTipo() == TipoProducto.PRODUCT;
    }
    private static OrdenCompraCatalogosDto.ProductoOpcion productOption(Producto product) {
        return new OrdenCompraCatalogosDto.ProductoOpcion(product.getId(), product.getCodigo(), product.getNombre(),
            product.getUnidadMedida() == null ? null : product.getUnidadMedida().getNombre(), product.getCostoCompra(),
            product.getMonedaId(), product.getImpuestoCompraId(),
            product.getImpuestoCompra() == null ? null : product.getImpuestoCompra().getNombre(),
            product.getImpuestoCompra() == null ? null : product.getImpuestoCompra().getPorcentaje());
    }
    private static FacturaRecepcionOpcionDto invoiceOption(FacturaProveedor invoice) {
        String currency = invoice.getMoneda() == null ? "" : invoice.getMoneda().getCodigoIso() + " ";
        return new FacturaRecepcionOpcionDto(invoice.getId(), invoice.getProveedorId(), invoice.getOrdenCompraId(),
            invoice.getNumeroProveedor(), invoice.getNumeroProveedor() + " · " + DATE_FORMAT.format(invoice.getFecha())
                + " · " + currency + String.format(Locale.US, "%,.2f", invoice.getTotal()));
    }
    private RecepcionCompra safe(UUID id) {
        var principal = TenantContext.principalActual();
        return receipts.findByIdAndTenantIdAndEmpresaId(id, principal.tenantId(), principal.empresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Recepción no encontrada."));
    }
    private RecepcionCompra locked(UUID id) {
        var principal = TenantContext.principalActual();
        return receipts.bloquear(id, principal.tenantId(), principal.empresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Recepción no encontrada."));
    }
    private static void draft(RecepcionCompra receipt) {
        if (receipt.getEstado() != EstadoRecepcionCompra.DRAFT)
            throw new ReglaNegocioException("Solo se pueden editar recepciones en borrador.");
    }
    private static void expected(RecepcionCompra receipt, Long version) {
        if (version == null || receipt.getVersion() != version)
            throw new ReglaNegocioException("La recepción fue modificada por otro usuario. Actualiza la pantalla.");
    }
    private static RecepcionCompraInput normalizedInput(RecepcionCompraInput input, Validated validated) {
        return new RecepcionCompraInput(input.proveedorId(), validated.order() == null ? null : validated.order().getId(),
            validated.invoice() == null ? null : validated.invoice().getId(), input.almacenId(), input.fecha(),
            optional(input.referencia(), 100), optional(input.notas(), 1000), optional(input.motivoDiferencia(), 1000),
            input.lineas(), input.version(), input.claveIdempotencia());
    }
    private RecepcionCompra existing(RecepcionCompraInput input, TenantPrincipal principal) {
        if (input == null || input.claveIdempotencia() == null) return null;
        return receipts.findByTenantIdAndEmpresaIdAndClaveIdempotencia(
            principal.tenantId(), principal.empresaId(), input.claveIdempotencia()).orElse(null);
    }
    private static ComprasCatalogosDto.Opcion option(UUID id, String name) { return new ComprasCatalogosDto.Opcion(id, name); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String optional(String value, int max) {
        String clean = clean(value);
        if (clean.isEmpty()) return null;
        if (clean.length() > max) throw new ReglaNegocioException("El valor excede " + max + " caracteres.");
        return clean;
    }
    private static String required(String value, int max, String message) {
        String clean = clean(value);
        if (clean.isEmpty()) throw new ReglaNegocioException(message);
        if (clean.length() > max) throw new ReglaNegocioException("El valor excede " + max + " caracteres.");
        return clean;
    }
    private static String detail(RecepcionCompra receipt, DifferenceSummary summary) {
        String differences = summary == null ? "" : String.join("; ", summary.descriptions());
        return "{\"numero\":\"" + json(receipt.getNumero()) + "\",\"estado\":\"" + receipt.getEstado()
            + "\",\"ordenCompraId\":" + jsonNullable(receipt.getOrdenCompraId())
            + ",\"facturaProveedorId\":" + jsonNullable(receipt.getFacturaProveedorId())
            + ",\"motivoDiferencia\":" + jsonNullable(receipt.getMotivoDiferencia())
            + ",\"diferencias\":\"" + json(differences) + "\"}";
    }
    private static String jsonNullable(Object value) { return value == null ? "null" : "\"" + json(String.valueOf(value)) + "\""; }
    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }

    private record ValidLine(Producto product, LineaOrdenCompra orderLine, LineaFacturaProveedor invoiceLine, BigDecimal quantity,
                             TipoDiferenciaRecepcion difference) {}
    private record Validated(Proveedor supplier, Almacen warehouse, OrdenCompra order, FacturaProveedor invoice,
                             List<ValidLine> lines) {}
    private record DifferenceSummary(List<String> descriptions) {
        boolean hasDifferences() { return !descriptions.isEmpty(); }
    }
}
