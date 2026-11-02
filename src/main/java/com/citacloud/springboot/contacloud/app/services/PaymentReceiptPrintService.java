package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.PaymentReceiptSnapshotRepository;
import com.citacloud.springboot.contacloud.app.reports.pos.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

/** Output-only adapter. Confirmation has already committed before this service is called. */
@Service
public class PaymentReceiptPrintService {
    private final PaymentReceiptSnapshotRepository snapshots;private final BillingPrintRouter router;
    public PaymentReceiptPrintService(PaymentReceiptSnapshotRepository snapshots,BillingPrintRouter router){this.snapshots=snapshots;this.router=router;}
    private PaymentReceiptSnapshotRepository.Stored obtener(UUID id){
        var p=TenantContext.principalActual();var s=snapshots.obtener(id,p.tenantId(),EmpresaContext.requerirEmpresaId());
        if(s==null||!EmpresaContext.permiteSucursal(s.branchId()))throw new RecursoNoEncontradoException("Recibo no encontrado.");
        if("VOIDED".equals(s.status()))throw new ReglaNegocioException("El recibo está anulado.");return s;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.imprimir')")
    public SolicitudImpresionResultadoDto solicitar(UUID id,UUID intento){
        var s=obtener(id);
        if(s.cashRegisterId()==null)throw new ReglaNegocioException("El recibo no conserva una caja de origen para resolver su agente.");
        return router.solicitar(new BillingPrintRouter.Documento(TipoDocumentoImpresion.PAYMENT_RECEIPT,id,s.branchId(),s.cashRegisterId(),()->pdf(id,s)),intento);
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.imprimir')")
    public ArchivoPdfDto generar(UUID id){return pdf(id,obtener(id));}
    private ArchivoPdfDto pdf(UUID id,PaymentReceiptSnapshotRepository.Stored s){
        try{var d=snapshots.documento(s);return new ArchivoPdfDto("Recibo_"+InvoiceFormats.fileName(d.receiptNumber()),new PaymentReceipt80mmPdfRenderer().render(d));}
        catch(InvoicePrintFailure|IllegalArgumentException ex){
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=PaymentReceiptRenderFailed receiptId={} traceId={}",id,com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());
            throw new ReglaNegocioException("El cobro está confirmado, pero no fue posible generar su ticket.",ex);
        }
    }
}
