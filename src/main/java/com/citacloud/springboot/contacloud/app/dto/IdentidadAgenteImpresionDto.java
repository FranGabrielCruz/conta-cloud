package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoAgenteImpresion;
import java.util.UUID;

public record IdentidadAgenteImpresionDto(UUID agentId,String displayCode,String name,String company,String branch,EstadoAgenteImpresion status){}
