package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "supplier_payment")
public class PagoProveedor {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="supplier_id",nullable=false,updatable=false) private UUID proveedorId;
    @Column(name="purchase_invoice_id",nullable=false,updatable=false) private UUID facturaId;
    @Column(name="accounts_payable_id",updatable=false) private UUID cuentaPagarId;
    @Column(nullable=false) private LocalDate fecha;
    @Column(name="currency_id",nullable=false,updatable=false) private UUID monedaId;
    @Column(nullable=false,precision=19,scale=4,updatable=false) private BigDecimal monto;
    @Enumerated(EnumType.STRING) @Column(name="source_type",nullable=false,length=20,updatable=false)
    private TipoCuentaDinero tipoFuente;
    @Column(name="cash_register_id",updatable=false) private UUID cajaId;
    @Column(name="bank_account_id",updatable=false) private UUID cuentaBancariaId;
    @Column(name="financial_movement_id") private UUID movimientoFinancieroId;
    @Column(length=100) private String referencia;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private EstadoPagoProveedor status;
    @Column(name="idempotency_key",nullable=false,updatable=false) private UUID claveIdempotencia;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="voided_at") private OffsetDateTime anuladoEn;
    @Column(name="voided_by") private UUID anuladoPor;
    @Column(name="void_reason",length=500) private String motivoAnulacion;
    @Version @Column(nullable=false) private long version;

    protected PagoProveedor() {}

    public PagoProveedor(UUID tenantId,UUID empresaId,UUID proveedorId,UUID facturaId,LocalDate fecha,
            UUID monedaId,BigDecimal monto,TipoCuentaDinero tipoFuente,UUID fuenteId,String referencia,
            UUID claveIdempotencia,UUID usuarioId) {
        this.tenantId=tenantId;this.empresaId=empresaId;this.proveedorId=proveedorId;this.facturaId=facturaId;
        this.fecha=fecha;this.monedaId=monedaId;this.monto=monto;this.tipoFuente=tipoFuente;
        this.cajaId=tipoFuente==TipoCuentaDinero.CASH_REGISTER?fuenteId:null;
        this.cuentaBancariaId=tipoFuente==TipoCuentaDinero.BANK_ACCOUNT?fuenteId:null;
        this.referencia=referencia;this.claveIdempotencia=claveIdempotencia;this.creadoPor=usuarioId;
        this.status=EstadoPagoProveedor.REGISTERED;
    }

    public void vincularMovimiento(UUID id){movimientoFinancieroId=id;}
    public void anular(String motivo,UUID usuarioId){status=EstadoPagoProveedor.VOIDED;motivoAnulacion=motivo;
        anuladoPor=usuarioId;anuladoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public UUID getProveedorId(){return proveedorId;} public UUID getFacturaId(){return facturaId;}
    public LocalDate getFecha(){return fecha;} public UUID getMonedaId(){return monedaId;} public BigDecimal getMonto(){return monto;}
    public TipoCuentaDinero getTipoFuente(){return tipoFuente;} public UUID getCajaId(){return cajaId;}
    public UUID getCuentaBancariaId(){return cuentaBancariaId;} public UUID getMovimientoFinancieroId(){return movimientoFinancieroId;}
    public String getReferencia(){return referencia;} public EstadoPagoProveedor getEstado(){return status;}
    public UUID getClaveIdempotencia(){return claveIdempotencia;} public long getVersion(){return version;}
}
