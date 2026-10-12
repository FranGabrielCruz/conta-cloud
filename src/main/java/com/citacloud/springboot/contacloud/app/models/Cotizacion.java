package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name="sales_quote")
public class Cotizacion {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="quote_number",nullable=false,updatable=false,length=30) private String numero;
    @Column(name="customer_id",nullable=false) private UUID clienteId;
    @Column(name="branch_id",nullable=false) private UUID sucursalId;
    @Column(name="quote_date",nullable=false) private LocalDate fecha;
    @Column(name="valid_until",nullable=false) private LocalDate validaHasta;
    @Column(name="currency_id",nullable=false) private UUID monedaId;
    @Column(name="customer_name_snapshot",nullable=false,length=240) private String clienteNombre;
    @Column(name="customer_tax_id_snapshot",length=50) private String clienteIdentificacion;
    @Column(name="customer_address_snapshot",length=500) private String clienteDireccion;
    @Column(name="customer_phone_snapshot",length=30) private String clienteTelefono;
    @Column(name="customer_email_snapshot",length=180) private String clienteCorreo;
    @Column(length=100) private String reference;
    @Column(length=2000) private String conditions;
    @Column(name="subtotal_amount",nullable=false,precision=19,scale=4) private BigDecimal subtotal;
    @Column(name="discount_amount",nullable=false,precision=19,scale=4) private BigDecimal descuento;
    @Column(name="tax_amount",nullable=false,precision=19,scale=4) private BigDecimal impuesto;
    @Column(name="total_amount",nullable=false,precision=19,scale=4) private BigDecimal total;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EstadoCotizacion status;
    @Column(name="sent_at") private OffsetDateTime enviadaEn;
    @Column(name="sent_by") private UUID enviadaPor;
    @Column(name="accepted_at") private OffsetDateTime aceptadaEn;
    @Column(name="accepted_by") private UUID aceptadaPor;
    @Column(name="acceptance_note",length=500) private String notaAceptacion;
    @Column(name="rejected_at") private OffsetDateTime rechazadaEn;
    @Column(name="rejected_by") private UUID rechazadaPor;
    @Column(name="rejection_reason",length=500) private String motivoRechazo;
    @Column(name="voided_at") private OffsetDateTime anuladaEn;
    @Column(name="voided_by") private UUID anuladaPor;
    @Column(name="void_reason",length=500) private String motivoAnulacion;
    @Column(name="source_quote_id",updatable=false) private UUID cotizacionOrigenId;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadaEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadaPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadaEn;
    @Column(name="updated_by",nullable=false) private UUID actualizadaPor;
    @Version @Column(nullable=false) private long version;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="branch_id",insertable=false,updatable=false) private Sucursal sucursal;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="currency_id",insertable=false,updatable=false) private Moneda moneda;
    @OneToMany(mappedBy="cotizacion",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("numeroLinea") private final List<LineaCotizacion> lineas=new ArrayList<>();
    @OneToMany(mappedBy="cotizacion",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("ocurridoEn") private final List<EventoCotizacion> eventos=new ArrayList<>();

    protected Cotizacion() {}

    public Cotizacion(UUID tenantId,UUID empresaId,String numero,UUID clienteId,UUID sucursalId,LocalDate fecha,
            LocalDate validaHasta,UUID monedaId,String clienteNombre,String clienteIdentificacion,String clienteDireccion,
            String clienteTelefono,String clienteCorreo,String referencia,String condiciones,UUID usuarioId,UUID origenId){
        this.tenantId=tenantId;this.empresaId=empresaId;this.numero=numero;this.cotizacionOrigenId=origenId;
        actualizar(clienteId,sucursalId,fecha,validaHasta,monedaId,clienteNombre,clienteIdentificacion,clienteDireccion,
            clienteTelefono,clienteCorreo,referencia,condiciones,usuarioId);
        status=EstadoCotizacion.BORRADOR;creadaPor=usuarioId;
        totales(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
    }

    public void actualizar(UUID clienteId,UUID sucursalId,LocalDate fecha,LocalDate validaHasta,UUID monedaId,
            String clienteNombre,String clienteIdentificacion,String clienteDireccion,String clienteTelefono,String clienteCorreo,
            String referencia,String condiciones,UUID usuarioId){
        this.clienteId=clienteId;this.sucursalId=sucursalId;this.fecha=fecha;this.validaHasta=validaHasta;this.monedaId=monedaId;
        this.clienteNombre=clienteNombre;this.clienteIdentificacion=clienteIdentificacion;this.clienteDireccion=clienteDireccion;
        this.clienteTelefono=clienteTelefono;this.clienteCorreo=clienteCorreo;this.reference=referencia;this.conditions=condiciones;
        this.actualizadaPor=usuarioId;this.actualizadaEn=OffsetDateTime.now();
    }
    public void reemplazarLineas(List<LineaCotizacion> nuevas){lineas.clear();nuevas.forEach(l->{l.asignarCotizacion(this);lineas.add(l);});}
    public void totales(BigDecimal subtotal,BigDecimal descuento,BigDecimal impuesto,BigDecimal total){this.subtotal=subtotal;this.descuento=descuento;this.impuesto=impuesto;this.total=total;}
    public void registrarEvento(String tipo,String detalle,UUID usuarioId){eventos.add(new EventoCotizacion(tenantId,empresaId,this,tipo,detalle,usuarioId));}
    public void enviar(UUID usuarioId){status=EstadoCotizacion.ENVIADA;enviadaEn=OffsetDateTime.now();enviadaPor=usuarioId;actualizadaPor=usuarioId;}
    public void aceptar(String nota,UUID usuarioId){status=EstadoCotizacion.ACEPTADA;notaAceptacion=nota;aceptadaEn=OffsetDateTime.now();aceptadaPor=usuarioId;actualizadaPor=usuarioId;}
    public void rechazar(String motivo,UUID usuarioId){status=EstadoCotizacion.RECHAZADA;motivoRechazo=motivo;rechazadaEn=OffsetDateTime.now();rechazadaPor=usuarioId;actualizadaPor=usuarioId;}
    public void anular(String motivo,UUID usuarioId){status=EstadoCotizacion.ANULADA;motivoAnulacion=motivo;anuladaEn=OffsetDateTime.now();anuladaPor=usuarioId;actualizadaPor=usuarioId;}
    public void marcarConvertida(UUID usuarioId){status=EstadoCotizacion.CONVERTIDA;actualizadaPor=usuarioId;}
    @PreUpdate void marca(){actualizadaEn=OffsetDateTime.now();}

    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getNumero(){return numero;} public UUID getClienteId(){return clienteId;} public UUID getSucursalId(){return sucursalId;}
    public LocalDate getFecha(){return fecha;} public LocalDate getValidaHasta(){return validaHasta;} public UUID getMonedaId(){return monedaId;}
    public String getClienteNombre(){return clienteNombre;} public String getClienteIdentificacion(){return clienteIdentificacion;}
    public String getClienteDireccion(){return clienteDireccion;} public String getClienteTelefono(){return clienteTelefono;}
    public String getClienteCorreo(){return clienteCorreo;} public String getReferencia(){return reference;} public String getCondiciones(){return conditions;}
    public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getDescuento(){return descuento;} public BigDecimal getImpuesto(){return impuesto;}
    public BigDecimal getTotal(){return total;} public EstadoCotizacion getEstado(){return status;} public long getVersion(){return version;}
    public OffsetDateTime getCreadaEn(){return creadaEn;} public UUID getCreadaPor(){return creadaPor;} public OffsetDateTime getEnviadaEn(){return enviadaEn;}
    public OffsetDateTime getAceptadaEn(){return aceptadaEn;} public OffsetDateTime getRechazadaEn(){return rechazadaEn;} public OffsetDateTime getAnuladaEn(){return anuladaEn;}
    public String getNotaAceptacion(){return notaAceptacion;} public String getMotivoRechazo(){return motivoRechazo;} public String getMotivoAnulacion(){return motivoAnulacion;}
    public UUID getCotizacionOrigenId(){return cotizacionOrigenId;} public Sucursal getSucursal(){return sucursal;} public Moneda getMoneda(){return moneda;}
    public List<LineaCotizacion> getLineas(){return lineas;} public List<EventoCotizacion> getEventos(){return eventos;}
}
