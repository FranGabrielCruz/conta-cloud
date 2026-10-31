package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoAgenteImpresion;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgenteImpresionDto(UUID id,String codigo,String nombre,UUID sucursalId,String sucursal,
    EstadoAgenteImpresion estado,OffsetDateTime ultimaConexion,boolean conectado,String versionAplicacion,
    String nombreMaquina,String sistemaOperativo,long version){}
