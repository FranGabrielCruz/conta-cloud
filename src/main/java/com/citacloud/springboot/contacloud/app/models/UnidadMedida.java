package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="unit_of_measure")
public class UnidadMedida {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="name",nullable=false,length=80) private String nombre;
    @Column(name="normalized_name",nullable=false,length=80) private String nombreNormalizado;
    @Column(name="abbreviation",nullable=false,length=20) private String abreviatura;
    @Column(name="normalized_abbreviation",nullable=false,length=20) private String abreviaturaNormalizada;
    @Column(name="description",length=500) private String descripcion;
    @Column(name="active",nullable=false) private boolean activo=true;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by") private UUID actualizadoPor;
    @Version @Column(nullable=false) private long version;
    protected UnidadMedida(){}
    public UnidadMedida(UUID tenantId,UUID empresaId,String nombre,String nombreNormalizado,String abreviatura,String abreviaturaNormalizada,String descripcion,boolean activo,UUID usuarioId){this.tenantId=tenantId;this.empresaId=empresaId;this.nombre=nombre;this.nombreNormalizado=nombreNormalizado;this.abreviatura=abreviatura;this.abreviaturaNormalizada=abreviaturaNormalizada;this.descripcion=descripcion;this.activo=activo;this.creadoPor=usuarioId;this.actualizadoPor=usuarioId;this.actualizadoEn=OffsetDateTime.now();}
    public void actualizar(String nombre,String nombreNormalizado,String abreviatura,String abreviaturaNormalizada,String descripcion,UUID usuarioId){this.nombre=nombre;this.nombreNormalizado=nombreNormalizado;this.abreviatura=abreviatura;this.abreviaturaNormalizada=abreviaturaNormalizada;this.descripcion=descripcion;this.actualizadoPor=usuarioId;}
    public void cambiarEstado(boolean activo,UUID usuarioId){this.activo=activo;this.actualizadoPor=usuarioId;}
    @PreUpdate void marca(){actualizadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getNombre(){return nombre;} public String getNombreNormalizado(){return nombreNormalizado;} public String getAbreviatura(){return abreviatura;} public String getAbreviaturaNormalizada(){return abreviaturaNormalizada;} public String getDescripcion(){return descripcion;} public boolean isActivo(){return activo;} public long getVersion(){return version;}
}
