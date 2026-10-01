package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="bank_reconciliation_match")
public class AsociacionConciliacionBancaria {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="bank_reconciliation_id",nullable=false,updatable=false) private UUID conciliacionId;
    @Column(name="financial_movement_id",nullable=false,updatable=false) private UUID movimientoFinancieroId;
    @Column(name="bank_statement_movement_id",nullable=false,updatable=false) private UUID movimientoBancarioId;
    @Column(name="matched_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime conciliadoEn;
    @Column(name="matched_by",nullable=false,updatable=false) private UUID conciliadoPor;
    protected AsociacionConciliacionBancaria() {}
    public AsociacionConciliacionBancaria(UUID tenantId,UUID empresaId,UUID conciliacionId,
            UUID financieroId,UUID bancarioId,UUID usuarioId){this.tenantId=tenantId;this.empresaId=empresaId;
        this.conciliacionId=conciliacionId;this.movimientoFinancieroId=financieroId;
        this.movimientoBancarioId=bancarioId;this.conciliadoPor=usuarioId;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public UUID getConciliacionId(){return conciliacionId;} public UUID getMovimientoFinancieroId(){return movimientoFinancieroId;}
    public UUID getMovimientoBancarioId(){return movimientoBancarioId;} public OffsetDateTime getConciliadoEn(){return conciliadoEn;}
    public UUID getConciliadoPor(){return conciliadoPor;}
}
