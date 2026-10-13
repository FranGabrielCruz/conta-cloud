package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory_transfer_line")
public class LineaTransferenciaInventario {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "inventory_transfer_id", nullable = false, updatable = false) private UUID transferenciaId;
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(name = "empresa_id", nullable = false, updatable = false) private UUID empresaId;
    @Column(name = "product_id", nullable = false, updatable = false) private UUID productoId;
    @Column(name = "unit_of_measure_id", nullable = false, updatable = false) private UUID unidadId;
    @Column(name = "product_code_snapshot", nullable = false, updatable = false, length = 24) private String codigoProducto;
    @Column(name = "product_name_snapshot", nullable = false, updatable = false, length = 300) private String nombreProducto;
    @Column(name = "description_snapshot", nullable = false, updatable = false, length = 500) private String descripcion;
    @Column(name = "unit_snapshot", nullable = false, updatable = false, length = 120) private String unidad;
    @Column(name = "dispatched_quantity", nullable = false, updatable = false, precision = 19, scale = 4) private BigDecimal cantidadDespachada;
    @Column(name = "received_quantity", nullable = false, precision = 19, scale = 4) private BigDecimal cantidadRecibida;
    @Column(name = "pending_quantity", nullable = false, precision = 19, scale = 4) private BigDecimal cantidadPendiente;
    @Column(name = "difference_quantity", nullable = false, precision = 19, scale = 4) private BigDecimal diferencia;
    @Column(name = "difference_reason", length = 500) private String motivoDiferencia;
    @Column(name = "conversion_factor", nullable = false, updatable = false, precision = 19, scale = 8) private BigDecimal factorConversion;
    @Column(name = "base_quantity", nullable = false, updatable = false, precision = 19, scale = 4) private BigDecimal cantidadBase;
    @Column(name = "line_order", nullable = false, updatable = false) private int orden;

    protected LineaTransferenciaInventario() {}

    public LineaTransferenciaInventario(UUID transferencia, UUID tenant, UUID empresa, Producto producto,
            BigDecimal cantidad, int orden) {
        transferenciaId = transferencia; tenantId = tenant; empresaId = empresa; productoId = producto.getId();
        unidadId = producto.getUnidadMedidaId(); codigoProducto = producto.getCodigo(); nombreProducto = producto.getNombre();
        descripcion = producto.getDescripcion() == null || producto.getDescripcion().isBlank()
            ? producto.getNombre() : producto.getDescripcion().trim();
        unidad = producto.getUnidadMedida() == null ? "Unidad" : producto.getUnidadMedida().getNombre();
        cantidadDespachada = cantidad; cantidadRecibida = BigDecimal.ZERO; cantidadPendiente = cantidad;
        diferencia = BigDecimal.ZERO; factorConversion = BigDecimal.ONE; cantidadBase = cantidad; this.orden = orden;
    }

    public void recibirCompleta(){cantidadRecibida=cantidadDespachada;cantidadPendiente=BigDecimal.ZERO;diferencia=BigDecimal.ZERO;}
    public UUID getId(){return id;} public UUID getTransferenciaId(){return transferenciaId;} public UUID getProductoId(){return productoId;}
    public UUID getUnidadId(){return unidadId;} public String getCodigoProducto(){return codigoProducto;} public String getNombreProducto(){return nombreProducto;}
    public String getDescripcion(){return descripcion;} public String getUnidad(){return unidad;} public BigDecimal getCantidadDespachada(){return cantidadDespachada;}
    public BigDecimal getCantidadRecibida(){return cantidadRecibida;} public BigDecimal getCantidadPendiente(){return cantidadPendiente;}
    public BigDecimal getDiferencia(){return diferencia;} public String getMotivoDiferencia(){return motivoDiferencia;}
    public BigDecimal getFactorConversion(){return factorConversion;} public BigDecimal getCantidadBase(){return cantidadBase;} public int getOrden(){return orden;}
}
