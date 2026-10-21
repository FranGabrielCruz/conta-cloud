package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_discount_authorization")
public class AutorizacionDescuentoVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="operation_key",nullable=false,updatable=false) private UUID operacionId;
    @Column(name="requested_by",nullable=false,updatable=false) private UUID solicitadaPor;
    @Column(name="authorized_by",nullable=false,updatable=false) private UUID autorizadaPor;
    @Column(name="requester_role_id",nullable=false,updatable=false) private UUID rolSolicitanteId;
    @Column(name="authorizer_role_id",nullable=false,updatable=false) private UUID rolAutorizadorId;
    @Enumerated(EnumType.STRING) @Column(name="scope",nullable=false,updatable=false,length=20) private AlcanceDescuentoVenta alcance;
    @Enumerated(EnumType.STRING) @Column(name="discount_type",nullable=false,updatable=false,length=20) private TipoDescuentoVenta tipo;
    @Column(name="product_id",updatable=false) private UUID productoId;
    @Column(name="requested_value",nullable=false,updatable=false,precision=19,scale=6) private BigDecimal valor;
    @Column(name="eligible_base",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal baseElegible;
    @Column(name="discount_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal monto;
    @Column(name="requester_max_percentage",precision=9,scale=4,updatable=false) private BigDecimal porcentajeSolicitante;
    @Column(name="requester_max_amount",precision=19,scale=4,updatable=false) private BigDecimal montoSolicitante;
    @Column(name="authorizer_max_percentage",precision=9,scale=4,updatable=false) private BigDecimal porcentajeAutorizador;
    @Column(name="authorizer_max_amount",precision=19,scale=4,updatable=false) private BigDecimal montoAutorizador;
    @Column(name="reason_id",updatable=false) private UUID motivoId;
    @Column(name="reason_name",length=120,updatable=false) private String motivoNombre;
    @Column(name="reason_detail",length=500,updatable=false) private String motivoDetalle;
    @Column(nullable=false,updatable=false,length=64) private String fingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoAutorizacionDescuento status;
    @Column(name="authorized_at",nullable=false,updatable=false) private OffsetDateTime autorizadaEn;
    @Column(name="consumed_at") private OffsetDateTime consumidaEn;
    @Column(name="sales_invoice_id") private UUID facturaId;
    protected AutorizacionDescuentoVenta(){}
    public AutorizacionDescuentoVenta(UUID tenant,UUID empresa,UUID operacion,UUID solicitante,UUID autorizador,UUID rolSolicitante,UUID rolAutorizador,AlcanceDescuentoVenta alcance,TipoDescuentoVenta tipo,UUID producto,BigDecimal valor,BigDecimal base,BigDecimal monto,BigDecimal maxPctSolicitante,BigDecimal maxMontoSolicitante,BigDecimal maxPctAutorizador,BigDecimal maxMontoAutorizador,UUID motivo,String motivoNombre,String detalle,String fingerprint){tenantId=tenant;empresaId=empresa;operacionId=operacion;solicitadaPor=solicitante;autorizadaPor=autorizador;rolSolicitanteId=rolSolicitante;rolAutorizadorId=rolAutorizador;this.alcance=alcance;this.tipo=tipo;productoId=producto;this.valor=valor;baseElegible=base;this.monto=monto;porcentajeSolicitante=maxPctSolicitante;montoSolicitante=maxMontoSolicitante;porcentajeAutorizador=maxPctAutorizador;montoAutorizador=maxMontoAutorizador;motivoId=motivo;this.motivoNombre=motivoNombre;motivoDetalle=detalle;this.fingerprint=fingerprint;status=EstadoAutorizacionDescuento.AUTHORIZED;autorizadaEn=OffsetDateTime.now();}
    public void consumir(UUID factura){if(status!=EstadoAutorizacionDescuento.AUTHORIZED)throw new IllegalStateException("La autorización ya fue utilizada.");status=EstadoAutorizacionDescuento.CONSUMED;facturaId=factura;consumidaEn=OffsetDateTime.now();}
    public UUID getId(){return id;}public UUID getTenantId(){return tenantId;}public UUID getEmpresaId(){return empresaId;}public UUID getOperacionId(){return operacionId;}public UUID getSolicitadaPor(){return solicitadaPor;}public UUID getAutorizadaPor(){return autorizadaPor;}public String getFingerprint(){return fingerprint;}public EstadoAutorizacionDescuento getStatus(){return status;}public OffsetDateTime getAutorizadaEn(){return autorizadaEn;}
}
