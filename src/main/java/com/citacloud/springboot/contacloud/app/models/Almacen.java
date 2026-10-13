package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="warehouse")
public class Almacen {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="code",nullable=false,updatable=false,length=30) private String codigo;
    @Column(name="name",nullable=false,length=120) private String nombre;
    @Column(name="normalized_name",nullable=false,length=120) private String nombreNormalizado;
    @Column(name="branch_id",nullable=false) private UUID sucursalId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="branch_id",insertable=false,updatable=false) private Sucursal sucursal;
    @Enumerated(EnumType.STRING) @Column(name="warehouse_type",nullable=false,length=20) private TipoAlmacen tipo;
    @Column(name="responsible_user_id") private UUID responsableId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="responsible_user_id",insertable=false,updatable=false) private Usuario responsable;
    @Column(name="address",length=500) private String direccion;
    @Column(name="observation",length=1000) private String observacion;
    @Column(name="active",nullable=false) private boolean activo;
    @Column(name="system_managed",nullable=false) private boolean administradoSistema;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by") private UUID creadoPor;
    @Column(name="updated_at",nullable=false,insertable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by") private UUID actualizadoPor;
    @Version @Column(nullable=false) private long version;

    protected Almacen() {}

    public Almacen(UUID tenantId,UUID empresaId,String codigo,String nombre,String nombreNormalizado,
                   UUID sucursalId,TipoAlmacen tipo,UUID responsableId,String direccion,String observacion,UUID usuarioId) {
        this.tenantId=tenantId;this.empresaId=empresaId;this.codigo=codigo;this.nombre=nombre;
        this.nombreNormalizado=nombreNormalizado;this.sucursalId=sucursalId;this.tipo=tipo;
        this.responsableId=responsableId;this.direccion=direccion;this.observacion=observacion;
        this.activo=true;this.creadoPor=usuarioId;this.actualizadoPor=usuarioId;
    }

    public void actualizar(String nombre,String nombreNormalizado,UUID sucursalId,TipoAlmacen tipo,
                           UUID responsableId,String direccion,String observacion,UUID usuarioId) {
        this.nombre=nombre;this.nombreNormalizado=nombreNormalizado;this.sucursalId=sucursalId;this.tipo=tipo;
        this.responsableId=responsableId;this.direccion=direccion;this.observacion=observacion;this.actualizadoPor=usuarioId;
    }

    public void cambiarEstado(boolean activo,UUID usuarioId){this.activo=activo;this.actualizadoPor=usuarioId;}
    public void asignarReferencias(Sucursal sucursal,Usuario responsable){this.sucursal=sucursal;this.responsable=responsable;}
    @PreUpdate void marcarActualizacion(){actualizadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getCodigo(){return codigo;} public String getNombre(){return nombre;}
    public String getNombreNormalizado(){return nombreNormalizado;} public UUID getSucursalId(){return sucursalId;}
    public Sucursal getSucursal(){return sucursal;} public TipoAlmacen getTipo(){return tipo;}
    public UUID getResponsableId(){return responsableId;} public Usuario getResponsable(){return responsable;}
    public String getDireccion(){return direccion;} public String getObservacion(){return observacion;}
    public boolean isAdministradoSistema(){return administradoSistema;}
    public boolean isActivo(){return activo;} public OffsetDateTime getCreadoEn(){return creadoEn;}
    public OffsetDateTime getActualizadoEn(){return actualizadoEn;} public long getVersion(){return version;}
}
