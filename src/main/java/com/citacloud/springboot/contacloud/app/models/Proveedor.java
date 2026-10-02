package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="supplier")
public class Proveedor {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="code",nullable=false,updatable=false,length=24) private String codigo;
    @Column(name="trade_name",nullable=false,length=150) private String nombreComercial;
    @Column(name="legal_name",length=180) private String razonSocial;
    @Column(name="tax_identification",length=50) private String identificacionFiscal;
    @Column(name="tax_identification_normalized",length=50) private String identificacionFiscalNormalizada;
    @Enumerated(EnumType.STRING) @Column(name="supplier_type",nullable=false,length=20) private TipoProveedor tipo;
    @Column(name="phone",length=30) private String telefono;
    @Column(name="email",length=180) private String correo;
    @Column(name="payment_term_id") private UUID condicionPagoId;
    @Column(name="currency_id") private UUID monedaId;
    @Column(name="address",length=500) private String direccion;
    @Column(name="contact_name",length=150) private String contacto;
    @Column(name="contact_phone",length=30) private String telefonoContacto;
    @Column(name="notes",length=1000) private String notas;
    @Column(name="active",nullable=false) private boolean activo;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",nullable=false,updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by",nullable=false) private UUID actualizadoPor;
    @Version @Column(nullable=false) private long version;

    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_term_id",insertable=false,updatable=false)
    private CondicionPago condicionPago;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="currency_id",insertable=false,updatable=false)
    private Moneda moneda;

    protected Proveedor() {}
    public Proveedor(UUID tenantId,UUID empresaId,String codigo,String nombreComercial,String razonSocial,
            String identificacionFiscal,String identificacionFiscalNormalizada,TipoProveedor tipo,String telefono,
            String correo,UUID condicionPagoId,UUID monedaId,String direccion,String contacto,
            String telefonoContacto,String notas,boolean activo,UUID usuarioId){
        this.tenantId=tenantId;this.empresaId=empresaId;this.codigo=codigo;this.nombreComercial=nombreComercial;
        this.razonSocial=razonSocial;this.identificacionFiscal=identificacionFiscal;
        this.identificacionFiscalNormalizada=identificacionFiscalNormalizada;this.tipo=tipo;this.telefono=telefono;
        this.correo=correo;this.condicionPagoId=condicionPagoId;this.monedaId=monedaId;this.direccion=direccion;
        this.contacto=contacto;this.telefonoContacto=telefonoContacto;this.notas=notas;this.activo=activo;
        this.creadoPor=usuarioId;this.actualizadoPor=usuarioId;this.actualizadoEn=OffsetDateTime.now();
    }
    @PreUpdate void actualizarMarca(){actualizadoEn=OffsetDateTime.now();}
    public void actualizar(String nombreComercial,String razonSocial,String identificacionFiscal,
            String identificacionFiscalNormalizada,TipoProveedor tipo,String telefono,String correo,
            UUID condicionPagoId,UUID monedaId,String direccion,String contacto,String telefonoContacto,
            String notas,UUID usuarioId){
        this.nombreComercial=nombreComercial;this.razonSocial=razonSocial;this.identificacionFiscal=identificacionFiscal;
        this.identificacionFiscalNormalizada=identificacionFiscalNormalizada;this.tipo=tipo;this.telefono=telefono;
        this.correo=correo;this.condicionPagoId=condicionPagoId;this.monedaId=monedaId;this.direccion=direccion;
        this.contacto=contacto;this.telefonoContacto=telefonoContacto;this.notas=notas;this.actualizadoPor=usuarioId;
    }
    public void cambiarEstado(boolean activo,UUID usuarioId){this.activo=activo;this.actualizadoPor=usuarioId;}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getCodigo(){return codigo;} public String getNombreComercial(){return nombreComercial;}
    public String getRazonSocial(){return razonSocial;} public String getIdentificacionFiscal(){return identificacionFiscal;}
    public String getIdentificacionFiscalNormalizada(){return identificacionFiscalNormalizada;}
    public TipoProveedor getTipo(){return tipo;} public String getTelefono(){return telefono;} public String getCorreo(){return correo;}
    public UUID getCondicionPagoId(){return condicionPagoId;} public UUID getMonedaId(){return monedaId;}
    public String getDireccion(){return direccion;} public String getContacto(){return contacto;}
    public String getTelefonoContacto(){return telefonoContacto;} public String getNotas(){return notas;}
    public boolean isActivo(){return activo;} public UUID getCreadoPor(){return creadoPor;}
    public UUID getActualizadoPor(){return actualizadoPor;} public long getVersion(){return version;}
    public CondicionPago getCondicionPago(){return condicionPago;} public Moneda getMoneda(){return moneda;}
}
