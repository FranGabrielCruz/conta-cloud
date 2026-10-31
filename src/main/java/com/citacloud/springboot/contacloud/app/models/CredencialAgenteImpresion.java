package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="print_agent_credentials")
public class CredencialAgenteImpresion {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="agent_id",nullable=false,updatable=false) private UUID agenteId;
    @Column(name="access_token_hash",nullable=false,updatable=false,length=64) private String hashAcceso;
    @Column(name="access_expires_at",nullable=false,updatable=false) private OffsetDateTime accesoExpiraEn;
    @Column(name="refresh_token_hash",nullable=false,updatable=false,length=64) private String hashRenovacion;
    @Column(name="refresh_expires_at",nullable=false,updatable=false) private OffsetDateTime renovacionExpiraEn;
    @Column(name="last_authenticated_at") private OffsetDateTime ultimaAutenticacion;
    @Column(name="revoked_at") private OffsetDateTime revocadaEn;
    @Column(name="rotated_from_id") private UUID rotadaDesdeId;
    @Version private long version;
    protected CredencialAgenteImpresion(){}
    public CredencialAgenteImpresion(UUID tenantId,UUID empresaId,UUID agenteId,String hashAcceso,OffsetDateTime accesoExpiraEn,String hashRenovacion,OffsetDateTime renovacionExpiraEn,UUID rotadaDesdeId){this.tenantId=tenantId;this.empresaId=empresaId;this.agenteId=agenteId;this.hashAcceso=hashAcceso;this.accesoExpiraEn=accesoExpiraEn;this.hashRenovacion=hashRenovacion;this.renovacionExpiraEn=renovacionExpiraEn;this.rotadaDesdeId=rotadaDesdeId;}
    public boolean accesoValido(OffsetDateTime ahora){return revocadaEn==null&&accesoExpiraEn.isAfter(ahora);}
    public boolean renovacionValida(OffsetDateTime ahora){return revocadaEn==null&&renovacionExpiraEn.isAfter(ahora);}
    public void autenticar(OffsetDateTime ahora){ultimaAutenticacion=ahora;}
    public void revocar(OffsetDateTime ahora){if(revocadaEn==null)revocadaEn=ahora;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;} public UUID getAgenteId(){return agenteId;} public String getHashAcceso(){return hashAcceso;} public OffsetDateTime getAccesoExpiraEn(){return accesoExpiraEn;} public String getHashRenovacion(){return hashRenovacion;} public OffsetDateTime getRenovacionExpiraEn(){return renovacionExpiraEn;} public OffsetDateTime getRevocadaEn(){return revocadaEn;}
}
