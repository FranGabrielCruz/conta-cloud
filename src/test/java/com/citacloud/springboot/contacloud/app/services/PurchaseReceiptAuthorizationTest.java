package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.RecepcionCompraMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseReceiptAuthorizationTest {
    private final PurchaseReceiptRepository receipts = mock(PurchaseReceiptRepository.class);
    private final PurchaseReceiptLineRepository receiptLines = mock(PurchaseReceiptLineRepository.class);
    private final SupplierRepository suppliers = mock(SupplierRepository.class);
    private final AlmacenRepository warehouses = mock(AlmacenRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    private final PurchaseInvoiceRepository invoices = mock(PurchaseInvoiceRepository.class);
    private final PurchaseReceiptNumberService numbers = mock(PurchaseReceiptNumberService.class);
    private final InventoryMovementService inventory = mock(InventoryMovementService.class);
    private final AuditoriaService audit = mock(AuditoriaService.class);
    private final PurchaseReceiptService service = new PurchaseReceiptService(receipts, receiptLines, suppliers,
        warehouses, products, orders, invoices, numbers, inventory, new RecepcionCompraMapper(), audit);
    private final UUID tenant = UUID.randomUUID(), empresa = UUID.randomUUID(), usuario = UUID.randomUUID();

    @BeforeEach
    void prepare() {
        when(receipts.saveAndFlush(any())).then(returnsFirstArg());
        when(numbers.next(tenant, empresa)).thenReturn("REC-000001");
    }

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void productoFueraDeOrdenNoSeConfirmaSinPermisoEspecial() {
        authenticate(Set.of("recepciones.crear", "recepciones.confirmar"));
        Fixture fixture = fixtureWithOrder();
        RecepcionCompraInput input = input(fixture, null, "Producto adicional aceptado.");

        assertThatThrownBy(() -> service.createAndConfirm(input)).isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("autorización");
        verifyNoInteractions(inventory);
    }

    @Test
    void usuarioAutorizadoConfirmaProductoFueraDeOrdenYRegistraDiferencia() {
        authenticate(Set.of("recepciones.crear", "recepciones.confirmar", "recepciones.recibir_diferencias"));
        Fixture fixture = fixtureWithOrder();
        RecepcionCompraInput input = input(fixture, null, "Producto adicional aceptado.");

        RecepcionCompraDto result = service.createAndConfirm(input);

        assertThat(result.estado()).isEqualTo(EstadoRecepcionCompra.CONFIRMED);
        assertThat(result.lineas().getFirst().diferencia()).isEqualTo(TipoDiferenciaRecepcion.UNORDERED_PRODUCT);
        verify(inventory).receive(any(), any(), eq(fixture.product()), eq(usuario));
        verify(audit).registrar(eq("PURCHASE_RECEIPT_DIFFERENCE_CONFIRMED"), eq("RecepcionCompra"),
            nullable(UUID.class), contains("Producto adicional aceptado"));
    }

    @Test
    void recepcionSinOrdenNoSeConsideraDiferencia() {
        authenticate(Set.of("recepciones.crear", "recepciones.confirmar"));
        Fixture fixture = fixtureWithoutOrder();
        RecepcionCompraInput input = input(fixture, null, null);

        RecepcionCompraDto result = service.createAndConfirm(input);

        assertThat(result.estado()).isEqualTo(EstadoRecepcionCompra.CONFIRMED);
        assertThat(result.lineas().getFirst().diferencia()).isEqualTo(TipoDiferenciaRecepcion.NONE);
        verify(audit, never()).registrar(eq("PURCHASE_RECEIPT_DIFFERENCE_CONFIRMED"), any(), nullable(UUID.class), any());
    }

    @Test
    void facturaRegistradaPermiteRecepcionParcialSinModificarCuentaPorPagar() {
        authenticate(Set.of("recepciones.crear", "recepciones.confirmar"));
        Fixture fixture = fixtureWithoutOrder();
        UUID invoiceId = UUID.randomUUID(), invoiceLineId = UUID.randomUUID(), orderId = UUID.randomUUID(), orderLineId = UUID.randomUUID();
        OrdenCompra order = new OrdenCompra(tenant, empresa, "OC-000020", fixture.supplierId(), "Proveedor", null,
            UUID.randomUUID(), LocalDate.now(), null, UUID.randomUUID(), null, null, null, usuario);
        ReflectionTestUtils.setField(order, "id", orderId);
        LineaOrdenCompra orderLine = new LineaOrdenCompra(tenant, empresa, 1, fixture.productId(), "P-MAN", UUID.randomUUID(),
            "Unidad", "Und", "Teclado", new BigDecimal("2.0000"), BigDecimal.ONE, BigDecimal.ZERO, null, null,
            BigDecimal.ZERO, new BigDecimal("2.0000"), new BigDecimal("2.0000"), BigDecimal.ZERO, new BigDecimal("2.0000"));
        ReflectionTestUtils.setField(orderLine, "id", orderLineId);
        order.reemplazarLineas(List.of(orderLine));
        order.emitir("Proveedor", null, usuario, OffsetDateTime.now(), null);
        FacturaProveedor invoice = new FacturaProveedor(tenant, empresa, fixture.supplierId(), UUID.randomUUID(),
            "FAC-100", "fac-100", null, LocalDate.now(), LocalDate.now(), null, UUID.randomUUID(), orderId,
            null, null, usuario);
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        LineaFacturaProveedor invoiceLine = new LineaFacturaProveedor(tenant, empresa, fixture.productId(), "P-MAN",
            "Teclado", "Unidad", new BigDecimal("10.0000"), BigDecimal.ONE, BigDecimal.ZERO, null, null,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, 1);
        ReflectionTestUtils.setField(invoiceLine, "id", invoiceLineId);
        invoice.reemplazarLineas(List.of(invoiceLine));
        invoice.registrar(usuario);
        when(invoices.findByIdAndTenantIdAndEmpresaId(invoiceId, tenant, empresa)).thenReturn(Optional.of(invoice));
        when(invoices.bloquear(invoiceId, tenant, empresa)).thenReturn(Optional.of(invoice));
        when(orders.findByIdAndTenantIdAndEmpresaId(orderId, tenant, empresa)).thenReturn(Optional.of(order));
        when(orders.bloquear(orderId, tenant, empresa)).thenReturn(Optional.of(order));
        when(receiptLines.recibidoConfirmado(orderLineId, tenant, empresa)).thenReturn(BigDecimal.ZERO);
        when(receiptLines.recibidoConfirmadoFactura(invoiceLineId, tenant, empresa)).thenReturn(BigDecimal.ZERO);
        RecepcionCompraInput input = new RecepcionCompraInput(fixture.supplierId(), orderId, invoiceId,
            fixture.warehouseId(), LocalDate.now(), null, null, null,
            List.of(new LineaRecepcionCompraInput(orderLineId, invoiceLineId, fixture.productId(), new BigDecimal("4.0000"))),
            null, UUID.randomUUID());

        RecepcionCompraDto result = service.createAndConfirm(input);

        assertThat(result.estado()).isEqualTo(EstadoRecepcionCompra.CONFIRMED);
        assertThat(result.lineas().getFirst().lineaFacturaId()).isEqualTo(invoiceLineId);
        assertThat(result.lineas().getFirst().origen()).isEqualTo(OrigenLineaRecepcion.PURCHASE_INVOICE);
        verify(inventory).receive(any(), any(), eq(fixture.product()), eq(usuario));
    }

    @Test
    void reintentoConMismaClaveNoDuplicaRecepcionNiInventario() {
        authenticate(Set.of("recepciones.crear", "recepciones.confirmar"));
        UUID key = UUID.randomUUID();
        RecepcionCompra existing = new RecepcionCompra(tenant, empresa, "REC-000003", UUID.randomUUID(), null,
            UUID.randomUUID(), LocalDate.now(), null, null, key, usuario);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.confirmar(usuario);
        when(receipts.findByTenantIdAndEmpresaIdAndClaveIdempotencia(tenant, empresa, key)).thenReturn(Optional.of(existing));
        RecepcionCompraInput retry = new RecepcionCompraInput(null, null, null, null, null, null, null, null,
            List.of(), null, key);

        RecepcionCompraDto result = service.createAndConfirm(retry);

        assertThat(result.numero()).isEqualTo("REC-000003");
        assertThat(result.estado()).isEqualTo(EstadoRecepcionCompra.CONFIRMED);
        verify(receipts, never()).saveAndFlush(any());
        verifyNoInteractions(inventory);
    }

    @Test
    void editarYConfirmarEliminaLasLineasAnterioresAntesDeInsertarLasNuevas() {
        authenticate(Set.of("recepciones.editar", "recepciones.confirmar"));
        Fixture fixture = fixtureWithoutOrder();
        UUID receiptId = UUID.randomUUID();
        RecepcionCompra existing = new RecepcionCompra(tenant, empresa, "REC-000004", fixture.supplierId(), null,
            fixture.warehouseId(), LocalDate.now(), null, null, usuario);
        ReflectionTestUtils.setField(existing, "id", receiptId);
        existing.reemplazarLineas(List.of(new LineaRecepcionCompra(tenant, empresa, null, fixture.productId(),
            "P-MAN", "Teclado", null, BigDecimal.ONE, 1)));
        when(receipts.bloquear(receiptId, tenant, empresa)).thenReturn(Optional.of(existing));
        doAnswer(invocation -> {
            assertThat(existing.getLineas()).isEmpty();
            return null;
        }).when(receipts).flush();
        RecepcionCompraInput update = new RecepcionCompraInput(fixture.supplierId(), null, null, fixture.warehouseId(),
            LocalDate.now(), null, null, null,
            List.of(new LineaRecepcionCompraInput(null, null, fixture.productId(), new BigDecimal("2.0000"))),
            existing.getVersion(), null);

        RecepcionCompraDto result = service.updateAndConfirm(receiptId, update);

        assertThat(result.estado()).isEqualTo(EstadoRecepcionCompra.CONFIRMED);
        assertThat(result.lineas()).hasSize(1);
        assertThat(result.lineas().getFirst().cantidad()).isEqualByComparingTo("2.0000");
        verify(receipts).flush();
    }

    private Fixture fixtureWithOrder() {
        Fixture fixture = fixtureWithoutOrder();
        UUID orderId = UUID.randomUUID(), orderedProductId = UUID.randomUUID(), orderLineId = UUID.randomUUID();
        OrdenCompra order = new OrdenCompra(tenant, empresa, "OC-000012", fixture.supplierId(), "Proveedor", null,
            UUID.randomUUID(), LocalDate.now(), null, UUID.randomUUID(), null, null, null, usuario);
        ReflectionTestUtils.setField(order, "id", orderId);
        order.emitir("Proveedor", null, usuario, OffsetDateTime.now(), null);
        LineaOrdenCompra orderLine = new LineaOrdenCompra(tenant, empresa, 1, orderedProductId, "P-ORD", UUID.randomUUID(),
            "Unidad", "Und", "Producto ordenado", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, null, null,
            BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN);
        ReflectionTestUtils.setField(orderLine, "id", orderLineId);
        order.reemplazarLineas(List.of(orderLine));
        when(orders.findByIdAndTenantIdAndEmpresaId(orderId, tenant, empresa)).thenReturn(Optional.of(order));
        when(orders.bloquear(orderId, tenant, empresa)).thenReturn(Optional.of(order));
        when(receiptLines.recibidoConfirmado(orderLineId, tenant, empresa)).thenReturn(BigDecimal.ZERO);
        return new Fixture(fixture.supplierId(), fixture.warehouseId(), fixture.productId(), orderId, fixture.product());
    }

    private Fixture fixtureWithoutOrder() {
        UUID supplierId = UUID.randomUUID(), warehouseId = UUID.randomUUID(), productId = UUID.randomUUID();
        Proveedor supplier = mock(Proveedor.class);
        when(supplier.getId()).thenReturn(supplierId);
        when(supplier.isActivo()).thenReturn(true);
        Almacen warehouse = mock(Almacen.class);
        when(warehouse.isActivo()).thenReturn(true);
        Producto product = mock(Producto.class);
        when(product.getId()).thenReturn(productId);
        when(product.getCodigo()).thenReturn("P-MAN");
        when(product.getNombre()).thenReturn("Teclado");
        when(product.getTipo()).thenReturn(TipoProducto.PRODUCT);
        when(product.isActivo()).thenReturn(true);
        when(product.isControlaExistencia()).thenReturn(true);
        when(suppliers.findByIdAndTenantIdAndEmpresaId(supplierId, tenant, empresa)).thenReturn(Optional.of(supplier));
        when(warehouses.findByIdAndTenantIdAndEmpresaId(warehouseId, tenant, empresa)).thenReturn(Optional.of(warehouse));
        when(products.findByIdAndTenantIdAndEmpresaId(productId, tenant, empresa)).thenReturn(Optional.of(product));
        return new Fixture(supplierId, warehouseId, productId, null, product);
    }

    private RecepcionCompraInput input(Fixture fixture, UUID orderLineId, String note) {
        return new RecepcionCompraInput(fixture.supplierId(), fixture.orderId(), null, fixture.warehouseId(), LocalDate.now(),
            null, null, note, List.of(new LineaRecepcionCompraInput(orderLineId, null, fixture.productId(), new BigDecimal("5.0000"))),
            null, UUID.randomUUID());
    }

    private void authenticate(Set<String> permissions) {
        TenantPrincipal principal = new TenantPrincipal(usuario, tenant, empresa, null, "DEMO", "Administrador",
            "admin", "", true, true, Set.of(), permissions);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private record Fixture(UUID supplierId, UUID warehouseId, UUID productId, UUID orderId, Producto product) {}
}
