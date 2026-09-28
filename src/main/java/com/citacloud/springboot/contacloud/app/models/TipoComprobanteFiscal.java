package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="tipos_comprobantes_fiscales")
public class TipoComprobanteFiscal {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false) private UUID empresaId;
    @Column(nullable=false,length=30) private String codigo;
    @Column(nullable=false,length=120) private String nombre;
    @Column(nullable=false,length=20) private String prefijo;
    @Column(nullable=false,length=40) private String tipo="FISCAL";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="reglas_secuencia") private String reglasSecuencia="{}";
    @Column(length=500) private String descripcion;
    @Column(nullable=false) private boolean activo=true;
    @Column(name="creado_en",nullable=false,updatable=false) private OffsetDateTime creadoEn=OffsetDateTime.now();
    @Column(name="actualizado_en",nullable=false) private OffsetDateTime actualizadoEn=OffsetDateTime.now();
    protected TipoComprobanteFiscal(){}
    public TipoComprobanteFiscal(UUID tenantId,UUID empresaId,String codigo,String nombre,String prefijo,String descripcion){this.tenantId=tenantId;this.empresaId=empresaId;this.codigo=codigo;this.nombre=nombre;this.prefijo=prefijo;this.descripcion=descripcion;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;} public String getCodigo(){return codigo;} public void setCodigo(String v){codigo=v;} public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;} public String getPrefijo(){return prefijo;} public void setPrefijo(String v){prefijo=v;} public String getDescripcion(){return descripcion;} public void setDescripcion(String v){descripcion=v;} public boolean isActivo(){return activo;} public void setActivo(boolean v){activo=v;}
    @PreUpdate void actualizarFecha(){actualizadoEn=OffsetDateTime.now();}
}
