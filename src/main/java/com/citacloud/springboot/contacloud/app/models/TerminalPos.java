package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="pos_terminal")
public class TerminalPos {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="branch_id",nullable=false,updatable=false) private UUID sucursalId;
    @Column(name="cash_register_id",nullable=false,updatable=false) private UUID cajaId;
    @Column(name="token_hash",nullable=false,updatable=false,length=64) private String tokenHash;
    @Column(name="name",nullable=false,length=120) private String nombre;
    @Column(name="active",nullable=false) private boolean activo=true;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="last_used_at") private OffsetDateTime ultimoUsoEn;
    protected TerminalPos(){}
    public TerminalPos(UUID tenantId,UUID empresaId,UUID sucursalId,UUID cajaId,String tokenHash,String nombre,UUID usuarioId){
        this.tenantId=tenantId;this.empresaId=empresaId;this.sucursalId=sucursalId;this.cajaId=cajaId;
        this.tokenHash=tokenHash;this.nombre=nombre;this.creadoPor=usuarioId;
    }
    public void registrarUso(){ultimoUsoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public UUID getSucursalId(){return sucursalId;} public UUID getCajaId(){return cajaId;} public String getNombre(){return nombre;}
    public boolean isActivo(){return activo;} public OffsetDateTime getCreadoEn(){return creadoEn;}
}
