package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_discount_configuration")
public class ConfiguracionDescuentoVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(nullable=false) private boolean enabled=true;
    @Column(name="allow_sale_discount",nullable=false) private boolean permiteVenta=true;
    @Column(name="allow_line_discount",nullable=false) private boolean permiteLinea=true;
    @Column(name="allow_percentage",nullable=false) private boolean permitePorcentaje=true;
    @Column(name="allow_amount",nullable=false) private boolean permiteMonto=true;
    @Column(name="require_reason",nullable=false) private boolean requiereMotivo=true;
    @Column(name="allow_authorization",nullable=false) private boolean permiteAutorizacion=true;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by") private UUID actualizadoPor;
    @Version private long version;
    protected ConfiguracionDescuentoVenta(){}
    public ConfiguracionDescuentoVenta(UUID tenant,UUID empresa,UUID usuario){tenantId=tenant;empresaId=empresa;creadoPor=usuario;actualizadoPor=usuario;actualizadoEn=OffsetDateTime.now();}
    public void actualizar(boolean habilitado,boolean venta,boolean linea,boolean porcentaje,boolean monto,boolean motivo,boolean autorizacion,UUID usuario){enabled=habilitado;permiteVenta=venta;permiteLinea=linea;permitePorcentaje=porcentaje;permiteMonto=monto;requiereMotivo=motivo;permiteAutorizacion=autorizacion;actualizadoPor=usuario;actualizadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;}public UUID getTenantId(){return tenantId;}public UUID getEmpresaId(){return empresaId;}public boolean isEnabled(){return enabled;}public boolean isPermiteVenta(){return permiteVenta;}public boolean isPermiteLinea(){return permiteLinea;}public boolean isPermitePorcentaje(){return permitePorcentaje;}public boolean isPermiteMonto(){return permiteMonto;}public boolean isRequiereMotivo(){return requiereMotivo;}public boolean isPermiteAutorizacion(){return permiteAutorizacion;}public long getVersion(){return version;}
}
