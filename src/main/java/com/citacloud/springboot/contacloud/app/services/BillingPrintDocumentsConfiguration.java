package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.context.annotation.*;
import static com.citacloud.springboot.contacloud.app.services.BillingPrintDocumentRegistry.*;
@Configuration
public class BillingPrintDocumentsConfiguration {
 @Bean Registration invoicePrintDocument(FacturaVentaRepository invoices,FacturaVentaPdfService pdf){
  return new Registration(TipoDocumentoImpresion.SALES_INVOICE,"facturas.pdf",id->{var p=TenantContext.principalActual();var f=invoices.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Factura no encontrada."));return new Document(f.getSucursalId(),f.getCajaId(),()->pdf.generar(id),()->pdf.generarParaImpresion(id));});
 }
 @Bean Registration salesReturnPrintDocument(DevolucionPosDocumentoService documents,DevolucionVentaPdfService pdf){
  return new Registration(TipoDocumentoImpresion.SALES_RETURN,"devoluciones_venta.pdf",id->{var d=documents.obtener(id);var origin=documents.origen(d);return new Document(origin.sucursalId(),origin.cajaId(),()->pdf.generar(id),()->documents.generar(id));});
 }
 @Bean Registration paymentReceiptPrintDocument(PaymentReceiptSnapshotRepository snapshots,PaymentReceiptPrintService pdf){
  return new Registration(TipoDocumentoImpresion.PAYMENT_RECEIPT,"recibos.imprimir",id->{var p=TenantContext.principalActual();var d=snapshots.obtener(id,p.tenantId(),p.empresaId());if(d==null)throw new RecursoNoEncontradoException("Recibo no encontrado.");if("VOIDED".equals(d.status()))throw new ReglaNegocioException("El recibo está anulado.");return new Document(d.branchId(),d.cashRegisterId(),()->pdf.generar(id),()->pdf.generar(id));});
 }
 @Bean Registration cashCountPrintDocument(CashCountDocumentService documents,CashCountPrintService pdf){
  return new Registration(TipoDocumentoImpresion.CASH_COUNT,"recuentos_caja.imprimir",id->{var d=documents.obtener(id);return new Document(d.getSucursalId(),d.getCajaId(),()->pdf.pantalla(id),()->documents.generar(d));});
 }
 @Bean Registration cashVoucherPrintDocument(CashOperationPrintDocumentService documents){return new Registration(TipoDocumentoImpresion.CASH_VOUCHER,"vales_caja.imprimir",documents::vale);}
 @Bean Registration cashClosingPrintDocument(CashOperationPrintDocumentService documents){return new Registration(TipoDocumentoImpresion.CASH_CLOSING,"cierres_caja.imprimir",documents::cierre);}
}
