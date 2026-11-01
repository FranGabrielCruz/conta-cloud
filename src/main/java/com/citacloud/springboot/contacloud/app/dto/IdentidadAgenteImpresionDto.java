package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoAgenteImpresion;
import java.util.UUID;
import java.util.List;

public record IdentidadAgenteImpresionDto(UUID agentId,String displayCode,String name,String company,String branch,EstadoAgenteImpresion status,UUID cashRegisterId,String cashRegister,List<CashRegister> cashRegisters){
    public IdentidadAgenteImpresionDto{cashRegisters=List.copyOf(cashRegisters);}
    public record CashRegister(UUID id,String name){}
}
