package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="customer")
public class Cliente {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="code",nullable=false,updatable=false,length=24) private String codigo;
    @Enumerated(EnumType.STRING) @Column(name="person_type",nullable=false,length=20) private TipoPersonaCliente tipoPersona;
    @Column(name="first_names",length=120) private String nombres;
    @Column(name="last_names",length=120) private String apellidos;
    @Column(name="legal_name",length=180) private String razonSocial;
    @Column(name="trade_name",length=150) private String nombreComercial;
    @Enumerated(EnumType.STRING) @Column(name="identification_type",length=30) private TipoIdentificacionCliente tipoIdentificacion;
    @Column(name="identification",length=50) private String identificacion;
    @Column(name="identification_normalized",length=50) private String identificacionNormalizada;
    @Column(name="phone",length=30) private String telefono;
    @Column(name="alternate_phone",length=30) private String telefonoAlternativo;
    @Column(name="email",length=180) private String correo;
    @Column(name="payment_term_id",nullable=false) private UUID condicionPagoId;
    @Column(name="currency_id",nullable=false) private UUID monedaId;
    @Column(name="credit_days",nullable=false) private int diasCredito;
    @Column(name="credit_limit",nullable=false,precision=19,scale=4) private BigDecimal limiteCredito;
    @Enumerated(EnumType.STRING) @Column(name="credit_limit_control",nullable=false,length=20) private ControlLimiteCredito controlLimite;
    @Column(name="preferred_receipt_type_id") private UUID tipoComprobantePreferidoId;
    @Column(name="tax_treatment",length=100) private String tratamientoFiscal;
    @Column(name="notes",length=1000) private String observacion;
    @Column(name="active",nullable=false) private boolean activo;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by",nullable=false) private UUID actualizadoPor;
    @Version @Column(nullable=false) private long version;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_term_id",insertable=false,updatable=false) private CondicionPago condicionPago;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="currency_id",insertable=false,updatable=false) private Moneda moneda;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="preferred_receipt_type_id",insertable=false,updatable=false) private TipoComprobanteFiscal tipoComprobantePreferido;

    protected Cliente(){}
    public Cliente(UUID tenantId,UUID empresaId,String codigo,TipoPersonaCliente tipoPersona,String nombres,String apellidos,
            String razonSocial,String nombreComercial,TipoIdentificacionCliente tipoIdentificacion,String identificacion,
            String identificacionNormalizada,String telefono,String telefonoAlternativo,String correo,UUID condicionPagoId,
            UUID monedaId,int diasCredito,BigDecimal limiteCredito,ControlLimiteCredito controlLimite,
            UUID tipoComprobantePreferidoId,String tratamientoFiscal,String observacion,UUID usuarioId){
        this.tenantId=tenantId;this.empresaId=empresaId;this.codigo=codigo;actualizar(tipoPersona,nombres,apellidos,razonSocial,
            nombreComercial,tipoIdentificacion,identificacion,identificacionNormalizada,telefono,telefonoAlternativo,correo,
            condicionPagoId,monedaId,diasCredito,limiteCredito,controlLimite,tipoComprobantePreferidoId,tratamientoFiscal,
            observacion,usuarioId);this.activo=true;this.creadoPor=usuarioId;
    }
    public void actualizar(TipoPersonaCliente tipoPersona,String nombres,String apellidos,String razonSocial,String nombreComercial,
            TipoIdentificacionCliente tipoIdentificacion,String identificacion,String identificacionNormalizada,String telefono,
            String telefonoAlternativo,String correo,UUID condicionPagoId,UUID monedaId,int diasCredito,BigDecimal limiteCredito,
            ControlLimiteCredito controlLimite,UUID tipoComprobantePreferidoId,String tratamientoFiscal,String observacion,UUID usuarioId){
        this.tipoPersona=tipoPersona;this.nombres=nombres;this.apellidos=apellidos;this.razonSocial=razonSocial;
        this.nombreComercial=nombreComercial;this.tipoIdentificacion=tipoIdentificacion;this.identificacion=identificacion;
        this.identificacionNormalizada=identificacionNormalizada;this.telefono=telefono;this.telefonoAlternativo=telefonoAlternativo;
        this.correo=correo;this.condicionPagoId=condicionPagoId;this.monedaId=monedaId;this.diasCredito=diasCredito;
        this.limiteCredito=limiteCredito;this.controlLimite=controlLimite;this.tipoComprobantePreferidoId=tipoComprobantePreferidoId;
        this.tratamientoFiscal=tratamientoFiscal;this.observacion=observacion;this.actualizadoPor=usuarioId;this.actualizadoEn=OffsetDateTime.now();
    }
    public void cambiarEstado(boolean activo,UUID usuarioId){this.activo=activo;this.actualizadoPor=usuarioId;}
    @PreUpdate void actualizarMarca(){actualizadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getCodigo(){return codigo;} public TipoPersonaCliente getTipoPersona(){return tipoPersona;}
    public String getNombres(){return nombres;} public String getApellidos(){return apellidos;} public String getRazonSocial(){return razonSocial;}
    public String getNombreComercial(){return nombreComercial;} public TipoIdentificacionCliente getTipoIdentificacion(){return tipoIdentificacion;}
    public String getIdentificacion(){return identificacion;} public String getIdentificacionNormalizada(){return identificacionNormalizada;}
    public String getTelefono(){return telefono;} public String getTelefonoAlternativo(){return telefonoAlternativo;} public String getCorreo(){return correo;}
    public UUID getCondicionPagoId(){return condicionPagoId;} public UUID getMonedaId(){return monedaId;} public int getDiasCredito(){return diasCredito;}
    public BigDecimal getLimiteCredito(){return limiteCredito;} public ControlLimiteCredito getControlLimite(){return controlLimite;}
    public UUID getTipoComprobantePreferidoId(){return tipoComprobantePreferidoId;} public String getTratamientoFiscal(){return tratamientoFiscal;}
    public String getObservacion(){return observacion;} public boolean isActivo(){return activo;} public long getVersion(){return version;}
    public CondicionPago getCondicionPago(){return condicionPago;} public Moneda getMoneda(){return moneda;}
    public TipoComprobanteFiscal getTipoComprobantePreferido(){return tipoComprobantePreferido;}
}
