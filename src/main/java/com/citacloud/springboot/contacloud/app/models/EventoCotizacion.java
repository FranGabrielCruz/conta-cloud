package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_quote_event")
public class EventoCotizacion {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sales_quote_id",nullable=false) private Cotizacion cotizacion;
    @Column(name="event_type",nullable=false,length=40) private String tipo;
    @Column(length=1000) private String detail;
    @Column(name="occurred_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime ocurridoEn;
    @Column(name="occurred_by",nullable=false,updatable=false) private UUID ocurridoPor;
    protected EventoCotizacion() {}
    public EventoCotizacion(UUID tenantId,UUID empresaId,Cotizacion cotizacion,String tipo,String detalle,UUID usuario){this.tenantId=tenantId;
        this.empresaId=empresaId;this.cotizacion=cotizacion;this.tipo=tipo;this.detail=detalle;this.ocurridoPor=usuario;}
    public String getTipo(){return tipo;} public String getDetalle(){return detail;} public OffsetDateTime getOcurridoEn(){return ocurridoEn;}
}
