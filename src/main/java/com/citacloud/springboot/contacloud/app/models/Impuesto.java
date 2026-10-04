package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="impuestos",uniqueConstraints=@UniqueConstraint(name="uk_impuestos_nombre_normalizado",columnNames={"tenant_id","empresa_id","nombre_normalizado"}))
public class Impuesto {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(nullable=false,length=30,updatable=false) private String codigo;
    @Column(nullable=false,length=120) private String nombre;
    @Column(name="nombre_normalizado",nullable=false,length=120) private String nombreNormalizado;
    @Column(nullable=false,precision=9,scale=6) private BigDecimal porcentaje;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private TipoImpuesto tipo;
    @Column(length=500) private String descripcion;
    @Column(nullable=false) private boolean activo=true;
    @ColumnDefault("CURRENT_TIMESTAMP") @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by") private UUID actualizadoPor;
    @Version @Column(nullable=false) private long version;
    protected Impuesto(){}
    public Impuesto(UUID tenantId,UUID empresaId,String codigo,String nombre,String nombreNormalizado,BigDecimal porcentaje,TipoImpuesto tipo,String descripcion,boolean activo,UUID usuarioId){this.tenantId=tenantId;this.empresaId=empresaId;this.codigo=codigo;this.nombre=nombre;this.nombreNormalizado=nombreNormalizado;this.porcentaje=porcentaje;this.tipo=tipo;this.descripcion=descripcion;this.activo=activo;this.creadoPor=usuarioId;this.actualizadoPor=usuarioId;this.actualizadoEn=OffsetDateTime.now();}
    /** Compatibilidad para creaciones internas anteriores. */
    public Impuesto(UUID tenantId,UUID empresaId,String codigo,String nombre,BigDecimal porcentaje,String descripcion){this(tenantId,empresaId,codigo,nombre,nombre.toLowerCase(java.util.Locale.ROOT),porcentaje,TipoImpuesto.PERCENTAGE,descripcion,true,null);}
    public void actualizar(String nombre,String nombreNormalizado,BigDecimal porcentaje,TipoImpuesto tipo,String descripcion,UUID usuarioId){this.nombre=nombre;this.nombreNormalizado=nombreNormalizado;this.porcentaje=porcentaje;this.tipo=tipo;this.descripcion=descripcion;this.actualizadoPor=usuarioId;}
    public void cambiarEstado(boolean activo,UUID usuarioId){this.activo=activo;this.actualizadoPor=usuarioId;}
    @PreUpdate void marcarActualizacion(){actualizadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;} public String getCodigo(){return codigo;} public String getNombre(){return nombre;} public String getNombreNormalizado(){return nombreNormalizado;} public BigDecimal getPorcentaje(){return porcentaje;} public TipoImpuesto getTipo(){return tipo;} public String getDescripcion(){return descripcion;} public boolean isActivo(){return activo;} public long getVersion(){return version;}
}
