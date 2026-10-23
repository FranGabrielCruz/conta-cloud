package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sales_return")
public class DevolucionVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="return_number",nullable=false,updatable=false,length=30) private String numero;
    @Column(name="sales_invoice_id",nullable=false,updatable=false) private UUID facturaId;
    @Column(name="invoice_number",nullable=false,updatable=false,length=30) private String facturaNumero;
    @Column(nullable=false,length=20) private String status="CONFIRMED";
    @Column(name="reason_id",nullable=false) private UUID motivoId;
    @Column(name="reason_code",nullable=false,length=40) private String motivoCodigo;
    @Column(name="reason_name",nullable=false,length=150) private String motivoNombre;
    @Column(length=1000) private String observation;
    @Column(name="returns_inventory",nullable=false) private boolean retornaInventario;
    @Column(name="destination_warehouse_id") private UUID almacenDestinoId;
    @Column(name="destination_warehouse_name",length=120) private String almacenDestinoNombre;
    @Column(name="customer_id") private UUID clienteId;
    @Column(name="customer_name",nullable=false,length=180) private String clienteNombre;
    @Column(name="customer_identification",length=50) private String clienteIdentificacion;
    @Column(name="currency_id",nullable=false) private UUID monedaId;
    @Column(name="currency_code",nullable=false,length=3) private String monedaCodigo;
    @Column(name="currency_symbol",length=10) private String monedaSimbolo;
    @Column(name="gross_subtotal",nullable=false,precision=19,scale=4) private BigDecimal subtotalBruto;
    @Column(name="discount_total",nullable=false,precision=19,scale=4) private BigDecimal descuento;
    @Column(name="surcharge_total",nullable=false,precision=19,scale=4) private BigDecimal recargo;
    @Column(name="tax_total",nullable=false,precision=19,scale=4) private BigDecimal impuesto;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal total;
    @Column(name="operation_key",nullable=false,updatable=false) private UUID claveOperacion;
    @Column(name="confirmed_at",nullable=false) private OffsetDateTime confirmadaEn;
    @Column(name="confirmed_by",nullable=false) private UUID confirmadaPor;
    protected DevolucionVenta(){}
    public DevolucionVenta(UUID tenant,UUID empresa,String numero,FacturaVenta factura,MotivoDevolucionVenta motivo,
            String observacion,boolean retornaInventario,UUID almacenDestino,String almacenDestinoNombre,BigDecimal subtotalBruto,BigDecimal descuento,
            BigDecimal recargo,BigDecimal impuesto,BigDecimal total,UUID claveOperacion,UUID usuario){
        this.tenantId=tenant;this.empresaId=empresa;this.numero=numero;facturaId=factura.getId();facturaNumero=factura.getNumero();motivoId=motivo.getId();
        motivoCodigo=motivo.getCodigo();motivoNombre=motivo.getNombre();observation=observacion;this.retornaInventario=retornaInventario;
        almacenDestinoId=almacenDestino;this.almacenDestinoNombre=almacenDestinoNombre;clienteId=factura.getClienteId();clienteNombre=factura.getClienteNombre();
        clienteIdentificacion=factura.getClienteIdentificacion();monedaId=factura.getMonedaId();monedaCodigo=factura.getMonedaCodigo();
        monedaSimbolo=factura.getMonedaSimbolo();this.subtotalBruto=subtotalBruto;this.descuento=descuento;this.recargo=recargo;
        this.impuesto=impuesto;this.total=total;this.claveOperacion=claveOperacion;confirmadaPor=usuario;confirmadaEn=OffsetDateTime.now();
    }
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getNumero(){return numero;} public UUID getFacturaId(){return facturaId;} public String getFacturaNumero(){return facturaNumero;} public String getMotivoNombre(){return motivoNombre;}
    public String getObservacion(){return observation;} public boolean isRetornaInventario(){return retornaInventario;}
    public UUID getAlmacenDestinoId(){return almacenDestinoId;} public String getAlmacenDestinoNombre(){return almacenDestinoNombre;} public UUID getClienteId(){return clienteId;}
    public String getClienteNombre(){return clienteNombre;} public String getClienteIdentificacion(){return clienteIdentificacion;}
    public UUID getMonedaId(){return monedaId;} public String getMonedaCodigo(){return monedaCodigo;} public String getMonedaSimbolo(){return monedaSimbolo;}
    public BigDecimal getSubtotalBruto(){return subtotalBruto;} public BigDecimal getDescuento(){return descuento;}
    public BigDecimal getRecargo(){return recargo;} public BigDecimal getImpuesto(){return impuesto;} public BigDecimal getTotal(){return total;}
    public OffsetDateTime getConfirmadaEn(){return confirmadaEn;} public UUID getConfirmadaPor(){return confirmadaPor;}
}
