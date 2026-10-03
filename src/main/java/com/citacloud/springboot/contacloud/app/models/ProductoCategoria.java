package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="product_category")
public class ProductoCategoria {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="name",nullable=false,length=120) private String nombre;
    @Column(name="description",length=500) private String descripcion;
    @Column(name="active",nullable=false) private boolean activo=true;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by") private UUID actualizadoPor;
    @Version @Column(nullable=false) private long version;
    protected ProductoCategoria(){}
    public ProductoCategoria(UUID tenantId,UUID empresaId,String nombre,String descripcion,UUID usuarioId){this.tenantId=tenantId;this.empresaId=empresaId;this.nombre=nombre;this.descripcion=descripcion;this.creadoPor=usuarioId;this.actualizadoPor=usuarioId;this.actualizadoEn=OffsetDateTime.now();}
    @PreUpdate void marca(){actualizadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getNombre(){return nombre;} public String getDescripcion(){return descripcion;} public boolean isActivo(){return activo;}
}
