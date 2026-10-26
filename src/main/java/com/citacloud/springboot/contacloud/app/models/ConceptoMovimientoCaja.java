package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="cash_movement_concepts")
public class ConceptoMovimientoCaja {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="code",nullable=false,length=40) private String codigo;
    @Column(name="name",nullable=false,length=120) private String nombre;
    @Enumerated(EnumType.STRING) @Column(name="movement_type",nullable=false,length=10)
    private TipoMovimientoFinanciero tipo;
    @Column(name="requires_observation",nullable=false) private boolean requiereObservacion;
    @Column(nullable=false) private boolean active;

    protected ConceptoMovimientoCaja(){}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;}
    public UUID getEmpresaId(){return empresaId;} public String getCodigo(){return codigo;}
    public String getNombre(){return nombre;} public TipoMovimientoFinanciero getTipo(){return tipo;}
    public boolean isRequiereObservacion(){return requiereObservacion;} public boolean isActivo(){return active;}
}
