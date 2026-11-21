package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Fuente;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Orquestador interno. Participa en la transacción de la operación, sin exigir permisos de configuración. */
@Service
public class AccountingPostingService {
 private final RegistroIntegracionesContables integraciones;
 private final ConfiguracionMotorService configuracion;
 private final AsientoContableService asientos;
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AccountingPostingService.class);
 public AccountingPostingService(RegistroIntegracionesContables integraciones,ConfiguracionMotorService configuracion,AsientoContableService asientos) {
  this.integraciones=integraciones;this.configuracion=configuracion;this.asientos=asientos;
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID contabilizar(EventoContable e) {
  Objects.requireNonNull(e);Objects.requireNonNull(e.fuente());
  try {
   var existente=asientos.automaticoExistente(e.fuente());
   if(existente.isPresent()) { registrar("PostingDuplicateIgnored",e,existente.get(),null);return existente.get(); }
   if(!configuracion.efectivo(Modulo.valueOf(e.fuente().origen().name())))return null;
   var handler=integraciones.resolver(e);
   var id=asientos.registrarAutomatico(e.fuente(),e.fecha(),e.moneda(),e.concepto(),handler.proponer(e),e.version(),e.traceId(),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(e.datos()));
   registrar("PostingSucceeded",e,id,null);return id;
  } catch(ReglaNegocioException ex) {
   String evento=switch(ex.getCode()) {
    case ACCOUNTING_PERIOD_CLOSED -> "PostingBlockedClosedPeriod";
    case ACCOUNTING_MISSING_CONFIGURATION -> "PostingMissingConfiguration";
    default -> "PostingFailed";
   };
   registrar(evento,e,null,ex.getCode().name());throw ex;
  } catch(RuntimeException ex) {
   registrar("PostingFailed",e,null,"UNEXPECTED_ERROR");throw ex;
  }
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID reversar(Fuente fuente,LocalDate fecha,String motivo) { return asientos.reversarAutomatico(fuente,fecha,motivo); }
 @Transactional(readOnly=true)
 public Optional<UUID> consultar(Fuente fuente) { return asientos.consultarAutomatico(fuente); }
 private void registrar(String evento,EventoContable e,UUID asiento,String error) {
  var p=TenantContext.principalActual();
  LOG.info("event={} traceId={} tenantId={} companyId={} sourceModule={} sourceDocumentId={} sourceDocumentType={} sourceEvent={} sourceVersion={} journalEntryId={} errorCode={}",
   evento,e.traceId(),p.tenantId(),p.empresaId(),e.fuente().origen(),e.fuente().id(),e.fuente().tipo(),e.fuente().evento(),e.version(),asiento,error);
 }
}

