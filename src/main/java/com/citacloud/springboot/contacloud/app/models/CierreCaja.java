package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="cash_closures")
public class CierreCaja {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="empresa_id",nullable=false,updatable=false) private UUID empresaId;
    @Column(name="closure_number",nullable=false,updatable=false,length=30) private String numero;
    @Column(name="cash_session_id",nullable=false,updatable=false) private UUID sesionId;
    @Column(name="cash_register_id",nullable=false,updatable=false) private UUID cajaId;
    @Column(name="branch_id",nullable=false,updatable=false) private UUID sucursalId;
    @Column(name="currency_id",nullable=false,updatable=false) private UUID monedaId;
    @Column(name="session_code",nullable=false,updatable=false,length=100) private String turno;
    @Column(name="cash_register_name",nullable=false,updatable=false,length=150) private String caja;
    @Column(name="branch_name",nullable=false,updatable=false,length=150) private String sucursal;
    @Column(name="currency_code",nullable=false,updatable=false,length=3) private String monedaCodigo;
    @Column(name="currency_symbol",updatable=false,length=10) private String monedaSimbolo;
    @Column(name="opened_at",nullable=false,updatable=false) private OffsetDateTime abiertoEn;
    @Column(name="opened_by",nullable=false,updatable=false) private UUID abiertoPor;
    @Column(name="opened_by_name",nullable=false,updatable=false,length=150) private String abiertoPorNombre;
    @Column(name="closed_at",nullable=false,updatable=false) private OffsetDateTime cerradoEn;
    @Column(name="closed_by",nullable=false,updatable=false) private UUID cerradoPor;
    @Column(name="closed_by_name",nullable=false,updatable=false,length=150) private String cerradoPorNombre;
    @Column(name="cutoff_at",nullable=false,updatable=false) private OffsetDateTime corteEn;
    @Column(name="opening_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal fondoInicial;
    @Column(name="cash_sales_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal ventasEfectivo;
    @Column(name="cash_collections_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal cobrosEfectivo;
    @Column(name="other_cash_in_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal otrasEntradasEfectivo;
    @Column(name="cash_voucher_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal valesCaja;
    @Column(name="cash_refund_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal devolucionesEfectivo;
    @Column(name="other_cash_out_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal otrasSalidasEfectivo;
    @Column(name="total_cash_in_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal totalEntradasEfectivo;
    @Column(name="total_cash_out_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal totalSalidasEfectivo;
    @Column(name="expected_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal esperado;
    @Column(name="counted_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal contado;
    @Column(name="difference_amount",nullable=false,updatable=false,precision=19,scale=4) private BigDecimal diferencia;
    @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=10) private ResultadoRecuentoCaja result;
    @Column(updatable=false,length=500) private String observation;
    @Column(name="idempotency_key",nullable=false,updatable=false) private UUID idempotencia;
    @Column(name="company_name",nullable=false,updatable=false,length=150) private String empresaNombre;
    @Column(name="company_tax_id",updatable=false,length=30) private String empresaIdentificacion;
    @Column(name="company_logo_object_key",updatable=false,length=512) private String empresaLogo;
    protected CierreCaja() {}
    public CierreCaja(UUID t,UUID e,String numero,SesionCaja s,OffsetDateTime instante,UUID usuarioId,
            String usuarioNombre,Resumen r,BigDecimal contado,String observacion,UUID key,Empresa empresa){
        tenantId=t;empresaId=e;this.numero=numero;sesionId=s.getId();cajaId=s.getCajaId();sucursalId=s.getSucursalId();monedaId=s.getMonedaId();
        turno=s.getCodigoVisible();caja=s.getCaja().getNombre();sucursal=s.getSucursal().getNombre();monedaCodigo=s.getMoneda().getCodigoIso();monedaSimbolo=s.getMoneda().getSimbolo();
        abiertoEn=s.getAbiertoEn();abiertoPor=s.getAbiertoPor();abiertoPorNombre=s.getUsuarioApertura().getNombre();cerradoEn=instante;cerradoPor=usuarioId;cerradoPorNombre=usuarioNombre;corteEn=instante;
        fondoInicial=s.getFondoInicial();ventasEfectivo=r.ventas();cobrosEfectivo=r.cobros();otrasEntradasEfectivo=r.otrasEntradas();valesCaja=r.vales();devolucionesEfectivo=r.devoluciones();otrasSalidasEfectivo=r.otrasSalidas();totalEntradasEfectivo=r.totalEntradas();totalSalidasEfectivo=r.totalSalidas();esperado=fondoInicial.add(totalEntradasEfectivo).subtract(totalSalidasEfectivo);this.contado=contado;diferencia=contado.subtract(esperado);result=RecuentoCaja.resultado(diferencia);observation=observacion;idempotencia=key;empresaNombre=empresa.getNombre();empresaIdentificacion=empresa.getIdentificacionFiscal();empresaLogo=empresa.getLogoObjectKey();
    }
    public record Resumen(BigDecimal ventas,BigDecimal cobros,BigDecimal otrasEntradas,BigDecimal vales,
            BigDecimal devoluciones,BigDecimal otrasSalidas,BigDecimal totalEntradas,BigDecimal totalSalidas) {}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getEmpresaId(){return empresaId;} public String getNumero(){return numero;} public UUID getSesionId(){return sesionId;} public UUID getCajaId(){return cajaId;} public UUID getSucursalId(){return sucursalId;} public UUID getMonedaId(){return monedaId;} public String getTurno(){return turno;} public String getCaja(){return caja;} public String getSucursal(){return sucursal;} public String getMonedaCodigo(){return monedaCodigo;} public String getMonedaSimbolo(){return monedaSimbolo;} public OffsetDateTime getAbiertoEn(){return abiertoEn;} public String getAbiertoPorNombre(){return abiertoPorNombre;} public OffsetDateTime getCerradoEn(){return cerradoEn;} public OffsetDateTime getCorteEn(){return corteEn;} public String getCerradoPorNombre(){return cerradoPorNombre;} public BigDecimal getFondoInicial(){return fondoInicial;} public BigDecimal getVentasEfectivo(){return ventasEfectivo;} public BigDecimal getCobrosEfectivo(){return cobrosEfectivo;} public BigDecimal getOtrasEntradasEfectivo(){return otrasEntradasEfectivo;} public BigDecimal getValesCaja(){return valesCaja;} public BigDecimal getDevolucionesEfectivo(){return devolucionesEfectivo;} public BigDecimal getOtrasSalidasEfectivo(){return otrasSalidasEfectivo;} public BigDecimal getTotalEntradasEfectivo(){return totalEntradasEfectivo;} public BigDecimal getTotalSalidasEfectivo(){return totalSalidasEfectivo;} public BigDecimal getEsperado(){return esperado;} public BigDecimal getContado(){return contado;} public BigDecimal getDiferencia(){return diferencia;} public ResultadoRecuentoCaja getResult(){return result;} public String getObservation(){return observation;} public String getEmpresaNombre(){return empresaNombre;} public String getEmpresaIdentificacion(){return empresaIdentificacion;} public String getEmpresaLogo(){return empresaLogo;}
}
