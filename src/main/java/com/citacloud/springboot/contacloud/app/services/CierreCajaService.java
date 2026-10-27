package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CierreCajaService {
 private static final int MAX_QTY=1_000_000; private static final String OP="CASH_CLOSURE_CONFIRM";
 private final CashRegisterSessionRepository sesiones; private final FinancialMovementRepository movimientos;
 private final DenominacionCajaRepository denominaciones; private final CierreCajaRepository cierres;
 private final DetalleCierreCajaRepository detalles; private final ResumenMedioPagoCierreCajaRepository medios;
 private final EmpresaRepository empresas; private final AuditoriaService auditoria;
 private final IdempotentOperationService idempotencia; private final JdbcTemplate jdbc;
 public CierreCajaService(CashRegisterSessionRepository s,FinancialMovementRepository m,DenominacionCajaRepository d,
        CierreCajaRepository c,DetalleCierreCajaRepository de,ResumenMedioPagoCierreCajaRepository mp,
        EmpresaRepository e,AuditoriaService a,IdempotentOperationService i,JdbcTemplate j){sesiones=s;movimientos=m;denominaciones=d;cierres=c;detalles=de;medios=mp;empresas=e;auditoria=a;idempotencia=i;jdbc=j;}

 @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('cierres_caja.ejecutar','operaciones_caja.cerrar')")
 public PreparacionCierreCajaDto preparar(UUID sesionId){SesionCaja s=sesionAbierta(sesionId,false);var p=TenantContext.principalActual();return new PreparacionCierreCajaDto(s.getId(),s.getCodigoVisible(),s.getCaja().getNombre(),s.getSucursal().getNombre(),s.getMoneda().getCodigoIso(),s.getMoneda().getSimbolo(),s.getAbiertoEn(),p.nombre(),catalogo(s).stream().map(d->new PreparacionCierreCajaDto.Denominacion(d.getId(),d.getValor(),d.getTipo(),d.getOrden())).toList());}

 @Transactional @PreAuthorize("hasAnyAuthority('cierres_caja.ejecutar','operaciones_caja.cerrar')")
 public CierreCajaDto confirmar(ConfirmarCierreCajaInput input){
  if(input==null||input.sesionId()==null||input.idempotencia()==null)throw new ReglaNegocioException("No fue posible identificar el cierre de caja.");
  var p=TenantContext.principalActual();UUID empresaId=EmpresaContext.requerirEmpresaId();String fp=CommandFingerprint.sha256(input);
  var inicio=idempotencia.begin(p.tenantId(),empresaId,OP,input.idempotencia(),fp);if(inicio.replay())return obtenerInterno(inicio.resultId());
  SesionCaja s=sesionAbierta(input.sesionId(),true);if(cierres.findByTenantIdAndEmpresaIdAndSesionId(p.tenantId(),empresaId,s.getId()).isPresent())throw new ReglaNegocioException("El turno de caja ya fue cerrado.");
  List<DenominacionCaja> catalogo=catalogo(s);Map<UUID,DenominacionCaja> permitidas=catalogo.stream().collect(Collectors.toMap(DenominacionCaja::getId,Function.identity()));Map<UUID,Integer> cantidades=validar(input.cantidades(),permitidas);
  BigDecimal contado=cantidades.entrySet().stream().map(x->permitidas.get(x.getKey()).getValor().multiply(BigDecimal.valueOf(x.getValue()))).reduce(BigDecimal.ZERO,BigDecimal::add);
  OffsetDateTime corte=OffsetDateTime.now();List<MovimientoFinanciero> ms=movimientos.movimientosValidosSesionHasta(p.tenantId(),empresaId,s.getId(),corte);CierreCaja.Resumen resumen=resumen(ms);BigDecimal esperado=s.getFondoInicial().add(resumen.totalEntradas()).subtract(resumen.totalSalidas());String observacion=nota(input.observacion());
  if(contado.compareTo(esperado)!=0&&observacion==null)throw new ReglaNegocioException("El cierre presenta una diferencia. Indique una observación para continuar.");
  Empresa empresa=empresas.findByIdAndTenantId(empresaId,p.tenantId()).orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
  CierreCaja c=new CierreCaja(p.tenantId(),empresaId,siguienteNumero(p.tenantId(),empresaId),s,corte,p.usuarioId(),p.nombre(),resumen,contado,observacion,input.idempotencia(),empresa);
  try{c=cierres.saveAndFlush(c);int orden=1;for(DenominacionCaja d:catalogo)detalles.save(new DetalleCierreCaja(p.tenantId(),empresaId,c.getId(),d,cantidades.getOrDefault(d.getId(),0),orden++));guardarMedios(p.tenantId(),empresaId,c.getId(),ms);detalles.flush();medios.flush();s.cerrar(c.getEsperado(),c.getContado(),observacion,p.usuarioId(),corte);sesiones.saveAndFlush(s);}catch(DataIntegrityViolationException ex){throw new ReglaNegocioException("El turno de caja ya fue cerrado.",ex);}
  auditoria.registrar("CASH_REGISTER_CLOSED","CierreCaja",c.getId(),"{\"sesionId\":\""+s.getId()+"\",\"numero\":\""+c.getNumero()+"\",\"resultado\":\""+c.getResult()+"\"}");idempotencia.complete(p.tenantId(),empresaId,OP,input.idempotencia(),"CierreCaja",c.getId());return dto(c,detalles.findAllByCierreIdOrderByOrdenAsc(c.getId()),medios.findAllByCierreIdOrderByOrdenAsc(c.getId()));
 }
 @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial')") public CierreCajaDto obtener(UUID id){return obtenerInterno(id);}
 @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial')") public CierreCajaDto obtenerPorSesion(UUID sesionId){var p=TenantContext.principalActual();CierreCaja c=cierres.findByTenantIdAndEmpresaIdAndSesionId(p.tenantId(),EmpresaContext.requerirEmpresaId(),sesionId).orElseThrow(()->new RecursoNoEncontradoException("Cierre histórico no encontrado."));return dto(c,detalles.findAllByCierreIdOrderByOrdenAsc(c.getId()),medios.findAllByCierreIdOrderByOrdenAsc(c.getId()));}
 @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('cierres_caja.ver','operaciones_caja.ver_historial')") public Page<CierreCajaDto> historial(int pagina,int tamano){if(pagina<0||!Set.of(10,25,50,100).contains(tamano))throw new ReglaNegocioException("Paginación inválida.");var p=TenantContext.principalActual();return cierres.findAllByTenantIdAndEmpresaId(p.tenantId(),EmpresaContext.requerirEmpresaId(),PageRequest.of(pagina,tamano,Sort.by(Sort.Direction.DESC,"cerradoEn","id"))).map(x->dto(x,List.of(),List.of()));}
 private CierreCajaDto obtenerInterno(UUID id){var p=TenantContext.principalActual();CierreCaja c=cierres.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(()->new RecursoNoEncontradoException("Cierre de caja no encontrado."));return dto(c,detalles.findAllByCierreIdOrderByOrdenAsc(id),medios.findAllByCierreIdOrderByOrdenAsc(id));}
 private SesionCaja sesionAbierta(UUID id,boolean lock){var p=TenantContext.principalActual();SesionCaja s=(lock?sesiones.bloquear(id,p.tenantId(),EmpresaContext.requerirEmpresaId()):sesiones.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),EmpresaContext.requerirEmpresaId())).orElseThrow(()->new ReglaNegocioException("La caja debe estar abierta para realizar el cierre."));if(s.getStatus()!=EstadoSesionCaja.OPEN||!EmpresaContext.permiteSucursal(s.getSucursalId())||!s.getAbiertoPor().equals(p.usuarioId()))throw new ReglaNegocioException("Solo el cajero del turno abierto puede cerrar esta caja.");return s;}
 private List<DenominacionCaja> catalogo(SesionCaja s){List<DenominacionCaja> ds=denominaciones.findAllByTenantIdAndEmpresaIdAndMonedaIdAndActiveTrueOrderByOrdenAsc(s.getTenantId(),s.getEmpresaId(),s.getMonedaId());if(ds.isEmpty())throw new ReglaNegocioException("La configuración de denominaciones de la moneda no está disponible.");return ds;}
 private Map<UUID,Integer> validar(List<ConfirmarCierreCajaInput.Cantidad> lineas,Map<UUID,DenominacionCaja> permitidas){Map<UUID,Integer> r=new HashMap<>();if(lineas==null)lineas=List.of();for(var l:lineas){if(l==null||l.denominacionId()==null||!permitidas.containsKey(l.denominacionId()))throw new ReglaNegocioException("La denominación seleccionada no es válida.");if(l.cantidad()==null||l.cantidad()<0||l.cantidad()>MAX_QTY)throw new ReglaNegocioException("La cantidad debe ser un entero entre 0 y "+MAX_QTY+".");if(r.put(l.denominacionId(),l.cantidad())!=null)throw new ReglaNegocioException("No se puede repetir una denominación.");}return r;}
 static CierreCaja.Resumen resumen(List<MovimientoFinanciero> ms){BigDecimal ventas=z(),cobros=z(),otrasIn=z(),vales=z(),devoluciones=z(),otrasOut=z();for(MovimientoFinanciero m:ms){if(m.getMedioPago()!=MedioPagoMovimiento.CASH)continue;if(m.getTipoMovimiento()==TipoMovimientoFinanciero.INCOME){if(m.getTipoOrigen()==TipoOrigenMovimiento.SALES_INVOICE)ventas=ventas.add(m.getMonto());else if(Set.of(TipoOrigenMovimiento.SALES_PAYMENT,TipoOrigenMovimiento.CUSTOMER_RECEIPT).contains(m.getTipoOrigen()))cobros=cobros.add(m.getMonto());else otrasIn=otrasIn.add(m.getMonto());}else{if(m.getTipoOrigen()==TipoOrigenMovimiento.CASH_VOUCHER)vales=vales.add(m.getMonto());else if(m.getTipoOrigen()==TipoOrigenMovimiento.REVERSAL)devoluciones=devoluciones.add(m.getMonto());else otrasOut=otrasOut.add(m.getMonto());}}return new CierreCaja.Resumen(ventas,cobros,otrasIn,vales,devoluciones,otrasOut,ventas.add(cobros).add(otrasIn),vales.add(devoluciones).add(otrasOut));}
 private void guardarMedios(UUID t,UUID e,UUID cierre,List<MovimientoFinanciero> ms){EnumMap<MedioPagoMovimiento,BigDecimal> sumas=new EnumMap<>(MedioPagoMovimiento.class);for(MovimientoFinanciero m:ms)if(m.getTipoMovimiento()==TipoMovimientoFinanciero.INCOME)sumas.merge(m.getMedioPago(),m.getMonto(),BigDecimal::add);int orden=1;for(var x:sumas.entrySet())medios.save(new ResumenMedioPagoCierreCaja(t,e,cierre,x.getKey(),x.getValue(),orden++));}
 private String siguienteNumero(UUID t,UUID e){jdbc.update("insert into cash_closure_sequence(tenant_id,empresa_id,current_value) values (?,?,0) on conflict do nothing",t,e);Long n=jdbc.queryForObject("update cash_closure_sequence set current_value=current_value+1 where tenant_id=? and empresa_id=? returning current_value",Long.class,t,e);return "CIE-"+String.format("%06d",n);}
 private static String nota(String v){if(v==null||v.isBlank())return null;String n=v.trim();if(n.length()>500)throw new ReglaNegocioException("La observación excede 500 caracteres.");return n;}
 private static BigDecimal z(){return BigDecimal.ZERO;}
 private static CierreCajaDto dto(CierreCaja c,List<DetalleCierreCaja> ds,List<ResumenMedioPagoCierreCaja> ms){return new CierreCajaDto(c.getId(),c.getNumero(),c.getSesionId(),c.getTurno(),c.getCaja(),c.getSucursal(),c.getMonedaCodigo(),c.getMonedaSimbolo(),c.getAbiertoEn(),c.getCerradoEn(),c.getAbiertoPorNombre(),c.getCerradoPorNombre(),c.getFondoInicial(),c.getVentasEfectivo(),c.getCobrosEfectivo(),c.getOtrasEntradasEfectivo(),c.getValesCaja(),c.getDevolucionesEfectivo(),c.getOtrasSalidasEfectivo(),c.getTotalEntradasEfectivo(),c.getTotalSalidasEfectivo(),c.getEsperado(),c.getContado(),c.getDiferencia(),c.getResult(),c.getObservation(),ds.stream().map(d->new CierreCajaDto.Detalle(d.getDenomination(),d.getTipo(),d.getQuantity(),d.getSubtotal())).toList(),ms.stream().map(m->new CierreCajaDto.MedioPago(m.getMedio(),m.getAmount())).toList());}
}
