package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_credit_note")
public class NotaCreditoVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="credit_note_number",nullable=false,updatable=false,length=30) private String numero;
    @Column(name="sales_return_id",nullable=false,updatable=false) private UUID devolucionId;
    @Column(name="sales_invoice_id",nullable=false,updatable=false) private UUID facturaId;
    @Column(name="customer_id") private UUID clienteId;
    @Column(name="currency_id",nullable=false) private UUID monedaId;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="applied_receivable_amount",nullable=false,precision=19,scale=4) private BigDecimal aplicadoCxC=BigDecimal.ZERO;
    @Column(name="customer_credit_amount",nullable=false,precision=19,scale=4) private BigDecimal creditoCliente;
    @Column(nullable=false,length=20) private String status="CONFIRMED";
    @Column(name="confirmed_at",nullable=false) private OffsetDateTime confirmadaEn;
    @Column(name="confirmed_by",nullable=false) private UUID confirmadaPor;
    protected NotaCreditoVenta(){}
    public NotaCreditoVenta(UUID tenant,UUID empresa,String numero,DevolucionVenta devolucion,BigDecimal total,UUID usuario){
        tenantId=tenant;empresaId=empresa;this.numero=numero;devolucionId=devolucion.getId();facturaId=devolucion.getFacturaId();
        clienteId=devolucion.getClienteId();monedaId=devolucion.getMonedaId();amount=total;creditoCliente=clienteId==null?BigDecimal.ZERO:total;
        confirmadaEn=OffsetDateTime.now();confirmadaPor=usuario;
    }
    public UUID getId(){return id;} public String getNumero(){return numero;} public BigDecimal getMonto(){return amount;}
    public BigDecimal getCreditoCliente(){return creditoCliente;} public UUID getDevolucionId(){return devolucionId;}
}
