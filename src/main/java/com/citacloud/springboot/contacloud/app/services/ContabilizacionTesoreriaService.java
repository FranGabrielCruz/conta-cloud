package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.models.PagoProveedor;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class ContabilizacionTesoreriaService {
 private final AccountingPostingService posting;
 public ContabilizacionTesoreriaService(AccountingPostingService posting){this.posting=posting;}
 public UUID cobro(UUID id,String numero,LocalDate fecha,UUID moneda,BigDecimal recibido,BigDecimal aplicado,List<EventoContable.RecursoImporte> recursos) {
  return evento(new Fuente(Origen.CXC,"RECIBO",id,numero,"CONFIRMACION"),0,fecha,moneda,
   "Cobro de cliente "+numero,new EventoContable.Cobro(recursos,recibido,aplicado));
 }
 public UUID aplicarRecibo(UUID id,String numero,LocalDate fecha,UUID moneda,BigDecimal importe) {
  return evento(new Fuente(Origen.CXC,"APLICACION_RECIBO",id,numero,"APLICACION"),0,fecha,moneda,
   "Aplicación de anticipo "+numero,new EventoContable.AplicacionAnticipo(importe));
 }
 public void anularRecibo(UUID id,String numero,LocalDate fecha,List<UUID> aplicaciones,String motivo) {
  for(var aplicacion:aplicaciones)posting.reversar(new Fuente(Origen.CXC,"APLICACION_RECIBO",aplicacion,numero,"APLICACION"),fecha,motivo);
  posting.reversar(new Fuente(Origen.CXC,"RECIBO",id,numero,"CONFIRMACION"),fecha,motivo);
 }
 public UUID pago(PagoProveedor p) {
  ambito(p);
  var recurso=new EventoContable.RecursoImporte(p.getTipoFuente(),p.getTipoFuente()==TipoCuentaDinero.CASH_REGISTER?p.getCajaId():p.getCuentaBancariaId(),p.getMonto());
  return evento(fuentePago(p),p.getVersion(),p.getFecha(),p.getMonedaId(),"Pago a proveedor "+p.getNumero(),new EventoContable.PagoProveedor(recurso,p.getMontoAplicado()));
 }
 public UUID aplicarPago(UUID id,PagoProveedor p,BigDecimal importe) {
  ambito(p);
  return evento(new Fuente(Origen.CXP,"APLICACION_PAGO",id,p.getNumero(),"APLICACION"),p.getVersion(),p.getFecha(),p.getMonedaId(),
   "Aplicación de anticipo "+p.getNumero(),new EventoContable.AplicacionAnticipo(importe));
 }
 public void anularPago(PagoProveedor p,List<UUID> aplicaciones,String motivo) {
  ambito(p);
  for(var id:aplicaciones)posting.reversar(new Fuente(Origen.CXP,"APLICACION_PAGO",id,p.getNumero(),"APLICACION"),p.getFecha(),motivo);
  posting.reversar(fuentePago(p),p.getFecha(),motivo);
 }
 private UUID evento(Fuente fuente,long version,LocalDate fecha,UUID moneda,String concepto,EventoContable.Datos datos) {
  return posting.contabilizar(new EventoContable(fuente,version,fecha,moneda,concepto,TraceContext.currentOrCreate(),datos));
 }
 private static Fuente fuentePago(PagoProveedor p){return new Fuente(Origen.CXP,"PAGO_PROVEEDOR",p.getId(),p.getNumero(),"CONFIRMACION");}
 private static void ambito(PagoProveedor pago) {
  var p=TenantContext.principalActual();
  if(!p.tenantId().equals(pago.getTenantId())||!p.empresaId().equals(pago.getEmpresaId()))
   throw new org.springframework.security.access.AccessDeniedException("El pago no está disponible.");
 }
}

