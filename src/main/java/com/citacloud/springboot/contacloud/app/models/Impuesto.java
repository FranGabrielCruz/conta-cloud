package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="impuestos")
public class Impuesto {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false) private UUID empresaId;
    @Column(nullable=false,length=30) private String codigo;
    @Column(nullable=false,length=100) private String nombre;
    @Column(nullable=false,precision=9,scale=4) private BigDecimal porcentaje;
    @Column(nullable=false,length=30) private String tipo;
    @Column(length=500) private String descripcion;
    @Column(nullable=false) private boolean activo=true;
    protected Impuesto(){}
    public Impuesto(UUID tenantId,UUID empresaId,String codigo,String nombre,BigDecimal porcentaje,String descripcion){this.tenantId=tenantId;this.empresaId=empresaId;this.codigo=codigo;this.nombre=nombre;this.porcentaje=porcentaje;this.tipo="PORCENTAJE";this.descripcion=descripcion;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;} public String getCodigo(){return codigo;} public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;} public BigDecimal getPorcentaje(){return porcentaje;} public void setPorcentaje(BigDecimal v){porcentaje=v;} public String getTipo(){return tipo;} public String getDescripcion(){return descripcion;} public void setDescripcion(String v){descripcion=v;} public boolean isActivo(){return activo;} public void setActivo(boolean v){activo=v;}
}
