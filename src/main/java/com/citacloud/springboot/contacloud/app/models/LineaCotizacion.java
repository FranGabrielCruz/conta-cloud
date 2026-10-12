package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_quote_line")
public class LineaCotizacion {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sales_quote_id",nullable=false) private Cotizacion cotizacion;
    @Column(name="line_number",nullable=false) private int numeroLinea;
    @Column(name="product_id",nullable=false) private UUID productoId;
    @Column(name="product_code_snapshot",length=24) private String productoCodigo;
    @Column(name="description_snapshot",nullable=false,length=500) private String descripcion;
    @Column(name="unit_of_measure_id_snapshot") private UUID unidadMedidaId;
    @Column(name="unit_name_snapshot",length=80) private String unidadMedidaNombre;
    @Column(name="unit_abbreviation_snapshot",length=20) private String unidadMedidaAbreviatura;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal quantity;
    @Column(name="unit_price",nullable=false,precision=19,scale=4) private BigDecimal precioUnitario;
    @Enumerated(EnumType.STRING) @Column(name="discount_type",nullable=false,length=20) private TipoDescuentoCotizacion tipoDescuento;
    @Column(name="discount_value",nullable=false,precision=19,scale=4) private BigDecimal valorDescuento;
    @Column(name="discount_amount",nullable=false,precision=19,scale=4) private BigDecimal descuento;
    @Column(name="tax_id") private UUID impuestoId;
    @Column(name="tax_name_snapshot",length=100) private String impuestoNombre;
    @Column(name="tax_rate_snapshot",nullable=false,precision=9,scale=4) private BigDecimal tasaImpuesto;
    @Column(name="gross_subtotal_amount",nullable=false,precision=19,scale=4) private BigDecimal subtotalBruto;
    @Column(name="taxable_base_amount",nullable=false,precision=19,scale=4) private BigDecimal baseImponible;
    @Column(name="tax_amount",nullable=false,precision=19,scale=4) private BigDecimal impuesto;
    @Column(name="total_amount",nullable=false,precision=19,scale=4) private BigDecimal total;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadaEn;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadaEn;
    protected LineaCotizacion() {}
    public LineaCotizacion(UUID tenantId,UUID empresaId,int numeroLinea,UUID productoId,String productoCodigo,UUID unidadMedidaId,
            String unidadMedidaNombre,String unidadMedidaAbreviatura,String descripcion,BigDecimal cantidad,BigDecimal precioUnitario,
            TipoDescuentoCotizacion tipoDescuento,BigDecimal valorDescuento,BigDecimal descuento,UUID impuestoId,String impuestoNombre,
            BigDecimal tasaImpuesto,BigDecimal subtotalBruto,BigDecimal baseImponible,BigDecimal impuesto,BigDecimal total){
        this.tenantId=tenantId;this.empresaId=empresaId;this.numeroLinea=numeroLinea;this.productoId=productoId;this.productoCodigo=productoCodigo;
        this.unidadMedidaId=unidadMedidaId;this.unidadMedidaNombre=unidadMedidaNombre;this.unidadMedidaAbreviatura=unidadMedidaAbreviatura;
        this.descripcion=descripcion;this.quantity=cantidad;this.precioUnitario=precioUnitario;this.tipoDescuento=tipoDescuento;
        this.valorDescuento=valorDescuento;this.descuento=descuento;this.impuestoId=impuestoId;this.impuestoNombre=impuestoNombre;
        this.tasaImpuesto=tasaImpuesto;this.subtotalBruto=subtotalBruto;this.baseImponible=baseImponible;this.impuesto=impuesto;
        this.total=total;this.actualizadaEn=OffsetDateTime.now();
    }
    void asignarCotizacion(Cotizacion cotizacion){this.cotizacion=cotizacion;}
    @PreUpdate void marca(){actualizadaEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public int getNumeroLinea(){return numeroLinea;} public UUID getProductoId(){return productoId;}
    public String getProductoCodigo(){return productoCodigo;} public String getDescripcion(){return descripcion;} public UUID getUnidadMedidaId(){return unidadMedidaId;}
    public String getUnidadMedidaNombre(){return unidadMedidaNombre;} public String getUnidadMedidaAbreviatura(){return unidadMedidaAbreviatura;}
    public BigDecimal getCantidad(){return quantity;} public BigDecimal getPrecioUnitario(){return precioUnitario;}
    public TipoDescuentoCotizacion getTipoDescuento(){return tipoDescuento;} public BigDecimal getValorDescuento(){return valorDescuento;}
    public BigDecimal getDescuento(){return descuento;} public UUID getImpuestoId(){return impuestoId;} public String getImpuestoNombre(){return impuestoNombre;}
    public BigDecimal getTasaImpuesto(){return tasaImpuesto;} public BigDecimal getSubtotalBruto(){return subtotalBruto;}
    public BigDecimal getBaseImponible(){return baseImponible;} public BigDecimal getImpuesto(){return impuesto;} public BigDecimal getTotal(){return total;}
}
