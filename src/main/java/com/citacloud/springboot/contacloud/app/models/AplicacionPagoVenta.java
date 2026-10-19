package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_payment_application")
public class AplicacionPagoVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="sales_payment_id",nullable=false,updatable=false) private UUID pagoId;
    @Column(name="payment_method_id",nullable=false,updatable=false) private UUID metodoId;
    @Column(name="method_code",nullable=false,length=30) private String codigoMetodo;
    @Column(name="method_name",nullable=false,length=100) private String nombreMetodo;
    @Enumerated(EnumType.STRING) @Column(name="method_type",nullable=false,length=24) private TipoMetodoPagoVenta tipoMetodo;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="received_amount",precision=19,scale=4) private BigDecimal recibido;
    @Column(name="change_amount",nullable=false,precision=19,scale=4) private BigDecimal cambio;
    @Column(name="bank_account_id") private UUID cuentaBancariaId;
    @Column(length=100) private String reference;
    @Column(name="bank_name",length=120) private String banco;
    @Column(name="line_order",nullable=false) private int lineOrder;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    protected AplicacionPagoVenta(){}
    public AplicacionPagoVenta(PagoVenta pago,MetodoPagoVenta metodo,BigDecimal monto,BigDecimal recibido,BigDecimal cambio,
            UUID cuentaBancariaId,String referencia,String banco,int orden){
        tenantId=pago.getTenantId();empresaId=pago.getEmpresaId();pagoId=pago.getId();metodoId=metodo.getId();
        codigoMetodo=metodo.getCode();nombreMetodo=metodo.getName();tipoMetodo=metodo.getType();amount=monto;
        this.recibido=recibido;this.cambio=cambio;this.cuentaBancariaId=cuentaBancariaId;reference=referencia;this.banco=banco;lineOrder=orden;
    }
    public UUID getId(){return id;} public UUID getPagoId(){return pagoId;} public String getNombreMetodo(){return nombreMetodo;}
    public TipoMetodoPagoVenta getTipoMetodo(){return tipoMetodo;} public BigDecimal getMonto(){return amount;}
    public BigDecimal getRecibido(){return recibido;} public BigDecimal getCambio(){return cambio;}
    public String getReferencia(){return reference;} public String getBanco(){return banco;} public int getLineOrder(){return lineOrder;}
}
