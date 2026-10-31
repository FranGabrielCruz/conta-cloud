package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="print_agent_pairings")
public class VinculacionAgenteImpresion {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="agent_id",nullable=false,updatable=false) private UUID agenteId;
    @Column(name="code_hash",nullable=false,updatable=false,length=64) private String hashCodigo;
    @Column(name="expires_at",nullable=false,updatable=false) private OffsetDateTime expiraEn;
    @Column(name="consumed_at") private OffsetDateTime consumidoEn;
    @Column(name="invalidated_at") private OffsetDateTime invalidadoEn;
    @Column(name="failed_attempts",nullable=false) private int intentosFallidos;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    @Version private long version;
    protected VinculacionAgenteImpresion(){}
    public VinculacionAgenteImpresion(UUID tenantId,UUID empresaId,UUID agenteId,String hashCodigo,OffsetDateTime expiraEn,UUID creadoPor){this.tenantId=tenantId;this.empresaId=empresaId;this.agenteId=agenteId;this.hashCodigo=hashCodigo;this.expiraEn=expiraEn;this.creadoPor=creadoPor;}
    public boolean utilizable(OffsetDateTime ahora,int maxIntentos){return consumidoEn==null&&invalidadoEn==null&&expiraEn.isAfter(ahora)&&intentosFallidos<maxIntentos;}
    public void consumir(OffsetDateTime ahora){consumidoEn=ahora;}
    public void invalidar(OffsetDateTime ahora){if(consumidoEn==null&&invalidadoEn==null)invalidadoEn=ahora;}
    public void fallo(int maxIntentos,OffsetDateTime ahora){intentosFallidos++;if(intentosFallidos>=maxIntentos)invalidar(ahora);}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;} public UUID getAgenteId(){return agenteId;} public String getHashCodigo(){return hashCodigo;} public OffsetDateTime getExpiraEn(){return expiraEn;} public OffsetDateTime getConsumidoEn(){return consumidoEn;} public OffsetDateTime getInvalidadoEn(){return invalidadoEn;} public int getIntentosFallidos(){return intentosFallidos;}
}
