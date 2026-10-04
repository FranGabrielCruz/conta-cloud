package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_receipt_line")
public class LineaRecepcionCompra {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_receipt_id", nullable = false) private RecepcionCompra recepcion;
    @Column(name = "purchase_order_line_id") private UUID lineaOrdenId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @Column(name = "product_id", nullable = false) private UUID productoId;
    @Column(name = "product_code_snapshot", length = 24) private String productoCodigo;
    @Column(name = "description_snapshot", nullable = false, length = 500) private String descripcion;
    @Column(name = "unit_snapshot", length = 120) private String unidad;
    @Column(name = "quantity_received", nullable = false, precision = 19, scale = 4) private BigDecimal cantidad;
    @Enumerated(EnumType.STRING) @Column(name = "line_source", nullable = false, length = 20) private OrigenLineaRecepcion origen;
    @Enumerated(EnumType.STRING) @Column(name = "difference_type", nullable = false, length = 30) private TipoDiferenciaRecepcion diferencia;
    @Column(name = "line_order", nullable = false) private int ordenLinea;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private OffsetDateTime creadaEn;

    protected LineaRecepcionCompra() {}

    public LineaRecepcionCompra(UUID tenantId, UUID empresaId, UUID lineaOrdenId, UUID productoId,
            String productoCodigo, String descripcion, String unidad, BigDecimal cantidad, int ordenLinea) {
        this(tenantId, empresaId, lineaOrdenId, productoId, productoCodigo, descripcion, unidad, cantidad,
            lineaOrdenId == null ? OrigenLineaRecepcion.MANUAL : OrigenLineaRecepcion.ORDER_LINE,
            TipoDiferenciaRecepcion.NONE, ordenLinea);
    }

    public LineaRecepcionCompra(UUID tenantId, UUID empresaId, UUID lineaOrdenId, UUID productoId,
            String productoCodigo, String descripcion, String unidad, BigDecimal cantidad,
            OrigenLineaRecepcion origen, TipoDiferenciaRecepcion diferencia, int ordenLinea) {
        this.tenantId = tenantId;
        this.empresaId = empresaId;
        this.lineaOrdenId = lineaOrdenId;
        this.productoId = productoId;
        this.productoCodigo = productoCodigo;
        this.descripcion = descripcion;
        this.unidad = unidad;
        this.cantidad = cantidad;
        this.origen = origen;
        this.diferencia = diferencia;
        this.ordenLinea = ordenLinea;
    }

    void asignar(RecepcionCompra recepcion) { this.recepcion = recepcion; }
    public void reclasificar(OrigenLineaRecepcion origen, TipoDiferenciaRecepcion diferencia) {
        this.origen = origen;
        this.diferencia = diferencia;
    }

    public UUID getLineaOrdenId() { return lineaOrdenId; }
    public UUID getProductoId() { return productoId; }
    public String getProductoCodigo() { return productoCodigo; }
    public String getDescripcion() { return descripcion; }
    public String getUnidad() { return unidad; }
    public BigDecimal getCantidad() { return cantidad; }
    public OrigenLineaRecepcion getOrigen() { return origen; }
    public TipoDiferenciaRecepcion getDiferencia() { return diferencia; }
    public int getOrdenLinea() { return ordenLinea; }
}
