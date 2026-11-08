package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import java.math.*;import java.time.*;import java.util.*;

@Service
public class NotaDebitoVentaService {
 private final FacturaVentaRepository facturas;private final NotaDebitoVentaRepository notas;
 private final ImpuestoRepository impuestos;private final EmpresaRepository empresas;private final DatosEmpresaRepository datos;
 private final PeriodoFiscalRepository periodos;private final IdempotentOperationService idempotencia;private final AuditoriaService auditoria;
 private final PurchaseOrderCalculationService calculos;private final LocalFileStorageService storage;private final CobroContadoDocumentoService cobros;
 public NotaDebitoVentaService(FacturaVentaRepository facturas,NotaDebitoVentaRepository notas,ImpuestoRepository impuestos,EmpresaRepository empresas,DatosEmpresaRepository datos,PeriodoFiscalRepository periodos,IdempotentOperationService idempotencia,AuditoriaService auditoria,PurchaseOrderCalculationService calculos,LocalFileStorageService storage){this(facturas,notas,impuestos,empresas,datos,periodos,idempotencia,auditoria,calculos,storage,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public NotaDebitoVentaService(FacturaVentaRepository facturas,NotaDebitoVentaRepository notas,ImpuestoRepository impuestos,EmpresaRepository empresas,DatosEmpresaRepository datos,PeriodoFiscalRepository periodos,IdempotentOperationService idempotencia,AuditoriaService auditoria,PurchaseOrderCalculationService calculos,LocalFileStorageService storage,CobroContadoDocumentoService cobros){
 this.facturas=facturas;this.notas=notas;this.impuestos=impuestos;this.empresas=empresas;this.datos=datos;this.periodos=periodos;this.idempotencia=idempotencia;this.auditoria=auditoria;this.calculos=calculos;this.storage=storage;this.cobros=cobros;
 }
 @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.ver')")
 public Page<NotaDebitoVentaRepository.Resumen> buscar(String texto,String estado,LocalDate desde,LocalDate hasta,int page,int size){pagina(page,size);if(!Set.of("","CONFIRMED","VOIDED").contains(estado)||desde!=null&&hasta!=null&&desde.isAfter(hasta))throw new ReglaNegocioException("Filtros inválidos.");return notas.buscar(limpiar(texto),estado,desde,hasta,page,size);}
 @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.crear')")
 public Page<NotaDebitoVentaRepository.FacturaOpcion> facturas(String texto,int page,int size){pagina(page,size);return notas.facturas(limpiar(texto),page,size);}
 @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.crear')")
 public LocalDate fechaActual(){return LocalDate.now(zona());}
 @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.crear')")
 public PreparacionNotaDebitoVentaDto preparar(UUID id){
  var f=factura(id,false);var p=TenantContext.principalActual();return new PreparacionNotaDebitoVentaDto(f.getId(),f.getNumero(),f.getFecha(),nombreCliente(f),f.getMonedaCodigo(),f.getTotal(),notas.saldo(f.getId()),f.getCondicionPago(),"Contado".equals(f.getCondicionPago())?"Pagada":"Emitida",notas.motivos(),
   impuestos.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),p.empresaId()).stream().filter(x->x.getTipo()==TipoImpuesto.PERCENTAGE).map(x->new PreparacionNotaDebitoVentaDto.Impuesto(x.getId(),x.getNombre(),x.getPorcentaje())).toList(),f.getClienteId()!=null,f.getSucursalId(),f.getMonedaId());
 }
 @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.crear')")
 public CatalogosCobroContadoDto catalogosPago(UUID facturaId){var f=factura(facturaId,false);return cobros.catalogos(f.getMonedaId(),f.getSucursalId());}
 @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.ver')")
 public NotaDebitoVentaDocumento obtener(UUID id){return notas.documento(id);}
 @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.ver')")
 public NotaDebitoVentaCicloDto ciclo(UUID id){var c=notas.ciclo(id,false);if(c.anulable()){var d=notas.documento(id);if(d.condicionNota()==TipoCondicionPago.CASH&&!cobros.anulable(d))return new NotaDebitoVentaCicloDto(c.estado(),c.version(),false,c.motivo(),c.anuladaPor(),c.anuladaEn(),c.pagado(),c.saldo());try{periodo(d.fecha());periodo(LocalDate.now(zona()));}catch(ReglaNegocioException ex){return new NotaDebitoVentaCicloDto(c.estado(),c.version(),false,c.motivo(),c.anuladaPor(),c.anuladaEn(),c.pagado(),c.saldo());}}return c;}
 @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.crear')")
 public NotaDebitoVentaDocumento calcular(EmitirNotaDebitoVentaInput i){return construir(i,factura(requerir(i),false),false);}
 @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.crear') and hasAuthority('notas_debito.emitir')")
 public NotaDebitoVentaDocumento emitir(EmitirNotaDebitoVentaInput i){
  requerir(i);if(i.idempotencia()==null)throw new ReglaNegocioException("No fue posible identificar la solicitud.");
  var p=TenantContext.principalActual();var intent=idempotencia.begin(p.tenantId(),p.empresaId(),"SALES_DEBIT_NOTE_ISSUE",i.idempotencia(),CommandFingerprint.sha256(i));
  if(intent.replay())return notas.documento(intent.resultId());
  // Same order as sales credits: invoice -> customer -> receivable; sequence last.
  var f=factura(i.facturaId(),true);validarCondicion(i,f);if(f.getClienteId()!=null)notas.cliente(f.getClienteId(),true);var d=construir(i,f,true);
  notas.insertar(d);if(d.condicionNota()==TipoCondicionPago.CREDIT)notas.incrementar(f,d);else cobros.registrar(d);auditar("SALES_DEBIT_NOTE_ISSUED",d,"CONFIRMED",null);
  idempotencia.complete(p.tenantId(),p.empresaId(),"SALES_DEBIT_NOTE_ISSUE",i.idempotencia(),"SALES_DEBIT_NOTE",d.id());return d;
 }
 @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.anular')")
 public NotaDebitoVentaDocumento anular(AnularNotaDebitoVentaInput i){
  if(i==null||i.notaId()==null||i.idempotencia()==null||i.version()<0)throw new ReglaNegocioException("Solicitud de anulación inválida.");
  String reason=texto(i.motivo(),3,1000);var p=TenantContext.principalActual();var d=notas.documento(i.notaId());
  var intent=idempotencia.begin(p.tenantId(),p.empresaId(),"SALES_DEBIT_NOTE_VOID",i.idempotencia(),CommandFingerprint.sha256(i));
  if(intent.replay())return notas.documento(intent.resultId());
  var f=factura(d.facturaId(),true);if(f.getClienteId()!=null)notas.cliente(f.getClienteId(),true);var c=notas.ciclo(d.id(),true);
  if(!"CONFIRMED".equals(c.estado())||c.version()!=i.version())throw new ReglaNegocioException("La nota cambió o ya fue anulada. Actualice la consulta.");
  if(!c.anulable())throw new ReglaNegocioException("Revierta los cobros o créditos aplicados antes de anular la nota.");
  periodo(d.fecha());periodo(LocalDate.now(zona()));if(d.condicionNota()==TipoCondicionPago.CASH)cobros.revertir(d,reason);else notas.revertir(d);notas.anular(d.id(),i.version(),reason,i.idempotencia(),zona().getId());
  auditar("SALES_DEBIT_NOTE_VOIDED",d,"VOIDED",reason);idempotencia.complete(p.tenantId(),p.empresaId(),"SALES_DEBIT_NOTE_VOID",i.idempotencia(),"SALES_DEBIT_NOTE",d.id());return d;
 }
 private NotaDebitoVentaDocumento construir(EmitirNotaDebitoVentaInput i,FacturaVenta f,boolean emitir){
  validarCondicion(i,f);
  if(i.fecha()==null||i.fecha().isBefore(f.getFecha())||i.fecha().isAfter(LocalDate.now(zona())))throw new ReglaNegocioException("La fecha debe estar entre la fecha de factura y la fecha actual de la empresa.");
  periodo(i.fecha());String reason=notas.motivo(i.motivoId()),description=texto(i.descripcion(),1,1000);
  if(i.lineas()==null||i.lineas().isEmpty()||i.lineas().size()>100)throw new ReglaNegocioException("Agregue entre 1 y 100 cargos.");
  var p=TenantContext.principalActual();List<NotaDebitoVentaDocumento.Linea> lines=new ArrayList<>();
  for(var l:i.lineas()){
   if(l==null)throw new ReglaNegocioException("Cargo inválido.");numero(l.cantidad(),true);numero(l.precio(),false);numero(l.descuento(),false);
   String name=null;BigDecimal rate=BigDecimal.ZERO;
   if(l.impuestoId()!=null){var tax=impuestos.findByIdAndTenantIdAndEmpresaId(l.impuestoId(),p.tenantId(),p.empresaId()).filter(x->x.isActivo()&&x.getTipo()==TipoImpuesto.PERCENTAGE).orElseThrow(()->new ReglaNegocioException("Seleccione un impuesto porcentual activo."));name=tax.getNombre();rate=tax.getPorcentaje();}
   // Reuse the configured financial rounding/calculation engine, not credit-note reversal rules.
   var result=calculos.calculate(new LineaOrdenCompraInput(null,texto(l.descripcion(),1,500),l.cantidad(),l.precio(),l.descuento(),l.impuestoId()),name,rate);
   lines.add(new NotaDebitoVentaDocumento.Linea(result.description(),result.quantity(),result.unitPrice(),result.grossSubtotal(),result.discount(),l.impuestoId(),name,rate,result.taxAmount(),result.total()));
  }
  BigDecimal gross=sum(lines,NotaDebitoVentaDocumento.Linea::bruto),discount=sum(lines,NotaDebitoVentaDocumento.Linea::descuento),tax=sum(lines,NotaDebitoVentaDocumento.Linea::impuesto),total=sum(lines,NotaDebitoVentaDocumento.Linea::total);numero(total,true);
  List<NotaDebitoVentaDocumento.Pago> payments=emitir&&i.condicion()==TipoCondicionPago.CASH?cobros.validar(i,f,total):List.of();
  BigDecimal paid=emitir&&i.condicion()==TipoCondicionPago.CASH?total:BigDecimal.ZERO;
  BigDecimal received=payments.stream().map(x->x.recibido()==null?x.monto():x.recibido()).reduce(BigDecimal.ZERO,BigDecimal::add),change=payments.stream().map(NotaDebitoVentaDocumento.Pago::cambio).reduce(BigDecimal.ZERO,BigDecimal::add);
  var e=empresa();var de=datos.findByEmpresaId(p.empresaId()).orElse(null);BigDecimal anterior=notas.saldo(f.getId());
  return new NotaDebitoVentaDocumento(emitir?UUID.randomUUID():null,emitir?notas.siguiente():"VISTA PREVIA",i.fecha(),LocalDateTime.now(zona()),p.nombre(),f.getId(),f.getNumero(),f.getFecha(),"Emitida",f.getCondicionPago(),f.getSucursalId(),f.getClienteId(),nombreCliente(f),f.getClienteId()==null?null:f.getClienteIdentificacion(),f.getMonedaId(),f.getMonedaCodigo(),i.motivoId(),reason,description,List.copyOf(lines),gross,discount,tax,total,anterior,i.condicion()==TipoCondicionPago.CREDIT?anterior.add(total):anterior,de==null||limpiar(de.getNombreComercial()).isBlank()?e.getNombre():de.getNombreComercial(),e.getNombre(),e.getIdentificacionFiscal(),de==null?null:de.getDireccion(),de==null?null:de.getTelefono(),de==null?null:de.getCorreo(),emitir&&storage!=null?CompanyReportAssets.logo(e,storage):null,i.condicion(),paid,total.subtract(paid),received,change,payments);
 }
 private FacturaVenta factura(UUID id,boolean lock){
  var p=TenantContext.principalActual();var f=(lock?facturas.bloquear(id,p.tenantId(),p.empresaId()):facturas.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())).filter(x->EmpresaContext.permiteSucursal(x.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("Factura no encontrada."));
  if(f.getStatus()!=EstadoFacturaVenta.CONFIRMED)throw new ReglaNegocioException("La factura seleccionada no es elegible para una nota de débito.");
  if(f.getClienteId()!=null)notas.cliente(f.getClienteId(),false);notas.validarMoneda(f.getMonedaId());return f;
 }
 private static void validarCondicion(EmitirNotaDebitoVentaInput i,FacturaVenta f){
  if(i.condicion()==null)throw new ReglaNegocioException("Seleccione la condición de la nota.");
  if(i.condicion()==TipoCondicionPago.CREDIT){
   if(f.getClienteId()==null)throw new ReglaNegocioException("Para emitir esta nota de débito a crédito debe existir un cliente identificado en la factura.");
   if(i.pagos()!=null&&!i.pagos().isEmpty()||i.cajaId()!=null||i.sesionCajaId()!=null)throw new ReglaNegocioException("Una nota a crédito no admite cobros inmediatos.");
  }
 }
 private static String nombreCliente(FacturaVenta f){return f.getClienteId()==null?"Consumidor Final":NotaCreditoVentaService.nombreCliente(f.getClienteNombre());}
 private Empresa empresa(){var p=TenantContext.principalActual();return empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));}
 private ZoneId zona(){return ZoneId.of(empresa().getZonaHoraria());}
 private void periodo(LocalDate date){var p=TenantContext.principalActual();periodos.findFirstByTenantIdAndEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(p.tenantId(),p.empresaId(),date,date).filter(x->x.getEstado()!=PeriodoFiscal.Estado.ABIERTO).ifPresent(x->{throw new ReglaNegocioException("El período fiscal está cerrado o bloqueado.");});}
 private void auditar(String action,NotaDebitoVentaDocumento d,String status,String reason){
  var p=TenantContext.principalActual();var audit=new LinkedHashMap<String,Object>();audit.put("DebitNoteId",d.id());audit.put("DebitNoteNumber",d.numero());audit.put("InvoiceId",d.facturaId());audit.put("CustomerId",d.clienteId());audit.put("TenantId",p.tenantId());audit.put("CompanyId",p.empresaId());audit.put("UserId",p.usuarioId());audit.put("Currency",d.moneda());audit.put("Total",d.total());audit.put("PaymentCondition",d.condicionNota());audit.put("Paid",d.pagado());audit.put("NoteBalance",d.saldoNota());audit.put("PreviousStatus",status.equals("VOIDED")?"CONFIRMED":null);audit.put("NewStatus",status);audit.put("Reason",reason);audit.put("TraceId",com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());
  auditoria.registrar(action,"NotaDebitoVenta",d.id(),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(audit));
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){org.slf4j.LoggerFactory.getLogger(NotaDebitoVentaService.class).atInfo().addKeyValue("DebitNoteId",d.id()).addKeyValue("InvoiceId",d.facturaId()).addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId()).addKeyValue("UserId",p.usuarioId()).addKeyValue("NewStatus",status).addKeyValue("TraceId",audit.get("TraceId")).log(action);}});
 }
 private static BigDecimal sum(List<NotaDebitoVentaDocumento.Linea> ls,java.util.function.Function<NotaDebitoVentaDocumento.Linea,BigDecimal> f){return ls.stream().map(f).reduce(BigDecimal.ZERO,BigDecimal::add);}
 private static UUID requerir(EmitirNotaDebitoVentaInput i){if(i==null||i.facturaId()==null)throw new ReglaNegocioException("Seleccione la factura de origen.");return i.facturaId();}
 private static void numero(BigDecimal n,boolean positive){if(n==null||n.signum()<0||positive&&n.signum()==0||n.scale()>4||n.precision()-n.scale()>15)throw new ReglaNegocioException("Cantidad o importe fuera de rango.");}
 private static void pagina(int page,int size){if(page<0||!Set.of(5,10,20,25,50).contains(size))throw new ReglaNegocioException("Paginación inválida.");}
 private static String limpiar(String x){return x==null?"":x.trim();}
 private static String texto(String x,int min,int max){String v=limpiar(x);if(v.length()<min||v.length()>max)throw new ReglaNegocioException("Complete el texto requerido (hasta "+max+" caracteres).");return v;}
}
