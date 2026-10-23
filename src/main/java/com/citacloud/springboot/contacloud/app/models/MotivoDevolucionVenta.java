package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="sales_return_reason")
public class MotivoDevolucionVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="code",nullable=false,length=40) private String codigo;
    @Column(name="name",nullable=false,length=150) private String nombre;
    @Column(name="requires_observation",nullable=false) private boolean requiereObservacion;
    @Column(name="active",nullable=false) private boolean activo;
    protected MotivoDevolucionVenta(){}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getCodigo(){return codigo;} public String getNombre(){return nombre;}
    public boolean isRequiereObservacion(){return requiereObservacion;} public boolean isActivo(){return activo;}
}
