package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ConfiguracionMotorService {
 private static final String VER="@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('configuracion_contable.ver','CONFIGURACION_CONTABLE_VER')";
 private final ConfiguracionMotorRepository repo;
 private final CuentaContableRepository catalogo;
 private final ConfiguracionContableService general;
 private final AuditoriaService auditoria;
 private final RegistroIntegracionesContables integraciones;
 private static final JsonMapper JSON=JsonMapper.builder().build();
 public ConfiguracionMotorService(ConfiguracionMotorRepository repo,CuentaContableRepository catalogo,
                                  ConfiguracionContableService general,AuditoriaService auditoria,RegistroIntegracionesContables integraciones) {
  this.repo=repo; this.catalogo=catalogo; this.general=general; this.auditoria=auditoria;
  this.integraciones=integraciones;
 }
 @Transactional
 @PreAuthorize(VER)
 public Estado obtener() {
  general.obtener();
  return estado(TenantContext.principalActual());
 }
 private Estado estado(TenantPrincipal p) {
  var g=repo.general(p); var cuentas=repo.cuentas(p);
  boolean disponible=repo.existe(p,"cuenta_contable");
  return respuesta(g.version(),g.metodo(),g.automatico(),disponible,
   repo.existe(p,"periodos_fiscales"),cuentas,estados(cuentas,repo.modulos(p),g.automatico(),disponible));
 }
 @Transactional(readOnly=true)
 @PreAuthorize(VER)
 public Estado evaluar(Entrada e) {
  if(e==null || e.cuentas()==null || e.modulos()==null || e.cuentas().size()>FuncionesContables.TODAS.size() ||
     e.modulos().keySet().stream().anyMatch(m->!Modulo.automatizables().contains(m)))
   throw new ReglaNegocioException("La configuración recibida no es válida.");
  var p=TenantContext.principalActual();var anterior=estado(p);var cuentas=new HashMap<>(anterior.cuentas());
  e.cuentas().forEach((clave,id)->{
   FuncionesContables.funcion(clave);
   if(id==null)cuentas.remove(clave);
   else cuentas.put(clave,repo.cuenta(p,id).orElseThrow(()->new ReglaNegocioException("La cuenta seleccionada no está disponible.")));
  });
  var flags=repo.modulos(p);flags.putAll(e.modulos());
  return respuesta(anterior.version(),"DEVENGADO",e.automatico(),anterior.catalogo(),anterior.periodos(),cuentas,
   estados(cuentas,flags,e.automatico(),anterior.catalogo()));
 }
 private Estado respuesta(long version,String metodo,boolean global,boolean catalogo,boolean periodos,Map<String,Cuenta> cuentas,List<EstadoModulo> estados) {
  return new Estado(version,metodo,global,catalogo,periodos,cuentas,
   estados.stream().filter(m->Modulo.automatizables().contains(m.modulo())).toList(),
   estados.stream().filter(m->m.modulo()!=Modulo.FINANZAS).toList(),
   TenantContext.principalActual().permisos().contains("configuracion_contable.automatizacion"));
 }
 private List<EstadoModulo> estados(Map<String,Cuenta> cuentas,Map<Modulo,Boolean> flags,boolean global,boolean disponible) {
  return FuncionesContables.estados(cuentas,flags,global&&disponible).stream().map(m->{
   boolean integrado=m.modulo()==Modulo.CIERRE||integraciones.disponible(m.modulo());
   boolean permisos=TenantContext.principalActual().permisos().contains("configuracion_contable.automatizacion");
   var pendientes=m.pendientes();boolean completo=m.completo()&&disponible;String motivo="";
   if(!disponible) { pendientes=List.of("Catálogo de cuentas");motivo="El Catálogo de cuentas no está disponible."; }
   if(disponible&&!completo&&m.modulo()!=Modulo.FINANZAS)motivo=FuncionesContables.TODAS.stream()
    .filter(f->f.obligatoria().contains(m.modulo())&&cuentas.containsKey(f.clave())&&!FuncionesContables.compatible(f,cuentas.get(f.clave())))
    .map(f->"«"+f.nombre()+"» requiere una cuenta activa de movimiento de clasificación "+String.join(" / ",f.clasificaciones())+".")
    .collect(java.util.stream.Collectors.joining(" "));
   if(completo&&!integrado)motivo="Las cuentas están configuradas. La integración de eventos contables de este módulo todavía no está disponible.";
   var estado=!completo?EstadoConfiguracion.INCOMPLETE:!integrado?EstadoConfiguracion.UNAVAILABLE:EstadoConfiguracion.READY;
   return new EstadoModulo(m.modulo(),completo,m.automatico(),global&&m.automatico()&&completo&&integrado,
    pendientes,estado,motivo,integrado,permisos,integrado?null:"INTEGRATION_NOT_IMPLEMENTED");
  }).toList();
 }
 @Transactional
 @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('configuracion_contable.editar','CONFIGURACION_CONTABLE_EDITAR','configuracion_contable.automatizacion')")
 public Estado guardar(Entrada e) {
  if(e==null || !"DEVENGADO".equals(e.metodo()) || e.cuentas()==null || e.modulos()==null ||
     e.cuentas().size()>FuncionesContables.TODAS.size() || e.modulos().keySet().stream().anyMatch(m->!Modulo.automatizables().contains(m)) ||
     e.modulos().values().stream().anyMatch(Objects::isNull))
   throw new ReglaNegocioException("La configuración recibida no es válida.");
  var p=TenantContext.principalActual();
  if(!e.cuentas().isEmpty()&&!p.permisos().contains("configuracion_contable.editar")&&!p.permisos().contains("CONFIGURACION_CONTABLE_EDITAR"))
   throw new AccessDeniedException("No tienes permiso para modificar las cuentas contables.");
  catalogo.bloquearEmpresa(p);
  var anterior=estado(p);
  if(!anterior.catalogo() && (e.automatico() || e.modulos().values().stream().anyMatch(Boolean.TRUE::equals)))
   throw new ReglaNegocioException("El Catálogo de cuentas no está disponible. Configúralo antes de activar la automatización.");
  if(anterior.version()!=e.version()) throw new ReglaNegocioException("La configuración cambió. Recarga la pantalla antes de guardar.");
  var flags=repo.modulos(p);
  boolean cambia=e.automatico()!=anterior.automatico() || e.modulos().entrySet().stream()
   .anyMatch(x->x.getValue()!=Boolean.TRUE.equals(flags.get(x.getKey())));
  if(cambia && !p.permisos().contains("configuracion_contable.automatizacion"))
   throw new AccessDeniedException("No tienes permiso para modificar la automatización.");
  var cuentas=new HashMap<>(anterior.cuentas());
  e.cuentas().forEach((clave,id)->{
   var f=FuncionesContables.funcion(clave);
   if(id==null) cuentas.remove(clave);
   else {
    var c=repo.cuenta(p,id).orElseThrow(()->new ReglaNegocioException("La cuenta seleccionada no está disponible."));
    if(!FuncionesContables.compatible(f,c))
     throw new ReglaNegocioException("Selecciona una cuenta activa de movimiento compatible con "+f.nombre()+".");
    cuentas.put(clave,c);
   }
  });
  flags.putAll(e.modulos());
  var estados=estados(cuentas,flags,e.automatico(),anterior.catalogo()).stream().filter(m->Modulo.automatizables().contains(m.modulo())).toList();
  var anteriores=repo.modulos(p);
  for(var m:estados) if(m.automatico()&&(!m.completo()||!m.integrationAvailable())&&!Boolean.TRUE.equals(anteriores.get(m.modulo())))
   throw new ReglaNegocioException(m.modulo().etiqueta()+": "+(m.estado()==EstadoConfiguracion.UNAVAILABLE?m.motivo():
    "completa "+String.join(", ",m.pendientes())+" antes de activar la automatización."));
  if(e.automatico() && estados.stream().noneMatch(m->m.completo()&&m.integrationAvailable()))
   throw new ReglaNegocioException("Configura al menos un módulo disponible antes de activar la contabilización automática.");
  repo.guardar(p,e);
  e.cuentas().forEach((clave,id)->{
   var previa=anterior.cuentas().get(clave);
   if(!Objects.equals(previa==null?null:previa.id(),id)) {
    Map<String,Object> cambio=new LinkedHashMap<>();
    cambio.put("funcion",clave);cambio.put("accountIdAnterior",previa==null?null:previa.id());cambio.put("accountIdNuevo",id);
    cambio.put("tenantId",p.tenantId());cambio.put("companyId",p.empresaId());cambio.put("userId",p.usuarioId());
    cambio.put("traceId",com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());
    auditoria.registrar("AccountingAccountMappingUpdated","ConfiguracionContable",p.empresaId(),JSON.writeValueAsString(cambio));
   }
  });
  var nuevo=estado(p);
  auditoria.registrar("AccountingConfigurationUpdated","ConfiguracionContable",p.empresaId(),
   JSON.writeValueAsString(Map.of("anterior",anterior,"solicitud",e,"nuevo",nuevo,
    "traceId",com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate())));
  if(e.automatico()!=anterior.automatico())
   evento(p,e.automatico()?"AutomaticPostingEnabled":"AutomaticPostingDisabled",null);
  e.modulos().forEach((m,on)->{
   boolean antes=anterior.modulos().stream().filter(x->x.modulo()==m).findFirst().orElseThrow().automatico();
   if(on!=antes)evento(p,on?"ModuleAutomaticPostingEnabled":"ModuleAutomaticPostingDisabled",m);
  });
  return nuevo;
 }
 private void evento(TenantPrincipal p,String evento,Modulo modulo) {
  String trace=com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate();
  var detalle=Map.of("traceId",trace,"tenantId",p.tenantId(),"companyId",p.empresaId(),"userId",p.usuarioId(),"module",modulo==null?"GLOBAL":modulo.name());
  auditoria.registrar(evento,"ConfiguracionContable",p.empresaId(),JSON.writeValueAsString(detalle));
  org.slf4j.LoggerFactory.getLogger(getClass()).info("event={} traceId={} tenantId={} companyId={} userId={} module={}",
   evento,trace,p.tenantId(),p.empresaId(),p.usuarioId(),modulo);
 }
 @Transactional(readOnly=true)
 @PreAuthorize(VER)
 public List<Cuenta> buscar(String clave,String texto,int offset,int limite) {
  var f=FuncionesContables.funcion(clave);
  // Filtrar en SQL por función evita páginas vacías cuando existen miles de cuentas.
  return repo.buscarCompatible(TenantContext.principalActual(),f,texto==null?"":texto.trim(),Math.max(0,offset),Math.clamp(limite,1,50));
 }
 /** Revalidación dentro de la misma transacción y bloqueo que la operación y el asiento. */
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public boolean efectivo(Modulo modulo) {
  if(modulo==Modulo.CIERRE) return false;
  var p=TenantContext.principalActual();
  catalogo.bloquearEmpresa(p);
  if(!repo.tieneConfiguracion(p))return false;
  var g=repo.general(p);
  if(!g.automatico() || modulo!=Modulo.FINANZAS&&!Boolean.TRUE.equals(repo.modulos(p).get(modulo))) return false;
  if(!repo.existe(p,"cuenta_contable")) throw new ReglaNegocioException("El Catálogo de cuentas no está disponible.");
  var s=estados(repo.cuentas(p),repo.modulos(p),true,true).stream().filter(x->x.modulo()==modulo).findFirst().orElseThrow();
  if(!s.completo()) throw new ReglaNegocioException(com.citacloud.springboot.contacloud.app.errors.ErrorCode.ACCOUNTING_MISSING_CONFIGURATION,"La configuración contable de "+modulo.etiqueta()+" está incompleta: "+String.join(", ",s.pendientes())+".");
  if(!s.integrationAvailable()) throw new ReglaNegocioException(com.citacloud.springboot.contacloud.app.errors.ErrorCode.INTEGRATION_NOT_IMPLEMENTED,s.motivo());
  return true;
 }
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public void validarFunciones(Set<String> claves) {
  var cuentas=repo.cuentas(TenantContext.principalActual());
  for(var clave:claves) {
   var f=FuncionesContables.funcion(clave);
   if(!FuncionesContables.compatible(f,cuentas.get(clave)))
    throw new ReglaNegocioException(com.citacloud.springboot.contacloud.app.errors.ErrorCode.ACCOUNTING_MISSING_CONFIGURATION,
     "Configura una cuenta activa de movimiento compatible con «"+f.nombre()+"» para esta operación.");
  }
 }
}

