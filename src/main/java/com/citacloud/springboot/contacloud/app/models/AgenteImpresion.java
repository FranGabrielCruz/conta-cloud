package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="print_agents")
public class AgenteImpresion {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="sucursal_id",nullable=false,updatable=false) private UUID sucursalId;
    @Column(name="display_code",nullable=false,updatable=false,length=30) private String codigoVisible;
    @Column(name="name",nullable=false,length=150) private String nombre;
    @Enumerated(EnumType.STRING) @Column(name="status",nullable=false,length=20) private EstadoAgenteImpresion estado;
    @Column(name="machine_name",length=150) private String nombreMaquina;
    @Column(name="application_version",length=60) private String versionAplicacion;
    @Column(name="operating_system",length=150) private String sistemaOperativo;
    @Column(name="last_seen_at") private OffsetDateTime ultimaConexion;
    @Column(name="revoked_at") private OffsetDateTime revocadoEn;
    @Column(name="revoked_by") private UUID revocadoPor;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Version private long version;
    protected AgenteImpresion(){}
    public AgenteImpresion(UUID tenantId,UUID empresaId,UUID sucursalId,String codigoVisible,String nombre,UUID usuarioId){this.tenantId=tenantId;this.empresaId=empresaId;this.sucursalId=sucursalId;this.codigoVisible=codigoVisible;this.nombre=nombre;this.creadoPor=usuarioId;estado=EstadoAgenteImpresion.PENDING;actualizadoEn=OffsetDateTime.now();}
    public void vincular(String maquina,String version,String sistema){if(estado==EstadoAgenteImpresion.REVOKED)throw new IllegalStateException("Agente revocado");nombreMaquina=maquina;versionAplicacion=version;sistemaOperativo=sistema;estado=EstadoAgenteImpresion.ACTIVE;ultimaConexion=OffsetDateTime.now();actualizadoEn=ultimaConexion;}
    public void heartbeat(String version,OffsetDateTime ahora){if(estado!=EstadoAgenteImpresion.ACTIVE)throw new IllegalStateException("Agente inactivo");ultimaConexion=ahora;if(version!=null&&!version.isBlank())versionAplicacion=version;actualizadoEn=ahora;}
    public void revocar(UUID usuarioId){estado=EstadoAgenteImpresion.REVOKED;revocadoEn=OffsetDateTime.now();revocadoPor=usuarioId;actualizadoEn=revocadoEn;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;} public UUID getSucursalId(){return sucursalId;} public String getCodigoVisible(){return codigoVisible;} public String getNombre(){return nombre;} public EstadoAgenteImpresion getEstado(){return estado;} public String getNombreMaquina(){return nombreMaquina;} public String getVersionAplicacion(){return versionAplicacion;} public String getSistemaOperativo(){return sistemaOperativo;} public OffsetDateTime getUltimaConexion(){return ultimaConexion;} public OffsetDateTime getRevocadoEn(){return revocadoEn;} public long getVersion(){return version;}
}
