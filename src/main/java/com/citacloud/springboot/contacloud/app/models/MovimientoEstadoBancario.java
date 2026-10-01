package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="bank_statement_movement")
public class MovimientoEstadoBancario {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="bank_reconciliation_id",nullable=false,updatable=false) private UUID conciliacionId;
    @Column(name="bank_account_id",nullable=false,updatable=false) private UUID cuentaBancariaId;
    @Column(name="movement_date",nullable=false) private LocalDate fecha;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private DireccionMovimientoBancario direction;
    @Column(nullable=false,length=180) private String description;
    @Column(length=100) private String reference;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(name="source_type",nullable=false,updatable=false,length=20) private FuenteMovimientoBancario fuente;
    @Column(name="source_reference",updatable=false,length=250) private String referenciaFuente;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    protected MovimientoEstadoBancario() {}
    public MovimientoEstadoBancario(UUID tenantId,UUID empresaId,UUID conciliacionId,UUID cuentaBancariaId,
            LocalDate fecha,DireccionMovimientoBancario direction,String description,String reference,
            BigDecimal amount,UUID usuarioId){this.tenantId=tenantId;this.empresaId=empresaId;
        this.conciliacionId=conciliacionId;this.cuentaBancariaId=cuentaBancariaId;this.fecha=fecha;
        this.direction=direction;this.description=description;this.reference=reference;this.amount=amount;
        this.fuente=FuenteMovimientoBancario.MANUAL;this.creadoPor=usuarioId;}
    public void corregir(LocalDate fecha,DireccionMovimientoBancario direction,String description,
            String reference,BigDecimal amount){
        this.fecha=fecha;this.direction=direction;this.description=description;
        this.reference=reference;this.amount=amount;
    }
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public UUID getConciliacionId(){return conciliacionId;} public UUID getCuentaBancariaId(){return cuentaBancariaId;}
    public LocalDate getFecha(){return fecha;} public DireccionMovimientoBancario getDirection(){return direction;}
    public String getDescription(){return description;} public String getReference(){return reference;}
    public BigDecimal getAmount(){return amount;} public FuenteMovimientoBancario getFuente(){return fuente;}
    public String getReferenciaFuente(){return referenciaFuente;} public OffsetDateTime getCreadoEn(){return creadoEn;}
    public UUID getCreadoPor(){return creadoPor;}
}
