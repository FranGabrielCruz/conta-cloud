package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.models.TipoDocumentoImpresion;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.util.function.*;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;

/** Closed registration boundary: adding an enum without an adapter prevents application startup. */
@Component
public class BillingPrintDocumentRegistry {
 public record Document(UUID branchId,UUID cashRegisterId,Supplier<ArchivoPdfDto> screen,Supplier<ArchivoPdfDto> printer){
  public Document{Objects.requireNonNull(screen);Objects.requireNonNull(printer);}
 }
 public record Registration(TipoDocumentoImpresion type,String permission,Function<UUID,Document> load){
  public Registration{Objects.requireNonNull(type);Objects.requireNonNull(permission);Objects.requireNonNull(load);}
 }
 private final Map<TipoDocumentoImpresion,Registration> registrations=new EnumMap<>(TipoDocumentoImpresion.class);
 public BillingPrintDocumentRegistry(List<Registration> definitions){
  for(var definition:definitions)if(registrations.putIfAbsent(definition.type(),definition)!=null)throw new IllegalStateException("Documento de impresión duplicado: "+definition.type());
  var missing=EnumSet.allOf(TipoDocumentoImpresion.class);missing.removeAll(registrations.keySet());
  if(!missing.isEmpty())throw new IllegalStateException("Documentos de Facturación sin adaptador de impresión: "+missing);
 }
 Document load(TipoDocumentoImpresion type,UUID id){
  var registration=registrations.get(type);if(registration==null||id==null)throw new ReglaNegocioException("Documento de impresión no registrado.");
  if(!TenantContext.principalActual().permisos().contains(registration.permission()))throw new AccessDeniedException("No tienes permiso para imprimir este documento.");
  var document=Objects.requireNonNull(registration.load().apply(id));
  if(document.branchId()==null||document.cashRegisterId()==null)throw new ReglaNegocioException("El documento histórico no conserva una caja de origen para resolver su agente.");
  if(!EmpresaContext.permiteSucursal(document.branchId()))throw new RecursoNoEncontradoException("Documento no encontrado.");
  return document;
 }
 public Set<TipoDocumentoImpresion> registeredTypes(){return Set.copyOf(registrations.keySet());}
}
