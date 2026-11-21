package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.MonedaRepository;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Traduce los hechos confirmados del dominio a eventos; nunca escribe el libro. */
@Service
public class ContabilizacionOperativaService {
 private final ConfiguracionMotorService configuracion;
 private final AccountingPostingService posting;
 private final MonedaRepository monedas;
 public ContabilizacionOperativaService(ConfiguracionMotorService configuracion,AccountingPostingService posting,MonedaRepository monedas) {
  this.configuracion=configuracion;this.posting=posting;this.monedas=monedas;
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID transferencia(TransferenciaFinanciera t) {
  if(!configuracion.efectivo(Modulo.FINANZAS))return null;
  var cash=TipoCuentaDinero.CASH_REGISTER;
  return posting.contabilizar(new EventoContable(fuenteTransferencia(t),0,t.getFecha(),t.getMonedaOrigenId(),"Transferencia financiera",
   TraceContext.currentOrCreate(),new EventoContable.Transferencia(t.getTipoCuentaOrigen(),t.getTipoCuentaOrigen()==cash?t.getCajaOrigenId():t.getCuentaBancariaOrigenId(),
    t.getTipoCuentaDestino(),t.getTipoCuentaDestino()==cash?t.getCajaDestinoId():t.getCuentaBancariaDestinoId(),t.getMonedaDestinoId(),t.getMontoOrigen())));
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID anularTransferencia(TransferenciaFinanciera t,String motivo) { return posting.reversar(fuenteTransferencia(t),t.getFecha(),motivo); }
 private static Fuente fuenteTransferencia(TransferenciaFinanciera t) {
  return new Fuente(Origen.FINANZAS,"TRANSFERENCIA",t.getId(),Objects.toString(t.getReferencia(),"TR-"+t.getId()),"REGISTRO");
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID factura(FacturaVenta f) {
  if(!configuracion.efectivo(Modulo.VENTAS))return null;
  return posting.contabilizar(new EventoContable(new Fuente(Origen.VENTAS,"FACTURA",f.getId(),f.getNumero(),"EMISION"),
   0,f.getFecha(),f.getMonedaId(),"Emisión de factura "+f.getNumero(),TraceContext.currentOrCreate(),new EventoContable.Venta(f.getTotal(),f.getImpuestoTotal())));
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID depreciacion(UUID id,String numero,LocalDate fecha,List<com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Total> totales) {
  if(!configuracion.efectivo(Modulo.ACTIVOS_FIJOS))return null;
  var p=com.citacloud.springboot.contacloud.app.security.TenantContext.principalActual();
  var base=monedas.findByEmpresaIdAndMonedaBaseTrue(p.empresaId()).filter(m->m.isActivo())
   .orElseThrow(()->new ReglaNegocioException("Configura la moneda base de la empresa."));
  if(totales.stream().anyMatch(t->!t.moneda().equals(base.getCodigoIso())))
   throw new ReglaNegocioException("La depreciación automática requiere importes en la moneda base.");
  var importe=totales.stream().map(com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Total::importe).reduce(BigDecimal.ZERO,BigDecimal::add);
  return posting.contabilizar(new EventoContable(new Fuente(Origen.ACTIVOS_FIJOS,"DEPRECIACION",id,numero,"PROCESAMIENTO"),
   0,fecha,base.getId(),"Depreciación "+numero,TraceContext.currentOrCreate(),new EventoContable.Depreciacion(importe)));
 }
}

