package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_payment")
public class PagoVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="sales_invoice_id",nullable=false,updatable=false) private UUID facturaId;
    @Column(name="cash_register_id",nullable=false) private UUID cajaId;
    @Column(name="cash_register_session_id",nullable=false) private UUID sesionCajaId;
    @Column(name="total_amount",nullable=false,precision=19,scale=4) private BigDecimal total;
    @Column(name="applied_amount",nullable=false,precision=19,scale=4) private BigDecimal aplicado;
    @Column(name="change_amount",nullable=false,precision=19,scale=4) private BigDecimal cambio;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoCobroVenta status;
    @Column(name="confirmed_at",nullable=false) private OffsetDateTime confirmadoEn;
    @Column(name="confirmed_by",nullable=false) private UUID confirmadoPor;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    protected PagoVenta(){}
    public PagoVenta(FacturaVenta f,BigDecimal aplicado,BigDecimal cambio,UUID usuario){tenantId=f.getTenantId();empresaId=f.getEmpresaId();facturaId=f.getId();cajaId=f.getCajaId();
        sesionCajaId=f.getSesionCajaId();total=f.getTotal();this.aplicado=aplicado;this.cambio=cambio;status=EstadoCobroVenta.CONFIRMED;
        confirmadoEn=OffsetDateTime.now();confirmadoPor=usuario;creadoPor=usuario;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public UUID getFacturaId(){return facturaId;} public BigDecimal getTotal(){return total;} public BigDecimal getAplicado(){return aplicado;}
    public BigDecimal getCambio(){return cambio;}
}
