package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_invoice_line")
public class LineaFacturaProveedor {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_invoice_id", nullable = false) private FacturaProveedor factura;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @Column(name = "product_id", nullable = false) private UUID productoId;
    @Column(name = "product_code_snapshot", length = 24) private String productoCodigo;
    @Column(name = "description_snapshot", nullable = false, length = 500) private String descripcion;
    @Column(name = "unit_snapshot", length = 120) private String unidad;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal precio;
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4) private BigDecimal descuento;
    @Column(name = "tax_id") private UUID impuestoId;
    @Column(name = "tax_name_snapshot", length = 120) private String impuestoNombre;
    @Column(name = "tax_rate_snapshot", nullable = false, precision = 9, scale = 6) private BigDecimal impuestoTasa;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4) private BigDecimal impuesto;
    @Column(name = "line_subtotal", nullable = false, precision = 19, scale = 4) private BigDecimal subtotal;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 4) private BigDecimal total;
    @Column(name = "line_order", nullable = false) private int ordenLinea;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private OffsetDateTime creadaEn;

    protected LineaFacturaProveedor() {}

    public LineaFacturaProveedor(UUID tenantId, UUID empresaId, UUID productoId, String productoCodigo,
            String descripcion, String unidad, BigDecimal cantidad, BigDecimal precio, BigDecimal descuento,
            UUID impuestoId, String impuestoNombre, BigDecimal impuestoTasa, BigDecimal impuesto,
            BigDecimal subtotal, BigDecimal total, int ordenLinea) {
        this.tenantId = tenantId; this.empresaId = empresaId; this.productoId = productoId;
        this.productoCodigo = productoCodigo; this.descripcion = descripcion; this.unidad = unidad;
        this.quantity = cantidad; this.precio = precio; this.descuento = descuento; this.impuestoId = impuestoId;
        this.impuestoNombre = impuestoNombre; this.impuestoTasa = impuestoTasa; this.impuesto = impuesto;
        this.subtotal = subtotal; this.total = total; this.ordenLinea = ordenLinea;
    }

    void asignar(FacturaProveedor factura) { this.factura = factura; }
    public UUID getId() { return id; }
    public UUID getProductoId() { return productoId; }
    public String getProductoCodigo() { return productoCodigo; }
    public String getDescripcion() { return descripcion; }
    public String getUnidad() { return unidad; }
    public BigDecimal getCantidad() { return quantity; }
    public BigDecimal getPrecio() { return precio; }
    public BigDecimal getDescuento() { return descuento; }
    public UUID getImpuestoId() { return impuestoId; }
    public String getImpuestoNombre() { return impuestoNombre; }
    public BigDecimal getImpuestoTasa() { return impuestoTasa; }
    public BigDecimal getImpuesto() { return impuesto; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotal() { return total; }
    public int getOrdenLinea() { return ordenLinea; }
}
