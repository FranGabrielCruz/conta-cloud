package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="sales_return_line")
public class LineaDevolucionVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="sales_return_id",nullable=false,updatable=false) private UUID devolucionId;
    @Column(name="sales_invoice_line_id",nullable=false,updatable=false) private UUID lineaFacturaId;
    @Column(name="product_id",nullable=false,updatable=false) private UUID productoId;
    @Column(name="product_code",nullable=false,length=24) private String productoCodigo;
    @Column(nullable=false,length=300) private String description;
    @Column(name="unit_of_measure_id",nullable=false) private UUID unidadId;
    @Column(name="unit_name",nullable=false,length=80) private String unidadNombre;
    @Column(name="returned_quantity",nullable=false,precision=19,scale=4) private BigDecimal cantidad;
    @Column(name="unit_price",nullable=false,precision=19,scale=4) private BigDecimal precio;
    @Column(name="gross_subtotal",nullable=false,precision=19,scale=4) private BigDecimal subtotalBruto;
    @Column(name="discount_amount",nullable=false,precision=19,scale=4) private BigDecimal descuento;
    @Column(name="surcharge_amount",nullable=false,precision=19,scale=4) private BigDecimal recargo;
    @Column(name="taxable_base_amount",nullable=false,precision=19,scale=4) private BigDecimal baseImponible;
    @Column(name="tax_rate",nullable=false,precision=9,scale=6) private BigDecimal tasaImpuesto;
    @Column(name="tax_amount",nullable=false,precision=19,scale=4) private BigDecimal impuesto;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal total;
    @Column(name="track_inventory",nullable=false) private boolean controlaExistencia;
    @Column(name="line_order",nullable=false) private int orden;
    protected LineaDevolucionVenta(){}
    public LineaDevolucionVenta(DevolucionVenta devolucion,LineaFacturaVenta original,BigDecimal cantidad,
            BigDecimal subtotalBruto,BigDecimal descuento,BigDecimal recargo,BigDecimal base,BigDecimal impuesto,BigDecimal total,int orden){
        tenantId=devolucion.getTenantId();empresaId=devolucion.getEmpresaId();devolucionId=devolucion.getId();lineaFacturaId=original.getId();
        productoId=original.getProductoId();productoCodigo=original.getCodigoProducto();description=original.getDescripcion();unidadId=original.getUnidadId();
        unidadNombre=original.getUnidadNombre();this.cantidad=cantidad;precio=original.getPrecioUnitario();this.subtotalBruto=subtotalBruto;
        this.descuento=descuento;this.recargo=recargo;baseImponible=base;tasaImpuesto=original.getTasaImpuesto();this.impuesto=impuesto;
        this.total=total;controlaExistencia=original.isControlaExistencia();this.orden=orden;
    }
    public UUID getId(){return id;} public UUID getProductoId(){return productoId;} public UUID getUnidadId(){return unidadId;}
    public UUID getLineaFacturaId(){return lineaFacturaId;} public String getProductoCodigo(){return productoCodigo;}
    public String getDescripcion(){return description;} public String getUnidadNombre(){return unidadNombre;}
    public BigDecimal getCantidad(){return cantidad;} public BigDecimal getPrecio(){return precio;}
    public BigDecimal getSubtotalBruto(){return subtotalBruto;} public BigDecimal getDescuento(){return descuento;}
    public BigDecimal getRecargo(){return recargo;} public BigDecimal getBaseImponible(){return baseImponible;}
    public BigDecimal getTasaImpuesto(){return tasaImpuesto;} public BigDecimal getImpuesto(){return impuesto;}
    public BigDecimal getTotal(){return total;} public boolean isControlaExistencia(){return controlaExistencia;}
}
