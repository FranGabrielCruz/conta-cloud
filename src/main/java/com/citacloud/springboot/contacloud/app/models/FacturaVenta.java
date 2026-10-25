package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="sales_invoice")
public class FacturaVenta {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="internal_number",nullable=false,updatable=false,length=30) private String numero;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoFacturaVenta status;
    @Column(name="invoice_date",nullable=false) private LocalDate fecha;
    @Column(name="confirmed_at",nullable=false) private OffsetDateTime confirmadaEn;
    @Column(name="confirmed_by",nullable=false) private UUID confirmadaPor;
    @Column(name="terminal_id",nullable=false) private UUID terminalId;
    @Column(name="cash_register_id",nullable=false) private UUID cajaId;
    @Column(name="cash_register_session_id",nullable=false) private UUID sesionCajaId;
    @Column(name="branch_id",nullable=false) private UUID sucursalId;
    @Column(name="warehouse_id",nullable=false) private UUID almacenId;
    @Column(name="customer_id") private UUID clienteId;
    @Column(name="customer_name",nullable=false,length=180) private String clienteNombre;
    @Column(name="customer_identification",length=50) private String clienteIdentificacion;
    @Column(name="customer_identification_normalized",length=50) private String clienteIdentificacionNormalizada;
    @Column(name="seller_name",nullable=false,length=180) private String vendedorNombre;
    @Column(name="currency_id",nullable=false) private UUID monedaId;
    @Column(name="currency_code",nullable=false,length=3) private String monedaCodigo;
    @Column(name="currency_symbol",length=10) private String monedaSimbolo;
    @Column(name="payment_condition",nullable=false,length=30) private String condicionPago;
    @Column(name="payment_term_id") private UUID condicionPagoId;
    @Column(name="credit_days") private Integer diasCredito;
    @Column(name="due_date") private LocalDate vencimiento;
    @Column(length=1000) private String note;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal subtotal;
    @Column(name="discount_total",nullable=false,precision=19,scale=4) private BigDecimal descuentoTotal;
    @Column(name="surcharge_total",nullable=false,precision=19,scale=4) private BigDecimal recargoTotal;
    @Column(name="tax_total",nullable=false,precision=19,scale=4) private BigDecimal impuestoTotal;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal total;
    @Column(name="received_amount",nullable=false,precision=19,scale=4) private BigDecimal recibido;
    @Column(name="change_amount",nullable=false,precision=19,scale=4) private BigDecimal cambio;
    @Column(name="idempotency_key",nullable=false,updatable=false) private UUID claveIdempotencia;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private OffsetDateTime creadoEn;
    protected FacturaVenta(){}
    public FacturaVenta(UUID tenant,UUID empresa,String numero,LocalDate fecha,UUID usuario,UUID terminal,UUID caja,UUID sesion,
            UUID sucursal,UUID almacen,UUID cliente,String clienteNombre,String clienteIdentificacion,String vendedor,
            UUID moneda,String monedaCodigo,String monedaSimbolo,String nota,BigDecimal subtotal,BigDecimal impuesto,
            BigDecimal total,BigDecimal recibido,UUID idempotencia){
        this(tenant,empresa,numero,fecha,usuario,terminal,caja,sesion,sucursal,almacen,cliente,clienteNombre,clienteIdentificacion,
                vendedor,moneda,monedaCodigo,monedaSimbolo,nota,subtotal,BigDecimal.ZERO,impuesto,total,recibido,idempotencia);
    }
    public FacturaVenta(UUID tenant,UUID empresa,String numero,LocalDate fecha,UUID usuario,UUID terminal,UUID caja,UUID sesion,
            UUID sucursal,UUID almacen,UUID cliente,String clienteNombre,String clienteIdentificacion,String vendedor,
            UUID moneda,String monedaCodigo,String monedaSimbolo,String nota,BigDecimal subtotal,BigDecimal descuento,BigDecimal impuesto,
            BigDecimal total,BigDecimal recibido,UUID idempotencia){
        this.tenantId=tenant;this.empresaId=empresa;this.numero=numero;this.status=EstadoFacturaVenta.CONFIRMED;
        this.fecha=fecha;this.confirmadaEn=OffsetDateTime.now();this.confirmadaPor=usuario;this.terminalId=terminal;
        this.cajaId=caja;this.sesionCajaId=sesion;this.sucursalId=sucursal;this.almacenId=almacen;this.clienteId=cliente;
        this.clienteNombre=clienteNombre;this.clienteIdentificacion=clienteIdentificacion;
        this.clienteIdentificacionNormalizada=normalizarIdentificacion(clienteIdentificacion);this.vendedorNombre=vendedor;
        this.monedaId=moneda;this.monedaCodigo=monedaCodigo;this.monedaSimbolo=monedaSimbolo;this.condicionPago="Contado";
        this.note=nota;this.subtotal=subtotal;this.descuentoTotal=descuento;this.recargoTotal=BigDecimal.ZERO;
        this.impuestoTotal=impuesto;this.total=total;this.recibido=recibido;this.cambio=recibido.subtract(total);this.claveIdempotencia=idempotencia;
    }
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;}
    public String getNumero(){return numero;} public LocalDate getFecha(){return fecha;} public OffsetDateTime getConfirmadaEn(){return confirmadaEn;}
    public UUID getCajaId(){return cajaId;} public UUID getSesionCajaId(){return sesionCajaId;} public UUID getSucursalId(){return sucursalId;} public UUID getAlmacenId(){return almacenId;}
    public UUID getClienteId(){return clienteId;}
    public String getClienteNombre(){return clienteNombre;} public String getClienteIdentificacion(){return clienteIdentificacion;}
    public String getVendedorNombre(){return vendedorNombre;} public UUID getMonedaId(){return monedaId;} public String getMonedaCodigo(){return monedaCodigo;}
    public String getMonedaSimbolo(){return monedaSimbolo;} public String getNota(){return note;} public BigDecimal getSubtotal(){return subtotal;}
    public BigDecimal getImpuestoTotal(){return impuestoTotal;} public BigDecimal getTotal(){return total;} public BigDecimal getRecibido(){return recibido;}
    public BigDecimal getCambio(){return cambio;} public EstadoFacturaVenta getStatus(){return status;}
    public BigDecimal getDescuentoTotal(){return descuentoTotal;} public BigDecimal getRecargoTotal(){return recargoTotal;}
    public void aplicarRecargo(BigDecimal recargo){this.recargoTotal=recargo==null?BigDecimal.ZERO:recargo;}
    public void marcarCredito(UUID condicionPagoId,int diasCredito,LocalDate vencimiento){
        this.condicionPago="Crédito";this.condicionPagoId=condicionPagoId;this.diasCredito=diasCredito;this.vencimiento=vencimiento;
        this.recibido=BigDecimal.ZERO.setScale(4);this.cambio=BigDecimal.ZERO.setScale(4);
    }
    public String getCondicionPago(){return condicionPago;} public UUID getCondicionPagoId(){return condicionPagoId;}
    public Integer getDiasCredito(){return diasCredito;} public LocalDate getVencimiento(){return vencimiento;}
    public UUID getConfirmadaPor(){return confirmadaPor;}
    private static String normalizarIdentificacion(String valor){return valor==null?null:valor.replaceAll("\\D","");}
}
