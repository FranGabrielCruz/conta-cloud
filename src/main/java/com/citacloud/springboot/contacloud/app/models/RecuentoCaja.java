package com.citacloud.springboot.contacloud.app.models;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.OffsetDateTime; import java.util.UUID;
@Entity @Table(name="cash_counts")
public class RecuentoCaja {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId; @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
 @Column(name="count_number",nullable=false,updatable=false,length=30) private String numero;
 @Column(name="cash_register_id",nullable=false,updatable=false) private UUID cajaId; @Column(name="cash_session_id",nullable=false,updatable=false) private UUID sesionId;
 @Column(name="branch_id",nullable=false,updatable=false) private UUID sucursalId; @Column(name="currency_id",nullable=false,updatable=false) private UUID monedaId;
 @Column(name="counted_at",nullable=false,updatable=false) private OffsetDateTime contadoEn; @Column(name="snapshot_at",nullable=false,updatable=false) private OffsetDateTime corteEn;
 @Column(name="counted_by",nullable=false,updatable=false) private UUID contadoPor;
 @Column(name="expected_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal esperado;
 @Column(name="counted_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal contado;
 @Column(name="difference_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal diferencia;
 @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=10) private ResultadoRecuentoCaja result;
 @Column(length=500,updatable=false) private String observation; @Column(name="idempotency_key",nullable=false,updatable=false) private UUID idempotencia;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cash_register_id",insertable=false,updatable=false) private Caja caja;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="branch_id",insertable=false,updatable=false) private Sucursal sucursal;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="currency_id",insertable=false,updatable=false) private Moneda moneda;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="counted_by",insertable=false,updatable=false) private Usuario usuario;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cash_session_id",insertable=false,updatable=false) private SesionCaja sesion;
 protected RecuentoCaja(){}
 public RecuentoCaja(UUID t,UUID e,String n,SesionCaja s,OffsetDateTime instante,UUID usuario,BigDecimal esperado,BigDecimal contado,String obs,UUID key){tenantId=t;empresaId=e;numero=n;cajaId=s.getCajaId();sesionId=s.getId();sucursalId=s.getSucursalId();monedaId=s.getMonedaId();contadoEn=instante;corteEn=instante;contadoPor=usuario;this.esperado=esperado;this.contado=contado;diferencia=contado.subtract(esperado);result=resultado(diferencia);observation=obs;idempotencia=key;this.caja=s.getCaja();this.sucursal=s.getSucursal();this.moneda=s.getMoneda();this.usuario=s.getUsuarioApertura();this.sesion=s;}
 public static ResultadoRecuentoCaja resultado(BigDecimal d){int s=d.signum();return s==0?ResultadoRecuentoCaja.BALANCED:s>0?ResultadoRecuentoCaja.OVERAGE:ResultadoRecuentoCaja.SHORTAGE;}
 public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;} public String getNumero(){return numero;} public UUID getCajaId(){return cajaId;} public UUID getSesionId(){return sesionId;} public UUID getSucursalId(){return sucursalId;} public UUID getMonedaId(){return monedaId;} public OffsetDateTime getContadoEn(){return contadoEn;} public OffsetDateTime getCorteEn(){return corteEn;} public UUID getContadoPor(){return contadoPor;} public BigDecimal getEsperado(){return esperado;} public BigDecimal getContado(){return contado;} public BigDecimal getDiferencia(){return diferencia;} public ResultadoRecuentoCaja getResult(){return result;} public String getObservation(){return observation;} public Caja getCaja(){return caja;} public Sucursal getSucursal(){return sucursal;} public Moneda getMoneda(){return moneda;} public Usuario getUsuario(){return usuario;} public SesionCaja getSesion(){return sesion;}
}
