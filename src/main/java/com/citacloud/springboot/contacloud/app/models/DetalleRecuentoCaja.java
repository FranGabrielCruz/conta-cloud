package com.citacloud.springboot.contacloud.app.models;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="cash_count_details")
public class DetalleRecuentoCaja {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId; @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
 @Column(name="cash_count_id",nullable=false,updatable=false) private UUID recuentoId; @Column(name="denomination_id",nullable=false,updatable=false) private UUID denominacionId;
 @Column(nullable=false,updatable=false,precision=19,scale=4) private BigDecimal denomination; @Enumerated(EnumType.STRING) @Column(name="denomination_type",nullable=false,updatable=false,length=10) private TipoDenominacionCaja tipo;
 @Column(nullable=false,updatable=false) private int quantity; @Column(nullable=false,updatable=false,precision=19,scale=4) private BigDecimal subtotal; @Column(name="line_order",nullable=false,updatable=false) private int orden;
 protected DetalleRecuentoCaja(){} public DetalleRecuentoCaja(UUID t,UUID e,UUID r,DenominacionCaja d,int q,int o){tenantId=t;empresaId=e;recuentoId=r;denominacionId=d.getId();denomination=d.getValor();tipo=d.getTipo();quantity=q;subtotal=denomination.multiply(BigDecimal.valueOf(q));orden=o;}
 public UUID getId(){return id;} public UUID getRecuentoId(){return recuentoId;} public UUID getDenominacionId(){return denominacionId;} public BigDecimal getDenomination(){return denomination;} public TipoDenominacionCaja getTipo(){return tipo;} public int getQuantity(){return quantity;} public BigDecimal getSubtotal(){return subtotal;} public int getOrden(){return orden;}
}
