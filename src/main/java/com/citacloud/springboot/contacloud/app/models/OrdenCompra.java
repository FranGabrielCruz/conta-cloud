package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name="purchase_order")
public class OrdenCompra {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="order_number",nullable=false,updatable=false,length=30) private String numero;
    @Column(name="supplier_id",nullable=false) private UUID proveedorId;
    @Column(name="supplier_name_snapshot",nullable=false,length=180) private String proveedorNombre;
    @Column(name="supplier_tax_id_snapshot",length=50) private String proveedorIdentificacion;
    @Column(name="branch_id",nullable=false) private UUID sucursalId;
    @Column(name="order_date",nullable=false) private LocalDate fecha;
    @Column(name="expected_delivery_date") private LocalDate fechaEntrega;
    @Column(name="currency_id",nullable=false) private UUID monedaId;
    @Column(name="payment_term_id") private UUID condicionPagoId;
    @Column(length=100) private String reference;
    @Column(length=1000) private String notes;
    @Column(name="subtotal_amount",nullable=false,precision=19,scale=4) private BigDecimal subtotal;
    @Column(name="discount_amount",nullable=false,precision=19,scale=4) private BigDecimal descuento;
    @Column(name="tax_amount",nullable=false,precision=19,scale=4) private BigDecimal impuesto;
    @Column(name="total_amount",nullable=false,precision=19,scale=4) private BigDecimal total;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EstadoOrdenCompra status;
    @Column(name="issued_at") private OffsetDateTime emitidaEn;
    @Column(name="issued_by") private UUID emitidaPor;
    @Column(name="voided_at") private OffsetDateTime anuladaEn;
    @Column(name="voided_by") private UUID anuladaPor;
    @Column(name="void_reason",length=500) private String motivoAnulacion;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadaEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadaPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadaEn;
    @Column(name="updated_by",nullable=false) private UUID actualizadaPor;
    @Version @Column(nullable=false) private long version;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="supplier_id",insertable=false,updatable=false) private Proveedor proveedor;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="branch_id",insertable=false,updatable=false) private Sucursal sucursal;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="currency_id",insertable=false,updatable=false) private Moneda moneda;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_term_id",insertable=false,updatable=false) private CondicionPago condicionPago;
    @OneToMany(mappedBy="orden",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("numeroLinea") private final List<LineaOrdenCompra> lineas=new ArrayList<>();

    protected OrdenCompra() {}
    public OrdenCompra(UUID tenantId,UUID empresaId,String numero,UUID proveedorId,String proveedorNombre,
            String proveedorIdentificacion,UUID sucursalId,LocalDate fecha,LocalDate fechaEntrega,UUID monedaId,
            UUID condicionPagoId,String referencia,String notas,UUID usuarioId){
        this.tenantId=tenantId;this.empresaId=empresaId;this.numero=numero;this.proveedorId=proveedorId;
        this.proveedorNombre=proveedorNombre;this.proveedorIdentificacion=proveedorIdentificacion;this.sucursalId=sucursalId;
        this.fecha=fecha;this.fechaEntrega=fechaEntrega;this.monedaId=monedaId;this.condicionPagoId=condicionPagoId;
        this.reference=referencia;this.notes=notas;this.status=EstadoOrdenCompra.DRAFT;this.creadaPor=usuarioId;
        this.actualizadaPor=usuarioId;this.actualizadaEn=OffsetDateTime.now();totales(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
    }
    public void actualizar(UUID proveedorId,String proveedorNombre,String proveedorIdentificacion,UUID sucursalId,
            LocalDate fecha,LocalDate fechaEntrega,UUID monedaId,UUID condicionPagoId,String referencia,String notas,UUID usuarioId){
        this.proveedorId=proveedorId;this.proveedorNombre=proveedorNombre;this.proveedorIdentificacion=proveedorIdentificacion;
        this.sucursalId=sucursalId;this.fecha=fecha;this.fechaEntrega=fechaEntrega;this.monedaId=monedaId;
        this.condicionPagoId=condicionPagoId;this.reference=referencia;this.notes=notas;this.actualizadaPor=usuarioId;
    }
    public void reemplazarLineas(List<LineaOrdenCompra> nuevas){lineas.clear();nuevas.forEach(l->{l.asignarOrden(this);lineas.add(l);});}
    public void totales(BigDecimal subtotal,BigDecimal descuento,BigDecimal impuesto,BigDecimal total){this.subtotal=subtotal;this.descuento=descuento;this.impuesto=impuesto;this.total=total;}
    public void emitir(String nombreProveedor,String identificacion,UUID usuarioId){proveedorNombre=nombreProveedor;proveedorIdentificacion=identificacion;status=EstadoOrdenCompra.ISSUED;emitidaEn=OffsetDateTime.now();emitidaPor=usuarioId;actualizadaPor=usuarioId;}
    public void anular(String motivo,UUID usuarioId){status=EstadoOrdenCompra.VOIDED;motivoAnulacion=motivo;anuladaEn=OffsetDateTime.now();anuladaPor=usuarioId;actualizadaPor=usuarioId;}
    @PreUpdate void marca(){actualizadaEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getNumero(){return numero;} public UUID getProveedorId(){return proveedorId;} public String getProveedorNombre(){return proveedorNombre;}
    public String getProveedorIdentificacion(){return proveedorIdentificacion;} public UUID getSucursalId(){return sucursalId;}
    public LocalDate getFecha(){return fecha;} public LocalDate getFechaEntrega(){return fechaEntrega;} public UUID getMonedaId(){return monedaId;}
    public UUID getCondicionPagoId(){return condicionPagoId;} public String getReferencia(){return reference;} public String getNotas(){return notes;}
    public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getDescuento(){return descuento;} public BigDecimal getImpuesto(){return impuesto;}
    public BigDecimal getTotal(){return total;} public EstadoOrdenCompra getEstado(){return status;} public long getVersion(){return version;}
    public OffsetDateTime getEmitidaEn(){return emitidaEn;} public OffsetDateTime getAnuladaEn(){return anuladaEn;} public String getMotivoAnulacion(){return motivoAnulacion;}
    public List<LineaOrdenCompra> getLineas(){return lineas;} public Sucursal getSucursal(){return sucursal;} public Moneda getMoneda(){return moneda;}
    public CondicionPago getCondicionPago(){return condicionPago;}
}
