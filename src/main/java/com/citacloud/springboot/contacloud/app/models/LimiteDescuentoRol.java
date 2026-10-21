package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_discount_role_limit")
public class LimiteDescuentoRol {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="role_id",nullable=false,updatable=false) private UUID rolId;
    @Column(name="max_percentage",precision=9,scale=4) private BigDecimal porcentajeMaximo;
    @Column(name="max_amount",precision=19,scale=4) private BigDecimal montoMaximo;
    @Column(nullable=false) private boolean active=true;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    @Column(name="created_by",updatable=false) private UUID creadoPor;
    @Column(name="updated_at",nullable=false) private OffsetDateTime actualizadoEn;
    @Column(name="updated_by") private UUID actualizadoPor;
    @Version private long version;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="role_id",insertable=false,updatable=false) private Rol rol;
    protected LimiteDescuentoRol(){}
    public LimiteDescuentoRol(UUID tenant,UUID empresa,UUID rol,BigDecimal porcentaje,BigDecimal monto,UUID usuario){tenantId=tenant;empresaId=empresa;rolId=rol;porcentajeMaximo=porcentaje;montoMaximo=monto;creadoPor=usuario;actualizadoPor=usuario;actualizadoEn=OffsetDateTime.now();}
    public void actualizar(BigDecimal porcentaje,BigDecimal monto,boolean activo,UUID usuario){porcentajeMaximo=porcentaje;montoMaximo=monto;active=activo;actualizadoPor=usuario;actualizadoEn=OffsetDateTime.now();}
    public UUID getId(){return id;}public UUID getRolId(){return rolId;}public Rol getRol(){return rol;}public BigDecimal getPorcentajeMaximo(){return porcentajeMaximo;}public BigDecimal getMontoMaximo(){return montoMaximo;}public boolean isActivo(){return active;}public long getVersion(){return version;}
}
