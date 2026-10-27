package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="cash_voucher")
public class ValeCaja {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
 @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
 @Column(name="branch_id",nullable=false,updatable=false) private UUID sucursalId;
 @Column(name="cash_register_id",nullable=false,updatable=false) private UUID cajaId;
 @Column(name="cash_session_id",nullable=false,updatable=false) private UUID sesionId;
 @Column(name="currency_id",nullable=false,updatable=false) private UUID monedaId;
 @Column(name="concept_id",nullable=false,updatable=false) private UUID conceptoId;
 @Column(name="voucher_number",nullable=false,updatable=false,length=30) private String numero;
 @Column(name="occurred_at",nullable=false,updatable=false) private OffsetDateTime fechaHora;
 @Column(name="concept_snapshot",nullable=false,updatable=false,length=120) private String concepto;
 @Column(name="beneficiary_snapshot",nullable=false,updatable=false,length=180) private String beneficiario;
 @Column(nullable=false,updatable=false,precision=19,scale=4) private BigDecimal amount;
 @Column(updatable=false,length=100) private String reference;
 @Column(updatable=false,length=500) private String observation;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private EstadoValeCaja status;
 @Column(name="branch_snapshot",nullable=false,updatable=false,length=150) private String sucursal;
 @Column(name="cash_register_snapshot",nullable=false,updatable=false,length=120) private String caja;
 @Column(name="currency_snapshot",nullable=false,updatable=false,length=3) private String moneda;
 @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
 @Column(name="created_by_snapshot",nullable=false,updatable=false,length=180) private String usuario;
 @Column(name="created_at",nullable=false,updatable=false) private OffsetDateTime creadoEn;
 @Column(name="company_name_snapshot",nullable=false,updatable=false,length=150) private String empresaNombre;
 @Column(name="company_tax_id_snapshot",updatable=false,length=30) private String empresaIdentificacion;
 @Column(name="company_address_snapshot",updatable=false,length=255) private String empresaDireccion;
 @Column(name="company_phone_snapshot",updatable=false,length=40) private String empresaTelefono;
 @Column(name="company_email_snapshot",updatable=false,length=180) private String empresaCorreo;
 @Column(name="company_logo_object_key_snapshot",updatable=false,length=512) private String empresaLogoObjectKey;
 @Column(name="movement_id") private UUID movimientoId;
 @Column(name="voided_by") private UUID anuladoPor;
 @Column(name="voided_by_snapshot",length=180) private String usuarioAnulacion;
 @Column(name="voided_at") private OffsetDateTime anuladoEn;
 @Column(name="void_reason",length=500) private String motivoAnulacion;
 @Column(name="reversal_movement_id") private UUID movimientoReversionId;
 @Column(name="idempotency_key",nullable=false,updatable=false) private UUID idempotencia;
 @Version private long version;
 protected ValeCaja(){}
 public ValeCaja(UUID tenant,UUID empresa,SesionCaja sesion,ConceptoValeCaja concepto,String numero,
   String beneficiario,BigDecimal importe,String referencia,String observacion,UUID usuarioId,String usuario,UUID idempotencia,
   String empresaNombre,String empresaIdentificacion,String empresaDireccion,String empresaTelefono,String empresaCorreo,String empresaLogoObjectKey){
  this.tenantId=tenant;this.empresaId=empresa;this.sucursalId=sesion.getSucursalId();this.cajaId=sesion.getCajaId();
  this.sesionId=sesion.getId();this.monedaId=sesion.getMonedaId();this.conceptoId=concepto.getId();this.numero=numero;
  this.fechaHora=OffsetDateTime.now();this.concepto=concepto.getNombre();this.beneficiario=beneficiario;this.amount=importe;
  this.reference=referencia;this.observation=observacion;this.status=EstadoValeCaja.CONFIRMED;
  this.sucursal=sesion.getSucursal().getNombre();this.caja=sesion.getCaja().getNombre();this.moneda=sesion.getMoneda().getCodigoIso();
  this.creadoPor=usuarioId;this.usuario=usuario;this.creadoEn=this.fechaHora;this.idempotencia=idempotencia;
  this.empresaNombre=empresaNombre;this.empresaIdentificacion=empresaIdentificacion;this.empresaDireccion=empresaDireccion;
  this.empresaTelefono=empresaTelefono;this.empresaCorreo=empresaCorreo;this.empresaLogoObjectKey=empresaLogoObjectKey;
 }
 public void vincularMovimiento(UUID id){if(movimientoId!=null)throw new IllegalStateException("El vale ya posee movimiento.");movimientoId=id;}
 public void anular(String motivo,UUID usuarioId,String usuario,UUID reversionId){if(status==EstadoValeCaja.VOIDED)return;
  status=EstadoValeCaja.VOIDED;motivoAnulacion=motivo;anuladoPor=usuarioId;usuarioAnulacion=usuario;anuladoEn=OffsetDateTime.now();movimientoReversionId=reversionId;}
 public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
 public UUID getSucursalId(){return sucursalId;} public UUID getCajaId(){return cajaId;} public UUID getSesionId(){return sesionId;}
 public UUID getMonedaId(){return monedaId;} public UUID getConceptoId(){return conceptoId;} public String getNumero(){return numero;}
 public OffsetDateTime getFechaHora(){return fechaHora;} public String getConcepto(){return concepto;} public String getBeneficiario(){return beneficiario;}
 public BigDecimal getImporte(){return amount;} public String getReferencia(){return reference;} public String getObservacion(){return observation;}
 public EstadoValeCaja getEstado(){return status;} public String getSucursal(){return sucursal;} public String getCaja(){return caja;}
 public String getMoneda(){return moneda;} public UUID getCreadoPor(){return creadoPor;} public String getUsuario(){return usuario;}
 public OffsetDateTime getCreadoEn(){return creadoEn;} public UUID getMovimientoId(){return movimientoId;} public UUID getAnuladoPor(){return anuladoPor;}
 public String getUsuarioAnulacion(){return usuarioAnulacion;} public OffsetDateTime getAnuladoEn(){return anuladoEn;}
 public String getMotivoAnulacion(){return motivoAnulacion;} public UUID getMovimientoReversionId(){return movimientoReversionId;} public long getVersion(){return version;}
 public String getEmpresaNombre(){return empresaNombre;} public String getEmpresaIdentificacion(){return empresaIdentificacion;}
 public String getEmpresaDireccion(){return empresaDireccion;} public String getEmpresaTelefono(){return empresaTelefono;}
 public String getEmpresaCorreo(){return empresaCorreo;} public String getEmpresaLogoObjectKey(){return empresaLogoObjectKey;}
}
