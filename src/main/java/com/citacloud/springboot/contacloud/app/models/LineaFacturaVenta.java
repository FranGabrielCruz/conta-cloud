package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="sales_invoice_line")
public class LineaFacturaVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="sales_invoice_id",nullable=false,updatable=false) private UUID facturaId;
    @Column(name="product_id",nullable=false,updatable=false) private UUID productoId;
    @Column(name="product_code",nullable=false,length=24) private String codigoProducto;
    @Column(name="description",nullable=false,length=300) private String descripcion;
    @Column(name="unit_of_measure_id",nullable=false) private UUID unidadId;
    @Column(name="unit_name",nullable=false,length=80) private String unidadNombre;
    @Column(name="quantity",nullable=false,precision=19,scale=4) private BigDecimal cantidad;
    @Column(name="unit_price",nullable=false,precision=19,scale=4) private BigDecimal precioUnitario;
    @Column(name="discount_amount",nullable=false,precision=19,scale=4) private BigDecimal descuento;
    @Column(name="tax_id") private UUID impuestoId;
    @Column(name="tax_name",length=120) private String impuestoNombre;
    @Column(name="tax_rate",nullable=false,precision=9,scale=6) private BigDecimal tasaImpuesto;
    @Column(name="tax_amount",nullable=false,precision=19,scale=4) private BigDecimal impuesto;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal subtotal;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal total;
    @Column(name="track_inventory",nullable=false) private boolean controlaExistencia;
    @Column(name="warehouse_id",nullable=false) private UUID almacenId;
    @Column(name="line_order",nullable=false) private int orden;
    protected LineaFacturaVenta(){}
    public LineaFacturaVenta(FacturaVenta f,Producto p,UUID almacen,int orden,BigDecimal cantidad,BigDecimal precio,
            BigDecimal subtotal,Impuesto tax,BigDecimal impuesto,BigDecimal total){
        tenantId=f.getTenantId();empresaId=f.getEmpresaId();facturaId=f.getId();productoId=p.getId();codigoProducto=p.getCodigo();
        descripcion=p.getNombre();unidadId=p.getUnidadMedidaId();unidadNombre=p.getUnidadMedida().getNombre();this.cantidad=cantidad;
        precioUnitario=precio;descuento=BigDecimal.ZERO;impuestoId=tax==null?null:tax.getId();impuestoNombre=tax==null?null:tax.getNombre();
        tasaImpuesto=tax==null?BigDecimal.ZERO:tax.getPorcentaje();this.impuesto=impuesto;this.subtotal=subtotal;this.total=total;
        controlaExistencia=p.isControlaExistencia();almacenId=almacen;this.orden=orden;
    }
    public UUID getId(){return id;} public UUID getProductoId(){return productoId;} public String getCodigoProducto(){return codigoProducto;}
    public String getDescripcion(){return descripcion;} public String getUnidadNombre(){return unidadNombre;} public BigDecimal getCantidad(){return cantidad;}
    public BigDecimal getPrecioUnitario(){return precioUnitario;} public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getImpuesto(){return impuesto;}
    public BigDecimal getTotal(){return total;} public boolean isControlaExistencia(){return controlaExistencia;} public UUID getUnidadId(){return unidadId;}
}
