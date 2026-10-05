package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity @Table(name="purchase_invoice")
public class FacturaProveedor {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="internal_number",nullable=false,updatable=false,length=30) private String numeroInterno;
    @Column(name="supplier_id",nullable=false) private UUID proveedorId;
    @Column(name="branch_id",nullable=false) private UUID sucursalId;
    @Column(name="supplier_invoice_number",nullable=false,length=100) private String numeroProveedor;
    @Column(name="normalized_supplier_invoice_number",nullable=false,length=100) private String numeroNormalizado;
    @Column(name="fiscal_number",length=50) private String numeroFiscal;
    @Column(name="invoice_date",nullable=false) private LocalDate fecha;
    @Column(name="due_date",nullable=false) private LocalDate vencimiento;
    @Column(name="payment_term_id") private UUID condicionPagoId;
    @Column(name="payment_term_name_snapshot",length=100) private String condicionPagoNombreSnapshot;
    @Enumerated(EnumType.STRING) @Column(name="payment_term_type_snapshot",length=20) private TipoCondicionPago condicionPagoTipoSnapshot;
    @Column(name="payment_term_days_snapshot") private Integer condicionPagoDiasSnapshot;
    @Enumerated(EnumType.STRING) @Column(name="financial_status",length=30) private EstadoFinancieroFactura estadoFinanciero;
    @Column(name="registration_key") private UUID claveRegistro;
    @Column(name="currency_id",nullable=false) private UUID monedaId;
    @Column(name="purchase_order_id") private UUID ordenCompraId;
    @Column(length=100) private String reference;
    @Column(length=1000) private String notes;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal subtotal;
    @Column(name="discount_total",nullable=false,precision=19,scale=4) private BigDecimal descuento;
    @Column(name="tax_total",nullable=false,precision=19,scale=4) private BigDecimal impuesto;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal total;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoFacturaProveedor status;
    @Column(name="registered_at") private OffsetDateTime registradaEn;
    @Column(name="registered_by") private UUID registradaPor;
    @Column(name="voided_at") private OffsetDateTime anuladaEn;
    @Column(name="voided_by") private UUID anuladaPor;
    @Column(name="void_reason",length=500) private String motivoAnulacion;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadaEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadaPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadaEn;
    @Column(name="updated_by",nullable=false) private UUID actualizadaPor;
    @Version @Column(nullable=false) private long version;
    @OneToMany(mappedBy="factura",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("ordenLinea") private List<LineaFacturaProveedor> lineas=new ArrayList<>();
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="supplier_id",insertable=false,updatable=false) private Proveedor proveedor;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="branch_id",insertable=false,updatable=false) private Sucursal sucursal;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="currency_id",insertable=false,updatable=false) private Moneda moneda;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_term_id",insertable=false,updatable=false) private CondicionPago condicionPago;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="purchase_order_id",insertable=false,updatable=false) private OrdenCompra ordenCompra;

    protected FacturaProveedor(){}
    public FacturaProveedor(UUID t,UUID e,String interno,UUID p,UUID s,String n,String nn,String nf,LocalDate f,LocalDate v,UUID cp,UUID m,UUID oc,String r,String no,UUID u){tenantId=t;empresaId=e;numeroInterno=interno;proveedorId=p;sucursalId=s;numeroProveedor=n;numeroNormalizado=nn;numeroFiscal=nf;fecha=f;vencimiento=v;condicionPagoId=cp;monedaId=m;ordenCompraId=oc;reference=r;notes=no;status=EstadoFacturaProveedor.DRAFT;creadaPor=u;actualizadaPor=u;actualizadaEn=OffsetDateTime.now();totales(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);}
    /** Constructor para fixtures; la creación productiva usa la secuencia transaccional. */
    public FacturaProveedor(UUID t,UUID e,UUID p,UUID s,String n,String nn,String nf,LocalDate f,LocalDate v,UUID cp,UUID m,UUID oc,String r,String no,UUID u){this(t,e,"FP-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),p,s,n,nn,nf,f,v,cp,m,oc,r,no,u);}
    public void actualizar(UUID p,UUID s,String n,String nn,String nf,LocalDate f,LocalDate v,UUID cp,UUID m,UUID oc,String r,String no,UUID u){proveedorId=p;sucursalId=s;numeroProveedor=n;numeroNormalizado=nn;numeroFiscal=nf;fecha=f;vencimiento=v;condicionPagoId=cp;monedaId=m;ordenCompraId=oc;reference=r;notes=no;actualizadaPor=u;}
    public void reemplazarLineas(List<LineaFacturaProveedor> nuevas){lineas.clear();nuevas.forEach(l->{l.asignar(this);lineas.add(l);});}
    public void totales(BigDecimal s,BigDecimal d,BigDecimal i,BigDecimal t){subtotal=s;descuento=d;impuesto=i;total=t;}
    public void registrar(UUID u){status=EstadoFacturaProveedor.REGISTERED;registradaEn=OffsetDateTime.now();registradaPor=u;actualizadaPor=u;estadoFinanciero=EstadoFinancieroFactura.PENDING;}
    public void registrar(CondicionPago condicion,EstadoFinancieroFactura financiero,UUID clave,UUID u){condicionPagoNombreSnapshot=condicion.getNombre();condicionPagoTipoSnapshot=condicion.getTipo();condicionPagoDiasSnapshot=condicion.getDias();estadoFinanciero=financiero;claveRegistro=clave;registrarBase(u);}
    private void registrarBase(UUID u){status=EstadoFacturaProveedor.REGISTERED;registradaEn=OffsetDateTime.now();registradaPor=u;actualizadaPor=u;}
    public void actualizarEstadoFinanciero(EstadoCuentaPagar estado,UUID usuario){estadoFinanciero=estado==EstadoCuentaPagar.PAID?EstadoFinancieroFactura.PAID:estado==EstadoCuentaPagar.PARTIALLY_PAID?EstadoFinancieroFactura.PARTIALLY_PAID:EstadoFinancieroFactura.PENDING;actualizadaPor=usuario;}
    public void anular(String m,UUID u){status=EstadoFacturaProveedor.VOIDED;motivoAnulacion=m;anuladaEn=OffsetDateTime.now();anuladaPor=u;actualizadaPor=u;}
    @PreUpdate void marca(){actualizadaEn=OffsetDateTime.now();}
    public UUID getId(){return id;}public UUID getTenantId(){return tenantId;}public UUID getEmpresaId(){return empresaId;}public String getNumeroInterno(){return numeroInterno;}public UUID getProveedorId(){return proveedorId;}public UUID getSucursalId(){return sucursalId;}public String getNumeroProveedor(){return numeroProveedor;}public String getNumeroFiscal(){return numeroFiscal;}public LocalDate getFecha(){return fecha;}public LocalDate getVencimiento(){return vencimiento;}public UUID getCondicionPagoId(){return condicionPagoId;}public String getCondicionPagoNombreSnapshot(){return condicionPagoNombreSnapshot;}public TipoCondicionPago getCondicionPagoTipoSnapshot(){return condicionPagoTipoSnapshot;}public Integer getCondicionPagoDiasSnapshot(){return condicionPagoDiasSnapshot;}public EstadoFinancieroFactura getEstadoFinanciero(){return estadoFinanciero;}public UUID getClaveRegistro(){return claveRegistro;}public UUID getMonedaId(){return monedaId;}public UUID getOrdenCompraId(){return ordenCompraId;}public String getReferencia(){return reference;}public String getNotas(){return notes;}public BigDecimal getSubtotal(){return subtotal;}public BigDecimal getDescuento(){return descuento;}public BigDecimal getImpuesto(){return impuesto;}public BigDecimal getTotal(){return total;}public EstadoFacturaProveedor getEstado(){return status;}public long getVersion(){return version;}public List<LineaFacturaProveedor> getLineas(){return lineas;}public Proveedor getProveedor(){return proveedor;}public Sucursal getSucursal(){return sucursal;}public Moneda getMoneda(){return moneda;}public CondicionPago getCondicionPago(){return condicionPago;}public OrdenCompra getOrdenCompra(){return ordenCompra;}public String getMotivoAnulacion(){return motivoAnulacion;}
}
