package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.SolicitudImpresionResultadoDto;
import com.citacloud.springboot.contacloud.app.models.TipoDocumentoImpresion;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;

/** Output-only post-commit adapter for the shared billing router. */
@Service
public class CashCountPrintService {
    private final CashCountDocumentService documentos;private final BillingPrintRouter router;private final RecuentoCajaPdfService pdfNormal;
    public CashCountPrintService(CashCountDocumentService documentos,BillingPrintRouter router,RecuentoCajaPdfService pdfNormal){this.documentos=documentos;this.router=router;this.pdfNormal=pdfNormal;}
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('recuentos_caja.imprimir')")
    public com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto pantalla(UUID id){var r=documentos.obtener(id);return r.getDocumentoPos()==null?pdfNormal.generar(id):documentos.generar(r);}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    @PreAuthorize("hasAuthority('recuentos_caja.imprimir')")
    public SolicitudImpresionResultadoDto solicitar(UUID id,UUID intento){
        var r=documentos.obtener(id);
        return router.solicitar(new BillingPrintRouter.Documento(TipoDocumentoImpresion.CASH_COUNT,id,r.getSucursalId(),r.getCajaId(),()->documentos.generar(r),()->documentos.documento(r)),intento);
    }
}
