package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="cash_register_session")
public class SesionCaja {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="cash_register_id",nullable=false,updatable=false) private UUID cajaId;
    @Column(name="branch_id",nullable=false,updatable=false) private UUID sucursalId;
    @Column(name="currency_id",nullable=false,updatable=false) private UUID monedaId;
    @Column(name="business_date",nullable=false,updatable=false) private LocalDate fechaOperativa;
    @Column(name="shift_number",nullable=false,updatable=false) private int numeroTurno;
    @Column(name="display_code",nullable=false,updatable=false,length=100) private String codigoVisible;
    @Column(name="opened_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime abiertoEn;
    @Column(name="opened_by",nullable=false,updatable=false) private UUID abiertoPor;
    @Column(name="opening_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal fondoInicial;
    @Column(name="opening_note",updatable=false,length=500) private String observacionApertura;
    @Column(name="closed_at") private OffsetDateTime cerradoEn;
    @Column(name="closed_by") private UUID cerradoPor;
    @Column(name="expected_cash_amount",precision=19,scale=4) private BigDecimal efectivoEsperado;
    @Column(name="counted_cash_amount",precision=19,scale=4) private BigDecimal efectivoContado;
    @Column(name="difference_amount",precision=19,scale=4) private BigDecimal diferencia;
    @Column(name="closing_note",length=500) private String observacionCierre;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private EstadoSesionCaja status;
    @Enumerated(EnumType.STRING) @Column(name="review_status",nullable=false,length=20) private EstadoRevisionCaja estadoRevision;
    @Column(name="reviewed_at") private OffsetDateTime revisadoEn;
    @Column(name="reviewed_by") private UUID revisadoPor;
    @Column(name="review_note",length=500) private String observacionRevision;
    @Version @Column(nullable=false) private long version;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="tenant_id",referencedColumnName="tenant_id",insertable=false,updatable=false),
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="cash_register_id",referencedColumnName="id",insertable=false,updatable=false)}) private Caja caja;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="branch_id",referencedColumnName="id",insertable=false,updatable=false)}) private Sucursal sucursal;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="currency_id",referencedColumnName="id",insertable=false,updatable=false)}) private Moneda moneda;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="opened_by",insertable=false,updatable=false) private Usuario usuarioApertura;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="closed_by",insertable=false,updatable=false) private Usuario usuarioCierre;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by",insertable=false,updatable=false) private Usuario usuarioRevision;

    protected SesionCaja(){}
    public SesionCaja(UUID tenantId,UUID empresaId,Caja caja,LocalDate fechaOperativa,int numeroTurno,
            String codigoVisible,BigDecimal fondoInicial,String observacionApertura,UUID usuarioId){
        this.tenantId=tenantId;this.empresaId=empresaId;this.cajaId=caja.getId();this.sucursalId=caja.getSucursalId();
        this.monedaId=caja.getMonedaId();this.fechaOperativa=fechaOperativa;this.numeroTurno=numeroTurno;
        this.codigoVisible=codigoVisible;this.fondoInicial=fondoInicial;this.observacionApertura=observacionApertura;
        this.abiertoPor=usuarioId;this.status=EstadoSesionCaja.OPEN;this.estadoRevision=EstadoRevisionCaja.PENDING;
        this.actualizadoEn=OffsetDateTime.now();this.caja=caja;this.sucursal=caja.getSucursal();this.moneda=caja.getMoneda();
    }
    @PreUpdate void actualizarMarca(){actualizadoEn=OffsetDateTime.now();}
    public void cerrar(BigDecimal esperado,BigDecimal contado,String nota,UUID usuarioId){
        efectivoEsperado=esperado;efectivoContado=contado;diferencia=contado.subtract(esperado);
        observacionCierre=nota;cerradoPor=usuarioId;cerradoEn=OffsetDateTime.now();status=EstadoSesionCaja.CLOSED;
        estadoRevision=EstadoRevisionCaja.PENDING;
    }
    public void revisar(EstadoRevisionCaja estado,String nota,UUID usuarioId){estadoRevision=estado;
        observacionRevision=nota;revisadoPor=usuarioId;revisadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public UUID getCajaId(){return cajaId;} public UUID getSucursalId(){return sucursalId;} public UUID getMonedaId(){return monedaId;}
    public LocalDate getFechaOperativa(){return fechaOperativa;} public int getNumeroTurno(){return numeroTurno;}
    public String getCodigoVisible(){return codigoVisible;} public OffsetDateTime getAbiertoEn(){return abiertoEn;}
    public UUID getAbiertoPor(){return abiertoPor;} public BigDecimal getFondoInicial(){return fondoInicial;}
    public String getObservacionApertura(){return observacionApertura;} public OffsetDateTime getCerradoEn(){return cerradoEn;}
    public UUID getCerradoPor(){return cerradoPor;} public BigDecimal getEfectivoEsperado(){return efectivoEsperado;}
    public BigDecimal getEfectivoContado(){return efectivoContado;} public BigDecimal getDiferencia(){return diferencia;}
    public String getObservacionCierre(){return observacionCierre;} public EstadoSesionCaja getStatus(){return status;}
    public EstadoRevisionCaja getEstadoRevision(){return estadoRevision;} public OffsetDateTime getRevisadoEn(){return revisadoEn;}
    public UUID getRevisadoPor(){return revisadoPor;} public String getObservacionRevision(){return observacionRevision;}
    public long getVersion(){return version;} public Caja getCaja(){return caja;} public Sucursal getSucursal(){return sucursal;}
    public Moneda getMoneda(){return moneda;} public Usuario getUsuarioApertura(){return usuarioApertura;}
    public Usuario getUsuarioCierre(){return usuarioCierre;} public Usuario getUsuarioRevision(){return usuarioRevision;}
}
