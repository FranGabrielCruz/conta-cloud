package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="product")
public class Producto {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="code",nullable=false,updatable=false,length=24) private String codigo;
    @Column(name="name",nullable=false,length=180) private String nombre;
    @Enumerated(EnumType.STRING) @Column(name="product_type",nullable=false,length=20) private TipoProducto tipo;
    @Column(name="category_id") private UUID categoriaId;
    @Column(name="unit_of_measure_id",nullable=false) private UUID unidadMedidaId;
    @Column(name="barcode",length=100) private String codigoBarras;
    @Column(name="description",length=1000) private String descripcion;
    @Column(name="purchase_cost",precision=19,scale=4) private BigDecimal costoCompra;
    @Column(name="sale_price",precision=19,scale=4) private BigDecimal precioVenta;
    @Column(name="currency_id") private UUID monedaId;
    @Column(name="purchase_tax_id") private UUID impuestoCompraId;
    @Column(name="sales_tax_id") private UUID impuestoVentaId;
    @Column(name="track_inventory",nullable=false) private boolean controlaExistencia;
    @Column(name="allow_negative_stock",nullable=false) private boolean permiteExistenciaNegativa;
    @Column(name="minimum_stock",precision=19,scale=4) private BigDecimal stockMinimo;
    @Column(name="active",nullable=false) private boolean activo;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by",nullable=false) private UUID actualizadoPor;
    @Version @Column(nullable=false) private long version;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="category_id",insertable=false,updatable=false) private ProductoCategoria categoria;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="unit_of_measure_id",insertable=false,updatable=false) private UnidadMedida unidadMedida;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="currency_id",insertable=false,updatable=false) private Moneda moneda;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="purchase_tax_id",insertable=false,updatable=false) private Impuesto impuestoCompra;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="sales_tax_id",insertable=false,updatable=false) private Impuesto impuestoVenta;
    protected Producto(){}
    public Producto(UUID tenantId,UUID empresaId,String codigo,String nombre,TipoProducto tipo,UUID categoriaId,UUID unidadMedidaId,
            String codigoBarras,String descripcion,BigDecimal costoCompra,BigDecimal precioVenta,UUID monedaId,UUID impuestoCompraId,
            UUID impuestoVentaId,boolean controlaExistencia,boolean permiteExistenciaNegativa,BigDecimal stockMinimo,boolean activo,UUID usuarioId){
        this.tenantId=tenantId;this.empresaId=empresaId;this.codigo=codigo;this.nombre=nombre;this.tipo=tipo;this.categoriaId=categoriaId;
        this.unidadMedidaId=unidadMedidaId;this.codigoBarras=codigoBarras;this.descripcion=descripcion;this.costoCompra=costoCompra;
        this.precioVenta=precioVenta;this.monedaId=monedaId;this.impuestoCompraId=impuestoCompraId;this.impuestoVentaId=impuestoVentaId;
        this.controlaExistencia=controlaExistencia;this.permiteExistenciaNegativa=permiteExistenciaNegativa;this.stockMinimo=stockMinimo;
        this.activo=activo;this.creadoPor=usuarioId;this.actualizadoPor=usuarioId;this.actualizadoEn=OffsetDateTime.now();
    }
    public void actualizar(String nombre,TipoProducto tipo,UUID categoriaId,UUID unidadMedidaId,String codigoBarras,String descripcion,
            BigDecimal costoCompra,BigDecimal precioVenta,UUID monedaId,UUID impuestoCompraId,UUID impuestoVentaId,
            boolean controlaExistencia,boolean permiteExistenciaNegativa,BigDecimal stockMinimo,UUID usuarioId){
        this.nombre=nombre;this.tipo=tipo;this.categoriaId=categoriaId;this.unidadMedidaId=unidadMedidaId;this.codigoBarras=codigoBarras;
        this.descripcion=descripcion;this.costoCompra=costoCompra;this.precioVenta=precioVenta;this.monedaId=monedaId;
        this.impuestoCompraId=impuestoCompraId;this.impuestoVentaId=impuestoVentaId;this.controlaExistencia=controlaExistencia;
        this.permiteExistenciaNegativa=permiteExistenciaNegativa;this.stockMinimo=stockMinimo;this.actualizadoPor=usuarioId;
    }
    public void cambiarEstado(boolean activo,UUID usuarioId){this.activo=activo;this.actualizadoPor=usuarioId;}
    @PreUpdate void marca(){actualizadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getCodigo(){return codigo;} public String getNombre(){return nombre;} public TipoProducto getTipo(){return tipo;}
    public UUID getCategoriaId(){return categoriaId;} public UUID getUnidadMedidaId(){return unidadMedidaId;} public String getCodigoBarras(){return codigoBarras;}
    public String getDescripcion(){return descripcion;} public BigDecimal getCostoCompra(){return costoCompra;} public BigDecimal getPrecioVenta(){return precioVenta;}
    public UUID getMonedaId(){return monedaId;} public UUID getImpuestoCompraId(){return impuestoCompraId;} public UUID getImpuestoVentaId(){return impuestoVentaId;}
    public boolean isControlaExistencia(){return controlaExistencia;} public boolean isPermiteExistenciaNegativa(){return permiteExistenciaNegativa;}
    public BigDecimal getStockMinimo(){return stockMinimo;} public boolean isActivo(){return activo;} public long getVersion(){return version;}
    public ProductoCategoria getCategoria(){return categoria;} public UnidadMedida getUnidadMedida(){return unidadMedida;} public Moneda getMoneda(){return moneda;}
    public Impuesto getImpuestoCompra(){return impuestoCompra;} public Impuesto getImpuestoVenta(){return impuestoVenta;}
}
