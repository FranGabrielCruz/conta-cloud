package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "movimientos_financieros")
public class MovimientoFinanciero {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Enumerated(EnumType.STRING) @Column(name="tipo_movimiento",nullable=false,length=10)
    private TipoMovimientoFinanciero tipoMovimiento;
    @Column(nullable=false) private LocalDate fecha;
    @Enumerated(EnumType.STRING) @Column(name="tipo_cuenta",nullable=false,length=20)
    private TipoCuentaDinero tipoCuenta;
    @Column(name="caja_id") private UUID cajaId;
    @Column(name="cuenta_bancaria_id") private UUID cuentaBancariaId;
    @Column(name="moneda_id",nullable=false) private UUID monedaId;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal monto;
    @Column(nullable=false,length=180) private String concepto;
    @Column(length=100) private String referencia;
    @Column(length=1000) private String descripcion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private EstadoMovimientoFinanciero estado;
    @Enumerated(EnumType.STRING) @Column(name="tipo_origen",nullable=false,length=30)
    private TipoOrigenMovimiento tipoOrigen;
    @Column(name="origen_id") private UUID origenId;
    @Column(name="cash_register_session_id") private UUID sesionCajaId;
    @Enumerated(EnumType.STRING) @Column(name="payment_method",nullable=false,length=20)
    private MedioPagoMovimiento medioPago;
    @Column(name="creado_en",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="creado_por",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="actualizado_en",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="actualizado_por",nullable=false) private UUID actualizadoPor;
    @Column(name="anulado_en") private OffsetDateTime anuladoEn;
    @Column(name="anulado_por") private UUID anuladoPor;
    @Column(name="motivo_anulacion",length=500) private String motivoAnulacion;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name="tenant_id",referencedColumnName="tenant_id",insertable=false,updatable=false),
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="caja_id",referencedColumnName="id",insertable=false,updatable=false)
    }) private Caja caja;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name="tenant_id",referencedColumnName="tenant_id",insertable=false,updatable=false),
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="cuenta_bancaria_id",referencedColumnName="id",insertable=false,updatable=false)
    }) private CuentaBancaria cuentaBancaria;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="moneda_id",referencedColumnName="id",insertable=false,updatable=false)
    }) private Moneda moneda;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="anulado_por",insertable=false,updatable=false) private Usuario usuarioAnulacion;

    protected MovimientoFinanciero() {}
    public MovimientoFinanciero(UUID tenantId, UUID empresaId, TipoMovimientoFinanciero tipo,
            LocalDate fecha, TipoCuentaDinero tipoCuenta, UUID cuentaId, UUID monedaId,
            BigDecimal monto, String concepto, String referencia, String descripcion, UUID usuarioId) {
        this(tenantId,empresaId,tipo,fecha,tipoCuenta,cuentaId,monedaId,monto,concepto,referencia,
            descripcion,TipoOrigenMovimiento.MANUAL,null,
            tipoCuenta==TipoCuentaDinero.CASH_REGISTER?MedioPagoMovimiento.CASH:MedioPagoMovimiento.BANK_TRANSFER,null,usuarioId);
    }
    public MovimientoFinanciero(UUID tenantId, UUID empresaId, TipoMovimientoFinanciero tipo,
            LocalDate fecha, TipoCuentaDinero tipoCuenta, UUID cuentaId, UUID monedaId,
            BigDecimal monto, String concepto, String referencia, String descripcion,
            MedioPagoMovimiento medioPago,UUID sesionCajaId,UUID usuarioId) {
        this(tenantId,empresaId,tipo,fecha,tipoCuenta,cuentaId,monedaId,monto,concepto,referencia,
            descripcion,TipoOrigenMovimiento.MANUAL,null,medioPago,sesionCajaId,usuarioId);
    }
    public static MovimientoFinanciero transferencia(UUID tenantId,UUID empresaId,TipoMovimientoFinanciero tipo,
            LocalDate fecha,TipoCuentaDinero tipoCuenta,UUID cuentaId,UUID monedaId,BigDecimal monto,
            String concepto,String referencia,String descripcion,UUID transferenciaId,UUID usuarioId){
        return new MovimientoFinanciero(tenantId,empresaId,tipo,fecha,tipoCuenta,cuentaId,monedaId,monto,
            concepto,referencia,descripcion,TipoOrigenMovimiento.TRANSFER,transferenciaId,
            tipoCuenta==TipoCuentaDinero.CASH_REGISTER?MedioPagoMovimiento.CASH:MedioPagoMovimiento.BANK_TRANSFER,null,usuarioId);
    }
    public static MovimientoFinanciero saldoApertura(UUID tenantId,UUID empresaId,TipoMovimientoFinanciero tipo,
            LocalDate fecha,UUID cuentaId,UUID monedaId,BigDecimal monto,UUID usuarioId){
        return new MovimientoFinanciero(tenantId,empresaId,tipo,fecha,TipoCuentaDinero.BANK_ACCOUNT,cuentaId,
            monedaId,monto,"Saldo de apertura",null,"Saldo inicial al incorporar la cuenta bancaria",
            TipoOrigenMovimiento.OPENING_BALANCE,cuentaId,MedioPagoMovimiento.BANK_TRANSFER,null,usuarioId);
    }
    private MovimientoFinanciero(UUID tenantId, UUID empresaId, TipoMovimientoFinanciero tipo,
            LocalDate fecha, TipoCuentaDinero tipoCuenta, UUID cuentaId, UUID monedaId,
            BigDecimal monto, String concepto, String referencia, String descripcion,
            TipoOrigenMovimiento tipoOrigen,UUID origenId,MedioPagoMovimiento medioPago,UUID sesionCajaId,UUID usuarioId) {
        this.tenantId=tenantId; this.empresaId=empresaId; this.tipoMovimiento=tipo; this.fecha=fecha;
        this.tipoCuenta=tipoCuenta; asignarCuenta(tipoCuenta,cuentaId); this.monedaId=monedaId;
        this.monto=monto; this.concepto=concepto; this.referencia=referencia; this.descripcion=descripcion;
        this.estado=EstadoMovimientoFinanciero.REGISTERED; this.tipoOrigen=tipoOrigen;this.origenId=origenId;
        this.medioPago=medioPago;this.sesionCajaId=sesionCajaId;
        this.creadoPor=usuarioId; this.actualizadoPor=usuarioId; this.actualizadoEn=OffsetDateTime.now();
    }
    @PreUpdate void actualizarMarcaTiempo(){ actualizadoEn=OffsetDateTime.now(); }
    public void actualizar(LocalDate fecha, TipoCuentaDinero tipoCuenta, UUID cuentaId, UUID monedaId,
            BigDecimal monto, String concepto, String referencia, String descripcion, UUID usuarioId) {
        actualizar(fecha,tipoCuenta,cuentaId,monedaId,monto,concepto,referencia,descripcion,
            tipoCuenta==TipoCuentaDinero.CASH_REGISTER?MedioPagoMovimiento.CASH:MedioPagoMovimiento.BANK_TRANSFER,
            tipoCuenta==TipoCuentaDinero.CASH_REGISTER?sesionCajaId:null,usuarioId);
    }
    public void actualizar(LocalDate fecha, TipoCuentaDinero tipoCuenta, UUID cuentaId, UUID monedaId,
            BigDecimal monto, String concepto, String referencia, String descripcion,
            MedioPagoMovimiento medioPago,UUID sesionCajaId,UUID usuarioId) {
        this.fecha=fecha; this.tipoCuenta=tipoCuenta; asignarCuenta(tipoCuenta,cuentaId); this.monedaId=monedaId;
        this.monto=monto; this.concepto=concepto; this.referencia=referencia; this.descripcion=descripcion;
        this.medioPago=medioPago;this.sesionCajaId=sesionCajaId;this.actualizadoPor=usuarioId;
    }
    public void anular(String motivo, UUID usuarioId) {
        estado=EstadoMovimientoFinanciero.VOIDED; motivoAnulacion=motivo; anuladoPor=usuarioId;
        anuladoEn=OffsetDateTime.now(); actualizadoPor=usuarioId;
    }
    public void asignarRelacionesCuenta(Caja caja, CuentaBancaria cuentaBancaria, Moneda moneda) {
        this.caja=caja;
        this.cuentaBancaria=cuentaBancaria;
        this.moneda=moneda;
    }
    public void asignarOperacionCaja(UUID sesionCajaId,MedioPagoMovimiento medioPago){
        this.sesionCajaId=sesionCajaId;this.medioPago=medioPago;
    }
    private void asignarCuenta(TipoCuentaDinero tipo, UUID id) {
        cajaId=tipo==TipoCuentaDinero.CASH_REGISTER?id:null;
        cuentaBancariaId=tipo==TipoCuentaDinero.BANK_ACCOUNT?id:null;
    }
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;}
    public UUID getEmpresaId(){return empresaId;} public TipoMovimientoFinanciero getTipoMovimiento(){return tipoMovimiento;}
    public LocalDate getFecha(){return fecha;} public TipoCuentaDinero getTipoCuenta(){return tipoCuenta;}
    public UUID getCajaId(){return cajaId;} public UUID getCuentaBancariaId(){return cuentaBancariaId;}
    public UUID getMonedaId(){return monedaId;} public BigDecimal getMonto(){return monto;}
    public String getConcepto(){return concepto;} public String getReferencia(){return referencia;}
    public String getDescripcion(){return descripcion;} public EstadoMovimientoFinanciero getEstado(){return estado;}
    public TipoOrigenMovimiento getTipoOrigen(){return tipoOrigen;} public UUID getOrigenId(){return origenId;}
    public UUID getSesionCajaId(){return sesionCajaId;} public MedioPagoMovimiento getMedioPago(){return medioPago;}
    public OffsetDateTime getCreadoEn(){return creadoEn;} public UUID getCreadoPor(){return creadoPor;}
    public OffsetDateTime getActualizadoEn(){return actualizadoEn;} public UUID getActualizadoPor(){return actualizadoPor;}
    public OffsetDateTime getAnuladoEn(){return anuladoEn;} public UUID getAnuladoPor(){return anuladoPor;}
    public String getMotivoAnulacion(){return motivoAnulacion;} public Caja getCaja(){return caja;}
    public CuentaBancaria getCuentaBancaria(){return cuentaBancaria;} public Moneda getMoneda(){return moneda;}
    public Usuario getUsuarioAnulacion(){return usuarioAnulacion;}
}
