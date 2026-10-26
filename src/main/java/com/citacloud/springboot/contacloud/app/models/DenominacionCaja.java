package com.citacloud.springboot.contacloud.app.models;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="cash_denominations")
public class DenominacionCaja {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
 @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
 @Column(name="currency_id",nullable=false,updatable=false) private UUID monedaId;
 @Column(name="denomination",nullable=false,precision=19,scale=4) private BigDecimal valor;
 @Enumerated(EnumType.STRING) @Column(name="denomination_type",nullable=false,length=10) private TipoDenominacionCaja tipo;
 @Column(name="display_order",nullable=false) private int orden; @Column(nullable=false) private boolean active;
 protected DenominacionCaja(){} public UUID getId(){return id;} public UUID getTenantId(){return tenantId;}
 public UUID getEmpresaId(){return empresaId;} public UUID getMonedaId(){return monedaId;} public BigDecimal getValor(){return valor;}
 public TipoDenominacionCaja getTipo(){return tipo;} public int getOrden(){return orden;} public boolean isActive(){return active;}
}
