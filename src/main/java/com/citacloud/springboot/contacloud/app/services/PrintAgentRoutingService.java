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
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('facturas.pdf')")
 public UUID resolver(UUID cajaId){var p=TenantContext.principalActual();var caja=cajas.findByIdAndTenantIdAndEmpresaId(cajaId,p.tenantId(),p.empresaId()).filter(Caja::isActivo).filter(c->EmpresaContext.permiteSucursal(c.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("Caja no encontrada."));
 var destinos=agentes.destinos(p.tenantId(),p.empresaId(),caja.getSucursalId(),cajaId,EstadoAgenteImpresion.ACTIVE);
 if(destinos.size()!=1)throw new ReglaNegocioException(destinos.isEmpty()?"El agente de impresión de esta caja no está disponible.":"Configura un único destino de Facturación para esta caja.");return destinos.getFirst().getId();
 }
}
