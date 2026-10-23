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
    @Column(name="gross_subtotal",nullable=false,precision=19,scale=4) private BigDecimal subtotalBruto;
    @Column(name="taxable_base_amount",nullable=false,precision=19,scale=4) private BigDecimal baseImponible;
    @Enumerated(EnumType.STRING) @Column(name="discount_type",length=20) private TipoDescuentoVenta tipoDescuento;
    @Column(name="discount_value",precision=19,scale=6) private BigDecimal valorDescuento;
    @Column(name="discount_reason_id") private UUID motivoDescuentoId;
    @Column(name="discount_reason_name",length=120) private String motivoDescuentoNombre;
    @Column(name="discount_reason_detail",length=500) private String motivoDescuentoDetalle;
    @Column(name="discount_authorization_id") private UUID autorizacionDescuentoId;
    @Column(name="discount_authorized_by") private UUID descuentoAutorizadoPor;
    @Column(name="discount_authorized_at") private java.time.OffsetDateTime descuentoAutorizadoEn;
    @Column(name="surcharge_amount",nullable=false,precision=19,scale=4) private BigDecimal recargo=BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(name="surcharge_type",length=20) private TipoRecargoVenta tipoRecargo;
    @Column(name="surcharge_value",precision=19,scale=6) private BigDecimal valorRecargo;
    @Column(name="surcharge_concept_id") private UUID conceptoRecargoId;
    @Column(name="surcharge_concept_name",length=120) private String conceptoRecargoNombre;
    @Enumerated(EnumType.STRING) @Column(name="surcharge_tax_treatment",length=30) private TratamientoFiscalRecargo tratamientoFiscalRecargo;
    @Column(name="surcharge_reason_id") private UUID motivoRecargoId;
    @Column(name="surcharge_reason_name",length=120) private String motivoRecargoNombre;
    @Column(name="surcharge_reason_detail",length=500) private String motivoRecargoDetalle;
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
        this(f,p,almacen,orden,cantidad,precio,subtotal,BigDecimal.ZERO,subtotal,tax,impuesto,total,null,null,null,null,null,null,null,null);
    }
    public LineaFacturaVenta(FacturaVenta f,Producto p,UUID almacen,int orden,BigDecimal cantidad,BigDecimal precio,
            BigDecimal subtotalBruto,BigDecimal descuento,BigDecimal baseImponible,Impuesto tax,BigDecimal impuesto,BigDecimal total,
            TipoDescuentoVenta tipoDescuento,BigDecimal valorDescuento,UUID motivoId,String motivoNombre,String motivoDetalle,
            UUID autorizacionId,UUID autorizadoPor,java.time.OffsetDateTime autorizadoEn){
        tenantId=f.getTenantId();empresaId=f.getEmpresaId();facturaId=f.getId();productoId=p.getId();codigoProducto=p.getCodigo();
        descripcion=p.getNombre();unidadId=p.getUnidadMedidaId();unidadNombre=p.getUnidadMedida().getNombre();this.cantidad=cantidad;
        precioUnitario=precio;this.subtotalBruto=subtotalBruto;this.descuento=descuento;this.baseImponible=baseImponible;
        this.tipoDescuento=tipoDescuento;this.valorDescuento=valorDescuento;motivoDescuentoId=motivoId;motivoDescuentoNombre=motivoNombre;
        motivoDescuentoDetalle=motivoDetalle;autorizacionDescuentoId=autorizacionId;descuentoAutorizadoPor=autorizadoPor;descuentoAutorizadoEn=autorizadoEn;
        impuestoId=tax==null?null:tax.getId();impuestoNombre=tax==null?null:tax.getNombre();
        tasaImpuesto=tax==null?BigDecimal.ZERO:tax.getPorcentaje();this.impuesto=impuesto;this.subtotal=baseImponible;this.total=total;
        controlaExistencia=p.isControlaExistencia();almacenId=almacen;this.orden=orden;
    }
    public void aplicarRecargo(BigDecimal recargo,BigDecimal nuevaBase,BigDecimal nuevoImpuesto,BigDecimal nuevoTotal,
            TipoRecargoVenta tipo,BigDecimal valor,TipoRecargo concepto,MotivoRecargoVenta motivo,String detalle){
        this.recargo=recargo;this.baseImponible=nuevaBase;this.subtotal=nuevaBase;this.impuesto=nuevoImpuesto;this.total=nuevoTotal;
        tipoRecargo=tipo;valorRecargo=valor;conceptoRecargoId=concepto==null?null:concepto.getId();conceptoRecargoNombre=concepto==null?null:concepto.getNombre();tratamientoFiscalRecargo=concepto==null?null:concepto.getTratamientoFiscal();
        motivoRecargoId=motivo==null?null:motivo.getId();motivoRecargoNombre=motivo==null?null:motivo.getNombre();motivoRecargoDetalle=detalle;
    }
    public UUID getId(){return id;} public UUID getProductoId(){return productoId;} public String getCodigoProducto(){return codigoProducto;}
    public String getDescripcion(){return descripcion;} public String getUnidadNombre(){return unidadNombre;} public BigDecimal getCantidad(){return cantidad;}
    public BigDecimal getPrecioUnitario(){return precioUnitario;} public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getImpuesto(){return impuesto;}
    public BigDecimal getSubtotalBruto(){return subtotalBruto;}public BigDecimal getDescuento(){return descuento;}public BigDecimal getBaseImponible(){return baseImponible;}public TipoDescuentoVenta getTipoDescuento(){return tipoDescuento;}public BigDecimal getValorDescuento(){return valorDescuento;}public String getMotivoDescuentoNombre(){return motivoDescuentoNombre;}public String getMotivoDescuentoDetalle(){return motivoDescuentoDetalle;}
    public BigDecimal getTotal(){return total;} public boolean isControlaExistencia(){return controlaExistencia;} public UUID getUnidadId(){return unidadId;}
    public UUID getFacturaId(){return facturaId;} public BigDecimal getTasaImpuesto(){return tasaImpuesto;}
    public BigDecimal getRecargo(){return recargo;}public String getConceptoRecargoNombre(){return conceptoRecargoNombre;}public String getMotivoRecargoNombre(){return motivoRecargoNombre;}
}
