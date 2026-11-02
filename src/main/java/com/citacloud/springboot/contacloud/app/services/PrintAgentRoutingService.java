package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
/** Resolves only the originating register. Ambiguous destinations fail closed. */
@Service
public class PrintAgentRoutingService {
 private final CashRegisterRepository cajas; private final AgenteImpresionRepository agentes;
 public PrintAgentRoutingService(CashRegisterRepository cajas,AgenteImpresionRepository agentes){this.cajas=cajas;this.agentes=agentes;}
 @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('facturas.pdf','devoluciones_venta.pdf','recibos.imprimir','recuentos_caja.imprimir','vales_caja.imprimir','cierres_caja.imprimir')")
 public UUID resolver(UUID cajaId){var p=TenantContext.principalActual();var caja=cajas.findByIdAndTenantIdAndEmpresaId(cajaId,p.tenantId(),p.empresaId()).filter(Caja::isActivo).filter(c->EmpresaContext.permiteSucursal(c.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("Caja no encontrada."));
 var destinos=agentes.destinos(p.tenantId(),p.empresaId(),caja.getSucursalId(),cajaId,EstadoAgenteImpresion.ACTIVE);
 if(destinos.isEmpty()){
     // An explicitly retained revoked association may still provide PDF-only configuration.
     // Never pick between historical agents, and never route physical jobs to a revoked agent.
     var revocados=agentes.destinos(p.tenantId(),p.empresaId(),caja.getSucursalId(),cajaId,EstadoAgenteImpresion.REVOKED);
     if(revocados.size()==1&&revocados.getFirst().isPrintOnScreen())return revocados.getFirst().getId();
 }
 if(destinos.size()!=1)throw new ReglaNegocioException(destinos.isEmpty()?"No hay un agente de impresión válido configurado para esta caja.":"Configura un único destino de impresión para esta caja.");return destinos.getFirst().getId();
 }
}
