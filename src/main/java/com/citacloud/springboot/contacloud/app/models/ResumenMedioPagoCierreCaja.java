package com.citacloud.springboot.contacloud.app.models;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="cash_closure_payment_summary")
public class ResumenMedioPagoCierreCaja {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId; @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId; @Column(name="cash_closure_id",nullable=false,updatable=false) private UUID cierreId; @Enumerated(EnumType.STRING) @Column(name="payment_method",nullable=false,updatable=false,length=20) private MedioPagoMovimiento medio; @Column(nullable=false,updatable=false,precision=19,scale=4) private BigDecimal amount; @Column(name="line_order",nullable=false,updatable=false) private int orden;
 protected ResumenMedioPagoCierreCaja(){} public ResumenMedioPagoCierreCaja(UUID t,UUID e,UUID c,MedioPagoMovimiento m,BigDecimal a,int o){tenantId=t;empresaId=e;cierreId=c;medio=m;amount=a;orden=o;} public MedioPagoMovimiento getMedio(){return medio;} public BigDecimal getAmount(){return amount;}
}
