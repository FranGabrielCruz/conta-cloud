package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="customer_credit_ledger")
public class CreditoCliente {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="customer_id",nullable=false,updatable=false) private UUID clienteId;
    @Column(name="currency_id",nullable=false,updatable=false) private UUID monedaId;
    @Column(name="sales_credit_note_id",nullable=false,updatable=false) private UUID notaCreditoId;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="available_amount",nullable=false,precision=19,scale=4) private BigDecimal disponible;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    protected CreditoCliente(){}
    public CreditoCliente(UUID tenant,UUID empresa,UUID cliente,UUID moneda,UUID nota,BigDecimal monto,UUID usuario){
        tenantId=tenant;empresaId=empresa;clienteId=cliente;monedaId=moneda;notaCreditoId=nota;amount=monto;disponible=monto;creadoPor=usuario;
    }
}
