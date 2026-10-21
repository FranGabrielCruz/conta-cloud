package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_discount_reason")
public class MotivoDescuentoVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="name",nullable=false,length=120) private String nombre;
    @Column(name="normalized_name",nullable=false,length=120) private String nombreNormalizado;
    @Column(name="other_reason",nullable=false) private boolean otro;
    @Column(name="active",nullable=false) private boolean activo=true;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by") private UUID actualizadoPor;
    @Version private long version;
    protected MotivoDescuentoVenta(){}
    public MotivoDescuentoVenta(UUID tenant,UUID empresa,String nombre,String normalizado,boolean otro,UUID usuario){tenantId=tenant;empresaId=empresa;this.nombre=nombre;nombreNormalizado=normalizado;this.otro=otro;creadoPor=usuario;actualizadoPor=usuario;actualizadoEn=OffsetDateTime.now();}
    public void actualizar(String nombre,String normalizado,boolean otro,boolean activo,UUID usuario){this.nombre=nombre;nombreNormalizado=normalizado;this.otro=otro;this.activo=activo;actualizadoPor=usuario;actualizadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;}public UUID getTenantId(){return tenantId;}public UUID getEmpresaId(){return empresaId;}public String getNombre(){return nombre;}public boolean isOtro(){return otro;}public boolean isActivo(){return activo;}public long getVersion(){return version;}
}
