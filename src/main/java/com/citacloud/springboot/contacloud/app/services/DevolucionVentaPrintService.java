package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.SolicitudImpresionResultadoDto;
import com.citacloud.springboot.contacloud.app.models.TipoDocumentoImpresion;
import com.citacloud.springboot.contacloud.app.repositories.AgenteImpresionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** Post-confirmation output adapter; never invokes return, inventory or refund commands. */
@Service
public class DevolucionVentaPrintService {
    private final DevolucionPosDocumentoService documentos;
    private final BillingPrintRouter router;
    public DevolucionVentaPrintService(JdbcTemplate jdbc,DevolucionPosDocumentoService documentos,PrintAgentRoutingService routing,
            AgenteImpresionRepository agentes,AuditoriaService auditoria){
        this(documentos,new BillingPrintRouter(jdbc,routing,agentes,auditoria));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public DevolucionVentaPrintService(DevolucionPosDocumentoService documentos,BillingPrintRouter router){this.documentos=documentos;this.router=router;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('devoluciones_venta.pdf')")
    public SolicitudImpresionResultadoDto solicitar(UUID devolucionId,UUID intento){
        var d=documentos.obtener(devolucionId);var origen=documentos.origen(d);
        return router.solicitar(new BillingPrintRouter.Documento(TipoDocumentoImpresion.SALES_RETURN,devolucionId,
            origen.sucursalId(),origen.cajaId(),()->documentos.generar(devolucionId)),intento);
    }
}
