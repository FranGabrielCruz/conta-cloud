package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity @Table(name="supplier_credit_note")
public class NotaCreditoProveedor {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="internal_number",nullable=false,updatable=false,length=30) private String numero;
    @Column(name="supplier_id",nullable=false) private UUID proveedorId;
    @Column(name="related_invoice_id") private UUID facturaRelacionadaId;
    @Column(name="credit_date",nullable=false) private LocalDate fecha;
    @Column(name="supplier_credit_number",nullable=false,length=100) private String numeroProveedor;
    @Column(name="normalized_supplier_credit_number",nullable=false,length=100) private String numeroProveedorNormalizado;
    @Column(name="fiscal_number",length=50) private String numeroFiscal;
    @Column(name="currency_id",nullable=false) private UUID monedaId;
    @Column(name="exchange_rate",precision=19,scale=8) private BigDecimal tasaCambio;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private MotivoNotaCreditoProveedor reason;
    @Column(name="other_reason",length=250) private String otroMotivo;
    @Column(length=1000) private String notes;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal subtotal=BigDecimal.ZERO;
    @Column(name="discount_total",nullable=false,precision=19,scale=4) private BigDecimal descuento=BigDecimal.ZERO;
    @Column(name="tax_total",nullable=false,precision=19,scale=4) private BigDecimal impuesto=BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal total=BigDecimal.ZERO;
    @Column(name="applied_amount",nullable=false,precision=19,scale=4) private BigDecimal aplicado=BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EstadoNotaCreditoProveedor status=EstadoNotaCreditoProveedor.DRAFT;
    @Column(name="confirmed_at") private OffsetDateTime confirmadaEn; @Column(name="confirmed_by") private UUID confirmadaPor;
    @Column(name="voided_at") private OffsetDateTime anuladaEn; @Column(name="voided_by") private UUID anuladaPor;
    @Column(name="void_reason",length=500) private String motivoAnulacion;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadaEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadaPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadaEn;
    @Column(name="updated_by",nullable=false) private UUID actualizadaPor;
    @Version @Column(nullable=false) private long version;
    @OneToMany(mappedBy="nota",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("ordenLinea") private List<LineaNotaCreditoProveedor> lineas=new ArrayList<>();
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="supplier_id",insertable=false,updatable=false) private Proveedor proveedor;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="currency_id",insertable=false,updatable=false) private Moneda moneda;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="related_invoice_id",insertable=false,updatable=false) private FacturaProveedor facturaRelacionada;
    protected NotaCreditoProveedor(){}
    public NotaCreditoProveedor(UUID t,UUID e,String numero,UUID proveedor,UUID factura,LocalDate fecha,String numeroProveedor,
            String normalizado,String fiscal,UUID moneda,BigDecimal tasa,MotivoNotaCreditoProveedor motivo,String otro,String notas,UUID usuario){
        tenantId=t;empresaId=e;this.numero=numero;proveedorId=proveedor;facturaRelacionadaId=factura;this.fecha=fecha;
        this.numeroProveedor=numeroProveedor;numeroProveedorNormalizado=normalizado;numeroFiscal=fiscal;monedaId=moneda;
        tasaCambio=tasa;reason=motivo;otroMotivo=otro;this.notes=notas;creadaPor=usuario;actualizadaPor=usuario;actualizadaEn=OffsetDateTime.now();}
    public void actualizar(UUID proveedor,UUID factura,LocalDate fecha,String numeroProveedor,String normalizado,String fiscal,
            UUID moneda,BigDecimal tasa,MotivoNotaCreditoProveedor motivo,String otro,String notas,UUID usuario){proveedorId=proveedor;
        facturaRelacionadaId=factura;this.fecha=fecha;this.numeroProveedor=numeroProveedor;numeroProveedorNormalizado=normalizado;
        numeroFiscal=fiscal;monedaId=moneda;tasaCambio=tasa;reason=motivo;otroMotivo=otro;this.notes=notas;actualizadaPor=usuario;}
    public void reemplazarLineas(List<LineaNotaCreditoProveedor> nuevas){lineas.clear();nuevas.forEach(x->{x.asignar(this);lineas.add(x);});}
    public void totales(BigDecimal s,BigDecimal d,BigDecimal i,BigDecimal t){subtotal=s;descuento=d;impuesto=i;total=t;}
    public void confirmar(UUID usuario){if(total.signum()<=0)throw new IllegalStateException("Total inválido");confirmadaEn=OffsetDateTime.now();confirmadaPor=usuario;actualizadaPor=usuario;recalcularEstado();}
    public void aplicar(BigDecimal monto,UUID usuario){
        if(monto==null||monto.signum()<=0||aplicado.add(monto).compareTo(total)>0)throw new IllegalArgumentException("El importe aplicado no es válido.");
        aplicado=aplicado.add(monto);actualizadaPor=usuario;recalcularEstado();
    }
    public void revertir(BigDecimal monto,UUID usuario){
        if(monto==null||monto.signum()<=0||aplicado.subtract(monto).signum()<0)throw new IllegalArgumentException("El importe revertido no es válido.");
        aplicado=aplicado.subtract(monto);actualizadaPor=usuario;recalcularEstado();
    }
    public void anular(String motivo,UUID usuario){status=EstadoNotaCreditoProveedor.VOIDED;motivoAnulacion=motivo;anuladaEn=OffsetDateTime.now();anuladaPor=usuario;actualizadaPor=usuario;}
    private void recalcularEstado(){status=aplicado.signum()==0?EstadoNotaCreditoProveedor.AVAILABLE:aplicado.compareTo(total)>=0?EstadoNotaCreditoProveedor.APPLIED:EstadoNotaCreditoProveedor.PARTIALLY_APPLIED;}
    @PreUpdate void marca(){actualizadaEn=OffsetDateTime.now();}
    public BigDecimal disponible(){return total.subtract(aplicado);} public UUID getId(){return id;} public UUID getTenantId(){return tenantId;}
    public UUID getEmpresaId(){return empresaId;} public String getNumero(){return numero;} public UUID getProveedorId(){return proveedorId;}
    public UUID getFacturaRelacionadaId(){return facturaRelacionadaId;} public LocalDate getFecha(){return fecha;} public String getNumeroProveedor(){return numeroProveedor;}
    public String getNumeroFiscal(){return numeroFiscal;} public UUID getMonedaId(){return monedaId;} public BigDecimal getTasaCambio(){return tasaCambio;}
    public MotivoNotaCreditoProveedor getMotivo(){return reason;} public String getOtroMotivo(){return otroMotivo;} public String getNotas(){return notes;}
    public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getDescuento(){return descuento;} public BigDecimal getImpuesto(){return impuesto;}
    public BigDecimal getTotal(){return total;} public BigDecimal getAplicado(){return aplicado;} public EstadoNotaCreditoProveedor getEstado(){return status;}
    public long getVersion(){return version;} public List<LineaNotaCreditoProveedor> getLineas(){return lineas;} public Proveedor getProveedor(){return proveedor;}
    public Moneda getMoneda(){return moneda;} public FacturaProveedor getFacturaRelacionada(){return facturaRelacionada;} public String getMotivoAnulacion(){return motivoAnulacion;}
}
