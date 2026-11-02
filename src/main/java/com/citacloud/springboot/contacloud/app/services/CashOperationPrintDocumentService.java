package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.reports.pos.*;
import com.citacloud.springboot.contacloud.app.reports.pos.CashOperationPrintDocument.Line;
import com.citacloud.springboot.contacloud.app.security.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
/** Read-only registration adapters for voucher and closing; never invoke confirmation commands. */
@Service
public class CashOperationPrintDocumentService {
 private final ValeCajaRepository vouchers;private final CierreCajaRepository closings;
 private final ValeCajaService voucherDetails;private final CierreCajaService closingDetails;
 private final ValeCajaPdfService voucherPdf;private final CierreCajaPdfService closingPdf;
 private final CashOperation80mmPdfRenderer renderer=new CashOperation80mmPdfRenderer();
 private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
 public CashOperationPrintDocumentService(ValeCajaRepository vouchers,CierreCajaRepository closings,ValeCajaService voucherDetails,CierreCajaService closingDetails,ValeCajaPdfService voucherPdf,CierreCajaPdfService closingPdf){this.vouchers=vouchers;this.closings=closings;this.voucherDetails=voucherDetails;this.closingDetails=closingDetails;this.voucherPdf=voucherPdf;this.closingPdf=closingPdf;}
 BillingPrintDocumentRegistry.Document vale(UUID id){
  var p=TenantContext.principalActual();var v=vouchers.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).filter(x->EmpresaContext.permiteSucursal(x.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("Vale de caja no encontrado."));
  return new BillingPrintDocumentRegistry.Document(v.getSucursalId(),v.getCajaId(),()->voucherPdf.generar(id),()->voucherTicket(voucherDetails.obtener(id)));
 }
 BillingPrintDocumentRegistry.Document cierre(UUID id){
  var p=TenantContext.principalActual();var c=closings.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).filter(x->EmpresaContext.permiteSucursal(x.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("Cierre de caja no encontrado."));
  return new BillingPrintDocumentRegistry.Document(c.getSucursalId(),c.getCajaId(),()->closingPdf.generar(id),()->closingTicket(c,closingDetails.obtener(id)));
 }
 ArchivoPdfDto voucherTicket(ValeCajaDto v){
  List<Line> rows=new ArrayList<>();rows.add(new Line("Estado",v.estado()==EstadoValeCaja.VOIDED?"ANULADO":"CONFIRMADO"));rows.add(new Line("Beneficiario",v.beneficiario()));rows.add(new Line("Concepto",v.concepto()));if(v.referencia()!=null)rows.add(new Line("Referencia",v.referencia()));
  var notes=new ArrayList<String>();if(v.observacion()!=null)notes.add(v.observacion());if(v.estado()==EstadoValeCaja.VOIDED&&v.motivoAnulacion()!=null)notes.add("Anulación: "+v.motivoAnulacion());
  var d=new CashOperationPrintDocument("VALE DE CAJA",v.empresaNombre(),v.empresaIdentificacion(),v.numero(),DATE.format(v.fechaHora()),v.sucursal(),v.caja(),v.usuario(),rows,"TOTAL "+v.monedaCodigo(),money(v.importe()),notes);
  return new ArchivoPdfDto("Vale_"+InvoiceFormats.fileName(v.numero()),renderer.render(d));
 }
 ArchivoPdfDto closingTicket(CierreCaja c,CierreCajaDto r){
  List<Line> rows=new ArrayList<>();rows.add(new Line("Turno",r.turno()));rows.add(new Line("Apertura",DATE.format(r.apertura())));rows.add(new Line("Fondo inicial",money(r.fondoInicial())));rows.add(new Line("Entradas efectivo",money(r.totalEntradasEfectivo())));rows.add(new Line("Salidas efectivo",money(r.totalSalidasEfectivo())));rows.add(new Line("Esperado",money(r.esperado())));rows.add(new Line("Contado",money(r.contado())));rows.add(new Line("Resultado",switch(r.resultado()){case BALANCED->"CUADRE CORRECTO";case SHORTAGE->"FALTANTE DE CAJA";case OVERAGE->"SOBRANTE DE CAJA";}));
  for(var detail:r.detalles())rows.add(new Line(money(detail.denominacion())+" × "+detail.cantidad(),money(detail.subtotal())));
  for(var medium:r.mediosPago())rows.add(new Line(medium.medio().getEtiqueta(),money(medium.importe())));
  var d=new CashOperationPrintDocument("CIERRE DE CAJA",c.getEmpresaNombre(),c.getEmpresaIdentificacion(),r.numero(),DATE.format(r.cierre()),r.sucursal(),r.caja(),r.cerradoPor(),rows,"DIFERENCIA "+r.monedaCodigo(),money(r.diferencia()),r.observacion()==null?List.of():List.of(r.observacion()));
  return new ArchivoPdfDto("Cierre_"+InvoiceFormats.fileName(r.numero()),renderer.render(d));
 }
 private static String money(BigDecimal amount){return amount==null?"—":InvoiceFormats.amount(amount);}
}
