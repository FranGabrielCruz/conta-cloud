package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class InventarioEventoContableService {
 private final ConfiguracionMotorService config;
 private final AccountingPostingService posting;
 private final ValoracionInventarioContableService valoracion;
 public InventarioEventoContableService(ConfiguracionMotorService config,AccountingPostingService posting,ValoracionInventarioContableService valoracion) {
  this.config=config;this.posting=posting;this.valoracion=valoracion;
 }
 public UUID ajuste(AjusteInventario a,List<LineaAjusteInventario> lineas) {
  ambito(a.getTenantId(),a.getEmpresaId());
  return registrar(new Fuente(Origen.INVENTARIO,"AJUSTE_INVENTARIO",a.getId(),a.getNumero(),"CONFIRMACION"),a.getVersion(),a.getFecha(),
   lineas.stream().collect(Collectors.toMap(LineaAjusteInventario::getProductoId,LineaAjusteInventario::getCantidadBase,BigDecimal::add)));
 }
 public UUID conteo(ConteoFisico c,List<LineaConteoFisico> lineas) {
  ambito(c.getTenantId(),c.getEmpresaId());
  return registrar(new Fuente(Origen.INVENTARIO,"CONTEO_FISICO",c.getId(),c.getNumero(),"CONFIRMACION"),c.getVersion(),c.getFecha(),
   lineas.stream().filter(l->l.getDiferencia().signum()!=0).collect(Collectors.toMap(LineaConteoFisico::getProductoId,LineaConteoFisico::getDiferencia,BigDecimal::add)));
 }
 public UUID revertir(AjusteInventario a,LocalDate fecha,String motivo) {
  ambito(a.getTenantId(),a.getEmpresaId());
  return posting.reversar(new Fuente(Origen.INVENTARIO,"AJUSTE_INVENTARIO",a.getId(),a.getNumero(),"CONFIRMACION"),fecha,motivo);
 }
 public UUID costoVenta(FacturaVenta f,List<LineaFacturaVenta> lineas) {
  ambito(f.getTenantId(),f.getEmpresaId());
  if(!config.efectivo(Modulo.VENTAS))return null;
  var cantidades=lineas.stream().filter(LineaFacturaVenta::isControlaExistencia)
   .collect(Collectors.toMap(LineaFacturaVenta::getProductoId,LineaFacturaVenta::getCantidad,BigDecimal::add));
  if(cantidades.isEmpty())return null;
  var valores=valoracion.valorar(cantidades,f.getFecha());var importe=valores.stream().map(EventoContable.Valoracion::importeBase).reduce(BigDecimal.ZERO,BigDecimal::add);
  if(importe.signum()==0)return null;
  return posting.contabilizar(new EventoContable(new Fuente(Origen.VENTAS,"FACTURA",f.getId(),f.getNumero(),"COSTO"),0,f.getFecha(),
   valoracion.monedaBase(),"Costo de venta "+f.getNumero(),TraceContext.currentOrCreate(),new EventoContable.CostoVenta(importe,valores)));
 }
 private UUID registrar(Fuente fuente,long version,LocalDate fecha,Map<UUID,BigDecimal> cantidades) {
  if(!config.efectivo(Modulo.INVENTARIO)||cantidades.isEmpty())return null;
  var valores=valoracion.valorar(cantidades,fecha);
  BigDecimal entradas=BigDecimal.ZERO,salidas=BigDecimal.ZERO;
  for(var v:valores)if(v.importeBase().signum()>0)entradas=entradas.add(v.importeBase());else salidas=salidas.subtract(v.importeBase());
  if(entradas.signum()==0&&salidas.signum()==0)return null;
  return posting.contabilizar(new EventoContable(fuente,version,fecha,valoracion.monedaBase(),"Ajuste propio de inventario "+fuente.numero(),
   TraceContext.currentOrCreate(),new EventoContable.Inventario(entradas,salidas,valores)));
 }
 private static void ambito(UUID tenant,UUID empresa) {
  var p=TenantContext.principalActual();
  if(!p.tenantId().equals(tenant)||!p.empresaId().equals(empresa))throw new org.springframework.security.access.AccessDeniedException("El documento no está disponible.");
 }
}

