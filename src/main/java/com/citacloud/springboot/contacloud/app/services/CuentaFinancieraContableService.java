package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.*;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** Asociación explícita en cada recurso financiero, sin defaults globales. */
@Service
public class CuentaFinancieraContableService {
 private final NamedParameterJdbcTemplate jdbc;
 private final ConfiguracionMotorRepository cuentas;
 private final CuentaContableRepository catalogo;
 private final EmpresaModuloService modulos;
 private final AuditoriaService auditoria;
 private static final Funcion FUNCION=new Funcion("RECURSO_FINANCIERO","Cuenta financiera",Set.of(),Set.of(),Set.of("ACTIVO"));
 public CuentaFinancieraContableService(NamedParameterJdbcTemplate jdbc,ConfiguracionMotorRepository cuentas,
  CuentaContableRepository catalogo,EmpresaModuloService modulos,AuditoriaService auditoria) {
  this.jdbc=jdbc;this.cuentas=cuentas;this.catalogo=catalogo;this.modulos=modulos;this.auditoria=auditoria;
 }
 private static String tabla(TipoCuentaDinero tipo) {
  if(tipo==null)throw new ReglaNegocioException("Selecciona un recurso financiero.");
  return tipo==TipoCuentaDinero.CASH_REGISTER?"cajas":"cuentas_bancarias";
 }
 private static MapSqlParameterSource args(TenantPrincipal p,UUID id) {
  return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId()).addValue("id",id);
 }
 private void permiso(TipoCuentaDinero tipo,boolean editar) {
  String recurso=tipo==TipoCuentaDinero.CASH_REGISTER?"cajas":"cuentas_bancarias";
  var p=TenantContext.principalActual();
  if(!modulos.habilitado("CAJA_BANCOS") || !p.permisos().contains(recurso+"."+ (editar?"editar":"ver")))
   throw new AccessDeniedException("No tienes permiso para configurar este recurso financiero.");
  if(editar && !p.permisos().contains("configuracion_contable.editar") && !p.permisos().contains("CONFIGURACION_CONTABLE_EDITAR"))
   throw new AccessDeniedException("No tienes permiso para modificar las asociaciones contables.");
 }
 private UUID asociada(TenantPrincipal p,TipoCuentaDinero tipo,UUID id) {
  String filtro=tipo==TipoCuentaDinero.CASH_REGISTER && !p.accesoTodasSucursales()
   ?" AND sucursal_id IN (:sucursales)":"";
  var a=args(p,id).addValue("sucursales",p.sucursalIds().isEmpty()?Set.of(new UUID(0,0)):p.sucursalIds());
  var rows=jdbc.query("SELECT cuenta_contable_id FROM "+tabla(tipo)+
   " WHERE tenant_id=:tenant AND empresa_id=:empresa AND id=:id AND activo"+filtro,a,
   (r,n)->Optional.ofNullable(r.getObject(1,UUID.class)));
  if(rows.isEmpty())throw new RecursoNoEncontradoException("El recurso financiero no está disponible.");
  return rows.getFirst().orElse(null);
 }
 @Transactional(readOnly=true)
 public Cuenta obtener(TipoCuentaDinero tipo,UUID id) {
  permiso(tipo,false);var p=TenantContext.principalActual();var cuenta=asociada(p,tipo,id);
  return cuenta==null?null:cuentas.cuenta(p,cuenta).orElse(null);
 }
 @Transactional(readOnly=true)
 public List<Cuenta> buscar(TipoCuentaDinero tipo,String filtro,int offset,int limite) {
  permiso(tipo,true);
  return cuentas.buscarCompatible(TenantContext.principalActual(),FUNCION,filtro==null?"":filtro.trim(),
   Math.max(0,offset),Math.clamp(limite,1,50));
 }
 @Transactional
 public void guardar(TipoCuentaDinero tipo,UUID id,UUID anterior,UUID cuenta) {
  permiso(tipo,true);var p=TenantContext.principalActual();catalogo.bloquearEmpresa(p);
  if(!Objects.equals(anterior,asociada(p,tipo,id)))throw new ReglaNegocioException("La cuenta asociada cambió. Vuelve a abrir el recurso.");
  if(cuenta!=null && !FuncionesContables.compatible(FUNCION,cuentas.cuenta(p,cuenta).orElse(null)))
   throw new ReglaNegocioException("Selecciona una cuenta de activo, activa y de movimiento de esta empresa.");
  jdbc.update("UPDATE "+tabla(tipo)+" SET cuenta_contable_id=:cuenta WHERE tenant_id=:tenant AND empresa_id=:empresa AND id=:id",
   args(p,id).addValue("cuenta",cuenta));
  Map<String,Object> detalle=new LinkedHashMap<>();detalle.put("tipo",tipo);detalle.put("accountIdAnterior",anterior);
  detalle.put("accountIdNuevo",cuenta);detalle.put("tenantId",p.tenantId());detalle.put("companyId",p.empresaId());
  detalle.put("traceId",com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());
  auditoria.registrar("FinancialResourceAccountUpdated",tipo.name(),id,JsonMapper.builder().build().writeValueAsString(detalle));
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID paraOperacion(TipoCuentaDinero tipo,UUID id) {
  var p=TenantContext.principalActual();catalogo.bloquearEmpresa(p);
  UUID cuenta=asociada(p,tipo,id);
  if(cuenta==null || !FuncionesContables.compatible(FUNCION,cuentas.cuenta(p,cuenta).orElse(null)))
   throw new ReglaNegocioException("Configura una cuenta contable activa de movimiento en la caja o cuenta bancaria utilizada.");
  return cuenta;
 }
}

