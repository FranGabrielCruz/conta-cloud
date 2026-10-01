package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="transferencias_financieras")
public class TransferenciaFinanciera {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(nullable=false,updatable=false) private LocalDate fecha;
    @Enumerated(EnumType.STRING) @Column(name="tipo_cuenta_origen",nullable=false,updatable=false,length=20)
    private TipoCuentaDinero tipoCuentaOrigen;
    @Column(name="caja_origen_id",updatable=false) private UUID cajaOrigenId;
    @Column(name="cuenta_bancaria_origen_id",updatable=false) private UUID cuentaBancariaOrigenId;
    @Enumerated(EnumType.STRING) @Column(name="tipo_cuenta_destino",nullable=false,updatable=false,length=20)
    private TipoCuentaDinero tipoCuentaDestino;
    @Column(name="caja_destino_id",updatable=false) private UUID cajaDestinoId;
    @Column(name="cuenta_bancaria_destino_id",updatable=false) private UUID cuentaBancariaDestinoId;
    @Column(name="moneda_origen_id",nullable=false,updatable=false) private UUID monedaOrigenId;
    @Column(name="moneda_destino_id",nullable=false,updatable=false) private UUID monedaDestinoId;
    @Column(name="monto_origen",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal montoOrigen;
    @Column(name="monto_destino",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal montoDestino;
    @Column(name="tasa_cambio",updatable=false,precision=19,scale=8) private BigDecimal tasaCambio;
    @Column(name="descripcion_tasa",updatable=false,length=180) private String descripcionTasa;
    @Column(updatable=false,length=100) private String referencia;
    @Column(updatable=false,length=1000) private String descripcion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private EstadoMovimientoFinanciero estado;
    @Column(name="creado_en",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="creado_por",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="anulado_en") private OffsetDateTime anuladoEn;
    @Column(name="anulado_por") private UUID anuladoPor;
    @Column(name="motivo_anulacion",length=500) private String motivoAnulacion;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="tenant_id",referencedColumnName="tenant_id",insertable=false,updatable=false),
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="caja_origen_id",referencedColumnName="id",insertable=false,updatable=false)})
    private Caja cajaOrigen;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="tenant_id",referencedColumnName="tenant_id",insertable=false,updatable=false),
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="cuenta_bancaria_origen_id",referencedColumnName="id",insertable=false,updatable=false)})
    private CuentaBancaria cuentaBancariaOrigen;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="tenant_id",referencedColumnName="tenant_id",insertable=false,updatable=false),
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="caja_destino_id",referencedColumnName="id",insertable=false,updatable=false)})
    private Caja cajaDestino;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="tenant_id",referencedColumnName="tenant_id",insertable=false,updatable=false),
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="cuenta_bancaria_destino_id",referencedColumnName="id",insertable=false,updatable=false)})
    private CuentaBancaria cuentaBancariaDestino;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="moneda_origen_id",referencedColumnName="id",insertable=false,updatable=false)})
    private Moneda monedaOrigen;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="moneda_destino_id",referencedColumnName="id",insertable=false,updatable=false)})
    private Moneda monedaDestino;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="anulado_por",insertable=false,updatable=false)
    private Usuario usuarioAnulacion;

    protected TransferenciaFinanciera() {}

    public TransferenciaFinanciera(UUID tenantId,UUID empresaId,LocalDate fecha,
            TipoCuentaDinero tipoOrigen,UUID origenId,TipoCuentaDinero tipoDestino,UUID destinoId,
            UUID monedaOrigenId,UUID monedaDestinoId,BigDecimal montoOrigen,BigDecimal montoDestino,
            BigDecimal tasaCambio,String descripcionTasa,String referencia,String descripcion,UUID usuarioId) {
        this.tenantId=tenantId;this.empresaId=empresaId;this.fecha=fecha;
        this.tipoCuentaOrigen=tipoOrigen;asignarOrigen(tipoOrigen,origenId);
        this.tipoCuentaDestino=tipoDestino;asignarDestino(tipoDestino,destinoId);
        this.monedaOrigenId=monedaOrigenId;this.monedaDestinoId=monedaDestinoId;
        this.montoOrigen=montoOrigen;this.montoDestino=montoDestino;this.tasaCambio=tasaCambio;
        this.descripcionTasa=descripcionTasa;this.referencia=referencia;this.descripcion=descripcion;
        this.estado=EstadoMovimientoFinanciero.REGISTERED;this.creadoPor=usuarioId;
    }

    public void anular(String motivo,UUID usuarioId){estado=EstadoMovimientoFinanciero.VOIDED;
        motivoAnulacion=motivo;anuladoPor=usuarioId;anuladoEn=OffsetDateTime.now();}
    public void asignarRelaciones(Caja cajaOrigen,CuentaBancaria bancoOrigen,Caja cajaDestino,
            CuentaBancaria bancoDestino,Moneda monedaOrigen,Moneda monedaDestino){
        this.cajaOrigen=cajaOrigen;this.cuentaBancariaOrigen=bancoOrigen;this.cajaDestino=cajaDestino;
        this.cuentaBancariaDestino=bancoDestino;this.monedaOrigen=monedaOrigen;this.monedaDestino=monedaDestino;}
    private void asignarOrigen(TipoCuentaDinero tipo,UUID id){cajaOrigenId=tipo==TipoCuentaDinero.CASH_REGISTER?id:null;
        cuentaBancariaOrigenId=tipo==TipoCuentaDinero.BANK_ACCOUNT?id:null;}
    private void asignarDestino(TipoCuentaDinero tipo,UUID id){cajaDestinoId=tipo==TipoCuentaDinero.CASH_REGISTER?id:null;
        cuentaBancariaDestinoId=tipo==TipoCuentaDinero.BANK_ACCOUNT?id:null;}

    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public LocalDate getFecha(){return fecha;} public TipoCuentaDinero getTipoCuentaOrigen(){return tipoCuentaOrigen;}
    public UUID getCajaOrigenId(){return cajaOrigenId;} public UUID getCuentaBancariaOrigenId(){return cuentaBancariaOrigenId;}
    public TipoCuentaDinero getTipoCuentaDestino(){return tipoCuentaDestino;} public UUID getCajaDestinoId(){return cajaDestinoId;}
    public UUID getCuentaBancariaDestinoId(){return cuentaBancariaDestinoId;} public UUID getMonedaOrigenId(){return monedaOrigenId;}
    public UUID getMonedaDestinoId(){return monedaDestinoId;} public BigDecimal getMontoOrigen(){return montoOrigen;}
    public BigDecimal getMontoDestino(){return montoDestino;} public BigDecimal getTasaCambio(){return tasaCambio;}
    public String getDescripcionTasa(){return descripcionTasa;} public String getReferencia(){return referencia;}
    public String getDescripcion(){return descripcion;} public EstadoMovimientoFinanciero getEstado(){return estado;}
    public OffsetDateTime getCreadoEn(){return creadoEn;} public UUID getCreadoPor(){return creadoPor;}
    public OffsetDateTime getAnuladoEn(){return anuladoEn;} public UUID getAnuladoPor(){return anuladoPor;}
    public String getMotivoAnulacion(){return motivoAnulacion;} public Caja getCajaOrigen(){return cajaOrigen;}
    public CuentaBancaria getCuentaBancariaOrigen(){return cuentaBancariaOrigen;} public Caja getCajaDestino(){return cajaDestino;}
    public CuentaBancaria getCuentaBancariaDestino(){return cuentaBancariaDestino;} public Moneda getMonedaOrigen(){return monedaOrigen;}
    public Moneda getMonedaDestino(){return monedaDestino;} public Usuario getUsuarioAnulacion(){return usuarioAnulacion;}
}
