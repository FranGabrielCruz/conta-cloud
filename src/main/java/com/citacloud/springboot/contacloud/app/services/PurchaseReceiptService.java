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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseReceiptService {
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final Set<EstadoOrdenCompra> RECEIVABLE = EnumSet.of(
        EstadoOrdenCompra.ISSUED, EstadoOrdenCompra.PARTIALLY_RECEIVED, EstadoOrdenCompra.RECEIVED);
    private final PurchaseReceiptRepository receipts;
    private final PurchaseReceiptLineRepository receiptLines;
    private final SupplierRepository suppliers;
    private final AlmacenRepository warehouses;
    private final ProductRepository products;
    private final PurchaseOrderRepository orders;
    private final PurchaseReceiptNumberService numbers;
    private final InventoryMovementService inventory;
    private final RecepcionCompraMapper mapper;
    private final AuditoriaService audit;

    public PurchaseReceiptService(PurchaseReceiptRepository receipts, PurchaseReceiptLineRepository receiptLines,
            SupplierRepository suppliers, AlmacenRepository warehouses, ProductRepository products,
            PurchaseOrderRepository orders, PurchaseReceiptNumberService numbers, InventoryMovementService inventory,
            RecepcionCompraMapper mapper, AuditoriaService audit) {
        this.receipts = receipts;
        this.receiptLines = receiptLines;
        this.suppliers = suppliers;
        this.warehouses = warehouses;
        this.products = products;
        this.orders = orders;
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
        return new ComprasCatalogosDto(providers, List.of(), List.of(), List.of(), stores, available, null);
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
        RecepcionCompra receipt = mapper.toEntity(cleanInput(input), principal.tenantId(), principal.empresaId(),
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
        RecepcionCompra receipt = mapper.toEntity(cleanInput(input), principal.tenantId(), principal.empresaId(),
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
        RecepcionCompraInput clean = cleanInput(input);
        flushExistingLines(receipt);
        receipt.actualizar(clean.proveedorId(), clean.ordenCompraId(), clean.almacenId(), clean.fecha(),
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
        RecepcionCompraInput clean = cleanInput(input);
        flushExistingLines(receipt);
        receipt.actualizar(clean.proveedorId(), clean.ordenCompraId(), clean.almacenId(), clean.fecha(),
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
        OrdenCompra order = input.ordenCompraId() == null ? null
            : orders.findByIdAndTenantIdAndEmpresaId(input.ordenCompraId(), principal.tenantId(), principal.empresaId())
                .filter(item -> item.getProveedorId().equals(supplier.getId()) && RECEIVABLE.contains(item.getEstado()))
                .orElseThrow(() -> new ReglaNegocioException("La orden seleccionada no está disponible para ese proveedor."));
        if (input.lineas() == null || input.lineas().isEmpty())
            throw new ReglaNegocioException("Agrega al menos una línea a la recepción.");

        Map<UUID, LineaOrdenCompra> orderLines = order == null ? Map.of()
            : order.getLineas().stream().collect(Collectors.toMap(LineaOrdenCompra::getId, Function.identity()));
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
            BigDecimal quantity = inputLine.cantidad().setScale(4, RoundingMode.HALF_UP);
            TipoDiferenciaRecepcion difference = classify(order, orderLine, quantity, principal);
            lines.add(new ValidLine(product, orderLine, quantity, difference));
        }
        return new Validated(supplier, warehouse, order, lines);
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
        return order.getLineas().stream().filter(line -> Objects.equals(line.getProductoId(), inputLine.productoId()))
            .findFirst().orElse(null);
    }

    private TipoDiferenciaRecepcion classify(OrdenCompra order, LineaOrdenCompra orderLine,
            BigDecimal quantity, TenantPrincipal principal) {
        if (order == null) return TipoDiferenciaRecepcion.NONE;
        if (orderLine == null) return TipoDiferenciaRecepcion.UNORDERED_PRODUCT;
        return received(orderLine.getId(), principal).add(quantity).compareTo(orderLine.getCantidad()) > 0
            ? TipoDiferenciaRecepcion.OVER_RECEIPT : TipoDiferenciaRecepcion.NONE;
    }

    private DifferenceSummary validateConfirmation(RecepcionCompra receipt, OrdenCompra order, TenantPrincipal principal) {
        Map<UUID, LineaOrdenCompra> orderLines = order == null ? Map.of()
            : order.getLineas().stream().collect(Collectors.toMap(LineaOrdenCompra::getId, Function.identity()));
        List<String> differences = new ArrayList<>();
        for (var line : receipt.getLineas()) {
            Producto product = product(line.getProductoId(), principal);
            if (!receivableProduct(product)) throw new ReglaNegocioException("La recepción contiene un producto que ya no está disponible.");
            LineaOrdenCompra orderLine = line.getLineaOrdenId() == null ? null : orderLines.get(line.getLineaOrdenId());
            if (line.getLineaOrdenId() != null && (orderLine == null || !Objects.equals(orderLine.getProductoId(), line.getProductoId())))
                throw new ReglaNegocioException("La recepción contiene una línea ajena a la orden.");
            TipoDiferenciaRecepcion difference = classify(order, orderLine, line.getCantidad(), principal);
            line.reclasificar(orderLine == null ? OrigenLineaRecepcion.MANUAL : OrigenLineaRecepcion.ORDER_LINE, difference);
            if (difference == TipoDiferenciaRecepcion.UNORDERED_PRODUCT)
                differences.add(product.getNombre() + ": " + line.getCantidad().toPlainString() + " fuera de la orden");
            if (difference == TipoDiferenciaRecepcion.OVER_RECEIPT) {
                BigDecimal excess = received(orderLine.getId(), principal).add(line.getCantidad())
                    .subtract(orderLine.getCantidad()).max(BigDecimal.ZERO);
                differences.add(product.getNombre() + ": exceso de " + excess.toPlainString());
            }
        }
        if (!differences.isEmpty()) {
            required(receipt.getMotivoDiferencia(), 1000, "El motivo de la diferencia es obligatorio para confirmar la recepción.");
        }
        return new DifferenceSummary(differences);
    }

    private RecepcionCompraDto confirmNew(RecepcionCompra receipt, TenantPrincipal principal) {
        if (receipt.getEstado() == EstadoRecepcionCompra.CONFIRMED) return fullDto(receipt);
        if (receipt.getLineas().isEmpty()) throw new ReglaNegocioException("La recepción debe tener al menos una línea.");
        OrdenCompra order = receipt.getOrdenCompraId() == null ? null : lockOrder(receipt, principal);
        DifferenceSummary summary = validateConfirmation(receipt, order, principal);
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
            result.add(new LineaRecepcionCompra(principal.tenantId(), principal.empresaId(), orderLineId,
                product.getId(), product.getCodigo(), product.getNombre(), unit == null ? null : unit.getNombre(),
                item.quantity(), orderLineId == null ? OrigenLineaRecepcion.MANUAL : OrigenLineaRecepcion.ORDER_LINE,
                item.difference(), number++));
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
        Map<UUID, BigDecimal> ordered = new HashMap<>();
        Map<UUID, BigDecimal> prior = new HashMap<>();
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
                prior.put(line.getId(), received.max(BigDecimal.ZERO));
            }
        }
        return mapper.toDto(receipt, ordered, prior);
    }

    private OrdenCompra order(UUID id, TenantPrincipal principal) {
        return orders.findByIdAndTenantIdAndEmpresaId(id, principal.tenantId(), principal.empresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Orden de compra no encontrada."));
    }
    private OrdenCompra lockOrder(RecepcionCompra receipt, TenantPrincipal principal) {
        return orders.bloquear(receipt.getOrdenCompraId(), principal.tenantId(), principal.empresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Orden de compra no encontrada."));
    }
    private BigDecimal received(UUID lineId, TenantPrincipal principal) {
        return receiptLines.recibidoConfirmado(lineId, principal.tenantId(), principal.empresaId());
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
    private static RecepcionCompraInput cleanInput(RecepcionCompraInput input) {
        return new RecepcionCompraInput(input.proveedorId(), input.ordenCompraId(), input.almacenId(), input.fecha(),
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
            + ",\"motivoDiferencia\":" + jsonNullable(receipt.getMotivoDiferencia())
            + ",\"diferencias\":\"" + json(differences) + "\"}";
    }
    private static String jsonNullable(Object value) { return value == null ? "null" : "\"" + json(String.valueOf(value)) + "\""; }
    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }

    private record ValidLine(Producto product, LineaOrdenCompra orderLine, BigDecimal quantity,
                             TipoDiferenciaRecepcion difference) {}
    private record Validated(Proveedor supplier, Almacen warehouse, OrdenCompra order, List<ValidLine> lines) {}
    private record DifferenceSummary(List<String> descriptions) {
        boolean hasDifferences() { return !descriptions.isEmpty(); }
    }
}
