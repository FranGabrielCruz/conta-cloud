package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** Collections of independent sales documents, using POS validation and the central money ledger. */
@Service
public class CobroContadoDocumentoService {
 private final MetodoPagoVentaRepository methods;private final BankAccountRepository banks;private final CuentaDineroResolver accounts;
 private final CashRegisterSessionService sessions;private final FinancialMovementRepository movements;private final BankReconciliationMatchRepository reconciliation;
 private final NotaDebitoVentaRepository notes;
 public CobroContadoDocumentoService(MetodoPagoVentaRepository methods,BankAccountRepository banks,CuentaDineroResolver accounts,CashRegisterSessionService sessions,FinancialMovementRepository movements,BankReconciliationMatchRepository reconciliation,NotaDebitoVentaRepository notes){
  this.methods=methods;this.banks=banks;this.accounts=accounts;this.sessions=sessions;this.movements=movements;this.reconciliation=reconciliation;this.notes=notes;
 }
 public CatalogosCobroContadoDto catalogos(UUID moneda,UUID sucursal){
  var p=TenantContext.principalActual();
  var ms=methods.findAllByTenantIdAndEmpresaIdAndActiveTrueOrderByDisplayOrderAscNameAsc(p.tenantId(),p.empresaId()).stream().map(m->new MetodoPagoPosDto(m.getId(),m.getCode(),m.getName(),m.getType(),m.isRequiresReference(),m.isRequiresBankAccount(),m.isAllowsChange(),m.isCash(),m.isElectronic(),m.getIcon(),m.isDefaultMethod())).toList();
  var cs=accounts.opciones().stream().filter(c->moneda.equals(c.monedaId())).filter(c->c.tipo()!=TipoCuentaDinero.CASH_REGISTER||sucursal.equals(accounts.resolver(c.tipo(),c.id()).caja().getSucursalId())).toList();
  var open=sessions.sesionesDisponiblesCobro(cs.stream().filter(c->c.tipo()==TipoCuentaDinero.CASH_REGISTER).map(CuentaDineroOpcionDto::id).toList());
  return new CatalogosCobroContadoDto(ms,cs.stream().filter(c->c.tipo()!=TipoCuentaDinero.CASH_REGISTER||open.containsKey(c.id())).toList(),open);
 }
 public List<NotaDebitoVentaDocumento.Pago> validar(EmitirNotaDebitoVentaInput input,FacturaVenta invoice,java.math.BigDecimal total){
  if(input.pagos()!=null)for(var item:input.pagos())if(item!=null)for(var value:Arrays.asList(item.monto(),item.recibido()))if(value!=null&&(value.scale()>4||value.precision()-value.scale()>15))throw new ReglaNegocioException("Importe de pago fuera de rango.");
  var p=TenantContext.principalActual();var validated=CobroContadoValidacion.validar(input.pagos(),total,p.tenantId(),p.empresaId(),invoice.getMonedaId(),p.permisos(),methods,banks);
  List<NotaDebitoVentaDocumento.Pago> result=new ArrayList<>();
  for(var v:validated){
   UUID account,session=null;String destination;
   if(v.metodo().isCash()){
    UUID caja=input.cajaId();
    if(caja==null){
     var available=catalogos(invoice.getMonedaId(),invoice.getSucursalId()).cuentas().stream().filter(c->c.tipo()==TipoCuentaDinero.CASH_REGISTER).toList();
     if(available.isEmpty())throw new ReglaNegocioException("No existe una sesión de caja abierta disponible para registrar este cobro.");
     if(available.size()>1)throw new ReglaNegocioException("Seleccione una caja entre las sesiones abiertas disponibles.");
     caja=available.getFirst().id();
    }
    var c=accounts.resolver(TipoCuentaDinero.CASH_REGISTER,caja);
    if(!invoice.getSucursalId().equals(c.caja().getSucursalId()))throw new ReglaNegocioException("La caja debe pertenecer a la sucursal de la factura.");
    if(!invoice.getMonedaId().equals(c.monedaId()))throw new ReglaNegocioException("La caja utiliza una moneda distinta a la nota.");
    session=sessions.requerirSesionAbierta(c.id());
    if(input.sesionCajaId()!=null&&!input.sesionCajaId().equals(session))throw new ReglaNegocioException("El turno de caja cambió. Actualice la información.");
    account=c.id();destination=c.nombre();
   }else{
    if(v.cuenta()==null)throw new ReglaNegocioException("Configure o seleccione una cuenta destino para "+v.metodo().getName()+".");
    account=v.cuenta().getId();destination=v.cuenta().getBancoNombre()+" · "+v.cuenta().getNombreCuenta();
   }
   result.add(new NotaDebitoVentaDocumento.Pago(UUID.randomUUID(),v.metodo().getId(),v.metodo().getName(),v.metodo().isCash()?"CASH":v.metodo().getType().name(),v.monto(),v.recibido(),v.cambio(),v.referencia(),account,session,destination));
  }
  return List.copyOf(result);
 }
 public void registrar(NotaDebitoVentaDocumento d){
  var p=TenantContext.principalActual();
  for(var payment:d.pagos()){
   TipoCuentaDinero type=payment.tipo().equals("CASH")?TipoCuentaDinero.CASH_REGISTER:TipoCuentaDinero.BANK_ACCOUNT;
   var account=accounts.resolver(type,payment.cuentaId());MedioPagoMovimiento medio=switch(TipoMetodoPagoVenta.valueOf(payment.tipo())){case CASH->MedioPagoMovimiento.CASH;case CREDIT_CARD,DEBIT_CARD->MedioPagoMovimiento.CARD;case BANK_TRANSFER->MedioPagoMovimiento.BANK_TRANSFER;case CHECK->MedioPagoMovimiento.CHECK;case OTHER->MedioPagoMovimiento.OTHER;};
   var m=MovimientoFinanciero.cobroNotaDebito(p.tenantId(),p.empresaId(),d.fecha(),type,account.id(),d.monedaId(),payment.monto(),d.numero(),payment.id(),medio,payment.sesionId(),payment.referencia(),p.usuarioId());
   m.asignarRelacionesCuenta(account.caja(),account.cuentaBancaria(),account.moneda());m=movements.saveAndFlush(m);notes.insertarPago(d.id(),payment,m.getId());
  }
 }
 public boolean anulable(NotaDebitoVentaDocumento d){
  var p=TenantContext.principalActual();
  try{for(var payment:d.pagos()){
   var m=movements.findByTenantIdAndEmpresaIdAndTipoOrigenAndOrigenId(p.tenantId(),p.empresaId(),TipoOrigenMovimiento.DEBIT_NOTE_PAYMENT,payment.id()).orElse(null);
   if(m==null||m.getEstado()!=EstadoMovimientoFinanciero.REGISTERED||reconciliation.existsByMovimientoFinancieroId(m.getId()))return false;
   sessions.validarMovimientoModificable(m.getSesionCajaId());
  }return !d.pagos().isEmpty();}catch(ReglaNegocioException ex){return false;}
 }
 public void revertir(NotaDebitoVentaDocumento d,String reason){
  if(reason.length()>500)throw new ReglaNegocioException("El motivo de anulación del cobro admite hasta 500 caracteres.");
  var p=TenantContext.principalActual();
  // Lock cash sessions before their money movements, matching the central cash-close order.
  for(var payment:d.pagos())if(payment.sesionId()!=null){UUID current=sessions.requerirSesionAbierta(payment.cuentaId());if(!current.equals(payment.sesionId()))throw new ReglaNegocioException("El turno original de caja está cerrado.");}
  for(var payment:d.pagos()){
   var m=movements.bloquearPorOrigen(p.tenantId(),p.empresaId(),TipoOrigenMovimiento.DEBIT_NOTE_PAYMENT,payment.id()).orElseThrow(()->new ReglaNegocioException("No se encontró el movimiento de cobro."));
   if(m.getEstado()!=EstadoMovimientoFinanciero.REGISTERED||reconciliation.existsByMovimientoFinancieroId(m.getId()))throw new ReglaNegocioException("El cobro cambió o está conciliado y no puede anularse.");
   sessions.validarMovimientoModificable(m.getSesionCajaId());m.anular(reason,p.usuarioId());movements.saveAndFlush(m);
  }
 }
}
