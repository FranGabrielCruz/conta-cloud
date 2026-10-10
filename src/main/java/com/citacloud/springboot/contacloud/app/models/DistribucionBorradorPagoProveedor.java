package com.citacloud.springboot.contacloud.app.models;
import jakarta.persistence.*;import java.math.BigDecimal;import java.util.UUID;
@Entity @Table(name="supplier_payment_draft_allocation") public class DistribucionBorradorPagoProveedor{
 @Id @GeneratedValue(strategy=GenerationType.UUID)private UUID id;@Column(name="tenant_id",nullable=false)private UUID tenantId;@Column(name="empresa_id",nullable=false)private UUID empresaId;@Column(name="supplier_payment_id",nullable=false)private UUID pagoId;@Column(name="purchase_invoice_id",nullable=false)private UUID facturaId;@Column(nullable=false,precision=19,scale=4)private BigDecimal amount;
 protected DistribucionBorradorPagoProveedor(){}public DistribucionBorradorPagoProveedor(UUID t,UUID e,UUID p,UUID f,BigDecimal m){tenantId=t;empresaId=e;pagoId=p;facturaId=f;amount=m;}public UUID getId(){return id;}public UUID getPagoId(){return pagoId;}public UUID getFacturaId(){return facturaId;}public BigDecimal getMonto(){return amount;}
}
