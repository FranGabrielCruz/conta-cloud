package com.citacloud.springboot.contacloud.app.models;
import jakarta.persistence.*;import java.util.UUID;
@Entity @Table(name="inventory_adjustment_reason") public class MotivoAjusteInventario{
 @Id private UUID id;@Column(name="tenant_id",nullable=false,updatable=false)private UUID tenantId;@Column(name="empresa_id",nullable=false,updatable=false)private UUID empresaId;
 @Column(nullable=false,length=40)private String code;@Column(nullable=false,length=150)private String name;@Enumerated(EnumType.STRING)@Column(name="allowed_direction",nullable=false,length=10)private DireccionMotivoAjuste direccion;
 @Column(name="requires_notes",nullable=false)private boolean requiereObservacion;@Column(nullable=false)private boolean active;
 protected MotivoAjusteInventario(){} public UUID getId(){return id;}public UUID getTenantId(){return tenantId;}public UUID getEmpresaId(){return empresaId;}public String getCodigo(){return code;}public String getNombre(){return name;}public DireccionMotivoAjuste getDireccion(){return direccion;}public boolean isRequiereObservacion(){return requiereObservacion;}public boolean isActivo(){return active;}
}
