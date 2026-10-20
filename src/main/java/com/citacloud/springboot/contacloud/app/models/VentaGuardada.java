package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="saved_sale")
public class VentaGuardada {
 @Id @GeneratedValue(strategy=GenerationType.UUID)private UUID id;
 @Column(name="tenant_id",nullable=false,updatable=false)private UUID tenantId;@Column(name="empresa_id",nullable=false,updatable=false)private UUID empresaId;
 @Column(nullable=false,updatable=false,length=30)private String numero;@Enumerated(EnumType.STRING)@Column(nullable=false,length=20)private EstadoVentaGuardada estado;
 @Column(name="branch_id",nullable=false)private UUID sucursalId;@Column(name="cash_register_id",nullable=false)private UUID cajaId;@Column(name="terminal_id",nullable=false)private UUID terminalId;@Column(name="warehouse_id",nullable=false)private UUID almacenId;
 @Column(name="customer_id")private UUID clienteId;@Column(name="customer_name",nullable=false,length=180)private String clienteNombre;@Column(name="customer_identification",length=50)private String clienteIdentificacion;
 @Column(name="currency_id",nullable=false)private UUID monedaId;@Column(name="currency_code",nullable=false,length=3)private String monedaCodigo;
 @Column(length=150)private String referencia;@Column(length=1000)private String observacion;
 @Column(nullable=false,precision=19,scale=4)private BigDecimal subtotal;@Column(nullable=false,precision=19,scale=4)private BigDecimal descuento;
 @Column(nullable=false,precision=19,scale=4)private BigDecimal recargo;@Column(nullable=false,precision=19,scale=4)private BigDecimal impuestos;@Column(nullable=false,precision=19,scale=4)private BigDecimal total;
 @Column(name="item_count",nullable=false)private int cantidadItems;@Column(name="created_at",nullable=false,insertable=false,updatable=false)private OffsetDateTime creadaEn;
 @Column(name="created_by",nullable=false,updatable=false)private UUID creadaPor;@Column(name="updated_at",nullable=false)private OffsetDateTime actualizadaEn;@Column(name="updated_by",nullable=false)private UUID actualizadaPor;
 @Column(name="recovered_at")private OffsetDateTime recuperadaEn;@Column(name="recovered_by")private UUID recuperadaPor;@Column(name="recovered_terminal_id")private UUID terminalRecuperacionId;
 @Column(name="completed_at")private OffsetDateTime completadaEn;@Column(name="completed_invoice_id")private UUID facturaCompletadaId;@Column(name="discarded_at")private OffsetDateTime descartadaEn;@Column(name="discarded_by")private UUID descartadaPor;
 @Version private long version;
 protected VentaGuardada(){}
 public VentaGuardada(UUID tenant,UUID empresa,String numero,UUID sucursal,UUID caja,UUID terminal,UUID almacen,UUID usuario){tenantId=tenant;empresaId=empresa;this.numero=numero;estado=EstadoVentaGuardada.SAVED;sucursalId=sucursal;cajaId=caja;terminalId=terminal;almacenId=almacen;creadaPor=usuario;actualizadaPor=usuario;actualizadaEn=OffsetDateTime.now();}
 public void actualizar(UUID sucursal,UUID caja,UUID terminal,UUID almacen,UUID cliente,String clienteNombre,String identificacion,UUID moneda,String monedaCodigo,String referencia,String observacion,BigDecimal subtotal,BigDecimal descuento,BigDecimal recargo,BigDecimal impuestos,BigDecimal total,int items,UUID usuario){sucursalId=sucursal;cajaId=caja;terminalId=terminal;almacenId=almacen;clienteId=cliente;this.clienteNombre=clienteNombre;clienteIdentificacion=identificacion;monedaId=moneda;this.monedaCodigo=monedaCodigo;this.referencia=referencia;this.observacion=observacion;this.subtotal=subtotal;this.descuento=descuento;this.recargo=recargo;this.impuestos=impuestos;this.total=total;cantidadItems=items;estado=EstadoVentaGuardada.SAVED;actualizadaPor=usuario;actualizadaEn=OffsetDateTime.now();recuperadaEn=null;recuperadaPor=null;terminalRecuperacionId=null;}
 public void recuperar(UUID usuario,UUID terminal){if(estado!=EstadoVentaGuardada.SAVED)throw new IllegalStateException();estado=EstadoVentaGuardada.IN_USE;recuperadaPor=usuario;terminalRecuperacionId=terminal;recuperadaEn=OffsetDateTime.now();actualizadaPor=usuario;actualizadaEn=recuperadaEn;}
 public void completar(UUID factura,UUID usuario){if(estado!=EstadoVentaGuardada.IN_USE)throw new IllegalStateException();estado=EstadoVentaGuardada.COMPLETED;facturaCompletadaId=factura;completadaEn=OffsetDateTime.now();actualizadaPor=usuario;actualizadaEn=completadaEn;}
 public void descartar(UUID usuario){if(estado!=EstadoVentaGuardada.SAVED)throw new IllegalStateException();estado=EstadoVentaGuardada.DISCARDED;descartadaPor=usuario;descartadaEn=OffsetDateTime.now();actualizadaPor=usuario;actualizadaEn=descartadaEn;}
 public UUID getId(){return id;}public UUID getTenantId(){return tenantId;}public UUID getEmpresaId(){return empresaId;}public String getNumero(){return numero;}public EstadoVentaGuardada getEstado(){return estado;}public UUID getSucursalId(){return sucursalId;}public UUID getCajaId(){return cajaId;}public UUID getTerminalId(){return terminalId;}public UUID getAlmacenId(){return almacenId;}public UUID getClienteId(){return clienteId;}public String getClienteNombre(){return clienteNombre;}public String getClienteIdentificacion(){return clienteIdentificacion;}public UUID getMonedaId(){return monedaId;}public String getMonedaCodigo(){return monedaCodigo;}public String getReferencia(){return referencia;}public String getObservacion(){return observacion;}public BigDecimal getSubtotal(){return subtotal;}public BigDecimal getDescuento(){return descuento;}public BigDecimal getRecargo(){return recargo;}public BigDecimal getImpuestos(){return impuestos;}public BigDecimal getTotal(){return total;}public int getCantidadItems(){return cantidadItems;}public OffsetDateTime getCreadaEn(){return creadaEn;}public OffsetDateTime getActualizadaEn(){return actualizadaEn;}public UUID getCreadaPor(){return creadaPor;}public UUID getRecuperadaPor(){return recuperadaPor;}public UUID getTerminalRecuperacionId(){return terminalRecuperacionId;}public long getVersion(){return version;}
}
