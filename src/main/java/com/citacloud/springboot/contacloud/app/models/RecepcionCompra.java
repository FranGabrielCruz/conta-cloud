package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "purchase_receipt")
public class RecepcionCompra {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(name = "empresa_id", nullable = false, updatable = false) private UUID empresaId;
    @Column(name = "receipt_number", nullable = false, updatable = false, length = 30) private String numero;
    @Column(name = "supplier_id", nullable = false) private UUID proveedorId;
    @Column(name = "purchase_order_id") private UUID ordenCompraId;
    @Column(name = "purchase_invoice_id") private UUID facturaProveedorId;
    @Column(name = "warehouse_id", nullable = false) private UUID almacenId;
    @Column(name = "receipt_date", nullable = false) private LocalDate fecha;
    @Column(length = 100) private String reference;
    @Column(length = 1000) private String notes;
    @Column(name = "difference_note", length = 1000) private String motivoDiferencia;
    @Column(name = "idempotency_key", updatable = false) private UUID claveIdempotencia;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoRecepcionCompra status;
    @Column(name = "confirmed_at") private OffsetDateTime confirmadaEn;
    @Column(name = "confirmed_by") private UUID confirmadaPor;
    @Column(name = "voided_at") private OffsetDateTime anuladaEn;
    @Column(name = "voided_by") private UUID anuladaPor;
    @Column(name = "void_reason", length = 500) private String motivoAnulacion;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private OffsetDateTime creadaEn;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID creadaPor;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime actualizadaEn;
    @Column(name = "updated_by", nullable = false) private UUID actualizadaPor;
    @Version @Column(nullable = false) private long version;
    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("ordenLinea") private List<LineaRecepcionCompra> lineas = new ArrayList<>();
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "supplier_id", insertable = false, updatable = false) private Proveedor proveedor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "warehouse_id", insertable = false, updatable = false) private Almacen almacen;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "purchase_order_id", insertable = false, updatable = false) private OrdenCompra ordenCompra;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "purchase_invoice_id", insertable = false, updatable = false) private FacturaProveedor facturaProveedor;

    protected RecepcionCompra() {}

    public RecepcionCompra(UUID tenantId, UUID empresaId, String numero, UUID proveedorId, UUID ordenCompraId,
            UUID almacenId, LocalDate fecha, String referencia, String notas, UUID usuarioId) {
        this(tenantId, empresaId, numero, proveedorId, ordenCompraId, null, almacenId, fecha, referencia, notas, null, usuarioId);
    }

    public RecepcionCompra(UUID tenantId, UUID empresaId, String numero, UUID proveedorId, UUID ordenCompraId,
            UUID almacenId, LocalDate fecha, String referencia, String notas, UUID claveIdempotencia, UUID usuarioId) {
        this(tenantId, empresaId, numero, proveedorId, ordenCompraId, null, almacenId, fecha, referencia, notas,
            claveIdempotencia, usuarioId);
    }

    public RecepcionCompra(UUID tenantId, UUID empresaId, String numero, UUID proveedorId, UUID ordenCompraId,
            UUID facturaProveedorId, UUID almacenId, LocalDate fecha, String referencia, String notas,
            UUID claveIdempotencia, UUID usuarioId) {
        this.tenantId = tenantId; this.empresaId = empresaId; this.numero = numero; this.proveedorId = proveedorId;
        this.ordenCompraId = ordenCompraId; this.facturaProveedorId = facturaProveedorId; this.almacenId = almacenId; this.fecha = fecha; this.reference = referencia;
        this.notes = notas; this.claveIdempotencia = claveIdempotencia; this.status = EstadoRecepcionCompra.DRAFT; this.creadaPor = usuarioId;
        this.actualizadaPor = usuarioId; this.actualizadaEn = OffsetDateTime.now();
    }

    public void actualizar(UUID proveedorId, UUID ordenCompraId, UUID facturaProveedorId, UUID almacenId, LocalDate fecha,
            String referencia, String notas, String motivoDiferencia, UUID usuarioId) {
        this.proveedorId = proveedorId; this.ordenCompraId = ordenCompraId; this.facturaProveedorId = facturaProveedorId; this.almacenId = almacenId;
        this.fecha = fecha; this.reference = referencia; this.notes = notas; this.motivoDiferencia = motivoDiferencia;
        this.actualizadaPor = usuarioId;
    }

    public void establecerMotivoDiferencia(String motivoDiferencia, UUID usuarioId) {
        this.motivoDiferencia = motivoDiferencia;
        this.actualizadaPor = usuarioId;
    }

    public void reemplazarLineas(List<LineaRecepcionCompra> nuevas) {
        lineas.clear();
        nuevas.forEach(linea -> { linea.asignar(this); lineas.add(linea); });
    }

    public void confirmar(UUID usuarioId) {
        status = EstadoRecepcionCompra.CONFIRMED; confirmadaEn = OffsetDateTime.now();
        confirmadaPor = usuarioId; actualizadaPor = usuarioId;
    }

    public void anular(String motivo, UUID usuarioId) {
        status = EstadoRecepcionCompra.VOIDED; motivoAnulacion = motivo; anuladaEn = OffsetDateTime.now();
        anuladaPor = usuarioId; actualizadaPor = usuarioId;
    }

    @PreUpdate void marcaActualizacion() { actualizadaEn = OffsetDateTime.now(); }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEmpresaId() { return empresaId; }
    public String getNumero() { return numero; }
    public UUID getProveedorId() { return proveedorId; }
    public UUID getOrdenCompraId() { return ordenCompraId; }
    public UUID getFacturaProveedorId() { return facturaProveedorId; }
    public UUID getAlmacenId() { return almacenId; }
    public LocalDate getFecha() { return fecha; }
    public String getReferencia() { return reference; }
    public String getNotas() { return notes; }
    public String getMotivoDiferencia() { return motivoDiferencia; }
    public UUID getClaveIdempotencia() { return claveIdempotencia; }
    public EstadoRecepcionCompra getEstado() { return status; }
    public OffsetDateTime getConfirmadaEn() { return confirmadaEn; }
    public UUID getConfirmadaPor() { return confirmadaPor; }
    public long getVersion() { return version; }
    public List<LineaRecepcionCompra> getLineas() { return lineas; }
    public Proveedor getProveedor() { return proveedor; }
    public Almacen getAlmacen() { return almacen; }
    public OrdenCompra getOrdenCompra() { return ordenCompra; }
    public FacturaProveedor getFacturaProveedor() { return facturaProveedor; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
}
