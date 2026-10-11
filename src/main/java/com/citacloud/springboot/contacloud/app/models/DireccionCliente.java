package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="customer_address")
public class DireccionCliente {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="customer_id",nullable=false,updatable=false) private UUID clienteId;
    @Enumerated(EnumType.STRING) @Column(name="address_type",nullable=false,length=20) private TipoDireccionCliente tipo;
    @Column(nullable=false,length=100) private String country;
    @Column(length=100) private String province; @Column(length=100) private String city; @Column(length=100) private String sector;
    @Column(nullable=false,length=500) private String address; @Column(name="postal_code",length=20) private String postalCode;
    @Column(nullable=false) private boolean principal; @Column(nullable=false) private boolean active;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime createdAt;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt; @Column(name="updated_by",nullable=false) private UUID updatedBy;
    @Version @Column(nullable=false) private long version;
    protected DireccionCliente(){}
    public DireccionCliente(UUID tenantId,UUID empresaId,UUID clienteId,TipoDireccionCliente tipo,String pais,String provincia,
            String ciudad,String sector,String direccion,String codigoPostal,boolean principal,UUID usuario){this.tenantId=tenantId;
        this.empresaId=empresaId;this.clienteId=clienteId;this.createdBy=usuario;actualizar(tipo,pais,provincia,ciudad,sector,direccion,codigoPostal,principal,usuario);this.active=true;}
    public void actualizar(TipoDireccionCliente tipo,String pais,String provincia,String ciudad,String sector,String direccion,
            String codigoPostal,boolean principal,UUID usuario){this.tipo=tipo;this.country=pais;this.province=provincia;this.city=ciudad;
        this.sector=sector;this.address=direccion;this.postalCode=codigoPostal;this.principal=principal;this.updatedBy=usuario;this.updatedAt=OffsetDateTime.now();}
    public void setPrincipal(boolean valor,UUID usuario){principal=valor;updatedBy=usuario;} public void desactivar(UUID usuario){active=false;principal=false;updatedBy=usuario;}
    @PreUpdate void mark(){updatedAt=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getClienteId(){return clienteId;} public TipoDireccionCliente getTipo(){return tipo;}
    public String getPais(){return country;} public String getProvincia(){return province;} public String getCiudad(){return city;}
    public String getSector(){return sector;} public String getDireccion(){return address;} public String getCodigoPostal(){return postalCode;}
    public boolean isPrincipal(){return principal;} public boolean isActivo(){return active;} public long getVersion(){return version;}
}
