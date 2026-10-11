package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="customer_contact")
public class ContactoCliente {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="customer_id",nullable=false,updatable=false) private UUID clienteId;
    @Column(name="name",nullable=false,length=150) private String nombre; @Column(name="position",length=100) private String cargo;
    @Column(name="phone",length=30) private String telefono; @Column(name="email",length=180) private String correo;
    @Column(nullable=false) private boolean principal; @Column(nullable=false) private boolean active;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime createdAt;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt; @Column(name="updated_by",nullable=false) private UUID updatedBy;
    @Version @Column(nullable=false) private long version;
    protected ContactoCliente(){}
    public ContactoCliente(UUID tenantId,UUID empresaId,UUID clienteId,String nombre,String cargo,String telefono,String correo,boolean principal,UUID usuario){
        this.tenantId=tenantId;this.empresaId=empresaId;this.clienteId=clienteId;this.createdBy=usuario;actualizar(nombre,cargo,telefono,correo,principal,usuario);this.active=true;}
    public void actualizar(String nombre,String cargo,String telefono,String correo,boolean principal,UUID usuario){this.nombre=nombre;this.cargo=cargo;
        this.telefono=telefono;this.correo=correo;this.principal=principal;this.updatedBy=usuario;this.updatedAt=OffsetDateTime.now();}
    public void desactivar(UUID usuario){active=false;principal=false;updatedBy=usuario;} @PreUpdate void mark(){updatedAt=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getClienteId(){return clienteId;} public String getNombre(){return nombre;} public String getCargo(){return cargo;}
    public String getTelefono(){return telefono;} public String getCorreo(){return correo;} public boolean isPrincipal(){return principal;}
    public boolean isActivo(){return active;} public long getVersion(){return version;}
}
