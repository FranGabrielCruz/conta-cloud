package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="cash_voucher_concepts")
public class ConceptoValeCaja {
 @Id private UUID id;
 @Column(name="tenant_id",nullable=false) private UUID tenantId;
 @Column(name="empresa_id",nullable=false) private UUID empresaId;
 @Column(nullable=false,length=40) private String code;
 @Column(nullable=false,length=120) private String name;
 @Column(name="requires_reference",nullable=false) private boolean requiresReference;
 @Column(name="requires_observation",nullable=false) private boolean requiresObservation;
 @Column(nullable=false) private boolean active;
 protected ConceptoValeCaja(){}
 public UUID getId(){return id;} public String getCodigo(){return code;} public String getNombre(){return name;}
 public boolean isRequiereReferencia(){return requiresReference;} public boolean isRequiereObservacion(){return requiresObservation;}
}
