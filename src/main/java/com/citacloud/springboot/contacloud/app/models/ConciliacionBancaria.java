package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="bank_reconciliation")
public class ConciliacionBancaria {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="bank_account_id",nullable=false,updatable=false) private UUID cuentaBancariaId;
    @Column(name="currency_id",nullable=false,updatable=false) private UUID monedaId;
    @Column(name="start_date",nullable=false,updatable=false) private LocalDate fechaInicial;
    @Column(name="end_date",nullable=false,updatable=false) private LocalDate fechaFinal;
    @Column(name="bank_opening_balance",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal saldoInicialBanco;
    @Column(name="bank_closing_balance",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal saldoFinalBanco;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoConciliacionBancaria status;
    @Version @Column(nullable=false) private long version;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="finalized_at") private OffsetDateTime finalizadoEn;
    @Column(name="finalized_by") private UUID finalizadoPor;
    @Column(name="voided_at") private OffsetDateTime anuladoEn;
    @Column(name="voided_by") private UUID anuladoPor;
    @Column(name="void_reason",length=500) private String motivoAnulacion;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="tenant_id",referencedColumnName="tenant_id",insertable=false,updatable=false),
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="bank_account_id",referencedColumnName="id",insertable=false,updatable=false)})
    private CuentaBancaria cuentaBancaria;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumns({
        @JoinColumn(name="empresa_id",referencedColumnName="empresa_id",insertable=false,updatable=false),
        @JoinColumn(name="currency_id",referencedColumnName="id",insertable=false,updatable=false)})
    private Moneda moneda;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="finalized_by",insertable=false,updatable=false)
    private Usuario usuarioFinalizacion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="voided_by",insertable=false,updatable=false)
    private Usuario usuarioAnulacion;

    protected ConciliacionBancaria() {}
    public ConciliacionBancaria(UUID tenantId,UUID empresaId,UUID cuentaBancariaId,UUID monedaId,
            LocalDate fechaInicial,LocalDate fechaFinal,BigDecimal saldoInicialBanco,
            BigDecimal saldoFinalBanco,UUID usuarioId){
        this.tenantId=tenantId;this.empresaId=empresaId;this.cuentaBancariaId=cuentaBancariaId;
        this.monedaId=monedaId;this.fechaInicial=fechaInicial;this.fechaFinal=fechaFinal;
        this.saldoInicialBanco=saldoInicialBanco;this.saldoFinalBanco=saldoFinalBanco;
        this.status=EstadoConciliacionBancaria.IN_PROGRESS;this.creadoPor=usuarioId;
    }
    public void asignarRelaciones(CuentaBancaria cuenta,Moneda moneda){this.cuentaBancaria=cuenta;this.moneda=moneda;}
    public void finalizar(UUID usuarioId){status=EstadoConciliacionBancaria.FINALIZED;finalizadoPor=usuarioId;finalizadoEn=OffsetDateTime.now();}
    public void anular(String motivo,UUID usuarioId){status=EstadoConciliacionBancaria.VOIDED;motivoAnulacion=motivo;
        anuladoPor=usuarioId;anuladoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public UUID getCuentaBancariaId(){return cuentaBancariaId;} public UUID getMonedaId(){return monedaId;}
    public LocalDate getFechaInicial(){return fechaInicial;} public LocalDate getFechaFinal(){return fechaFinal;}
    public BigDecimal getSaldoInicialBanco(){return saldoInicialBanco;} public BigDecimal getSaldoFinalBanco(){return saldoFinalBanco;}
    public EstadoConciliacionBancaria getStatus(){return status;} public long getVersion(){return version;}
    public OffsetDateTime getCreadoEn(){return creadoEn;} public UUID getCreadoPor(){return creadoPor;}
    public OffsetDateTime getFinalizadoEn(){return finalizadoEn;} public UUID getFinalizadoPor(){return finalizadoPor;}
    public OffsetDateTime getAnuladoEn(){return anuladoEn;} public UUID getAnuladoPor(){return anuladoPor;}
    public String getMotivoAnulacion(){return motivoAnulacion;} public CuentaBancaria getCuentaBancaria(){return cuentaBancaria;}
    public Moneda getMoneda(){return moneda;}
    public Usuario getUsuarioFinalizacion(){return usuarioFinalizacion;} public Usuario getUsuarioAnulacion(){return usuarioAnulacion;}
}
