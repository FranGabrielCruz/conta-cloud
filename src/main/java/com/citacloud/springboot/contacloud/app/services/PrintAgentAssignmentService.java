package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.Caja;
import com.citacloud.springboot.contacloud.app.repositories.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Locks registers in UUID order after the agent lock; PostgreSQL also enforces active-destination uniqueness. */
@Service
public class PrintAgentAssignmentService {
    private final CashRegisterRepository cajas;
    private final AgenteImpresionRepository agentes;
    public PrintAgentAssignmentService(CashRegisterRepository cajas,AgenteImpresionRepository agentes){this.cajas=cajas;this.agentes=agentes;}

    @Transactional
    public Set<Caja> validar(UUID tenant,UUID empresa,UUID sucursal,UUID agente,List<UUID> ids,Collection<UUID> anteriores,boolean verificarDestino){
        if(ids==null||ids.isEmpty())throw new ReglaNegocioException("Seleccione al menos una caja.");
        if(ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size())throw new ReglaNegocioException("No se permite seleccionar una caja más de una vez.");
        Set<UUID> bloqueos=new TreeSet<>();bloqueos.addAll(anteriores);bloqueos.addAll(ids);
        Map<UUID,Caja> registradas=new HashMap<>();
        for(UUID id:bloqueos){cajas.bloquearParaOperacion(id,tenant,empresa).ifPresent(c->registradas.put(id,c));}
        Set<Caja> seleccion=new LinkedHashSet<>();
        for(UUID id:ids){Caja caja=registradas.get(id);
            if(caja==null||!tenant.equals(caja.getTenantId())||!empresa.equals(caja.getEmpresaId())||!caja.isActivo()||!caja.getSucursalId().equals(sucursal))throw new ReglaNegocioException("Selecciona cajas activas de la sucursal y empresa del agente.");
            if(verificarDestino&&agentes.otrosDestinosActivos(tenant,empresa,id,agente)>0)throw new ReglaNegocioException("La caja "+caja.getNombre()+" ya está asignada a otro agente de impresión.");
            seleccion.add(caja);
        }
        return seleccion;
    }
}
