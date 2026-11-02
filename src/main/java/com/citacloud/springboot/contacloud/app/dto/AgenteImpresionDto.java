package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoAgenteImpresion;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

public record AgenteImpresionDto(UUID id,String codigo,String nombre,UUID sucursalId,String sucursal,
    EstadoAgenteImpresion estado,OffsetDateTime ultimaConexion,boolean conectado,String versionAplicacion,
    String nombreMaquina,String sistemaOperativo,long version,List<CajaAgenteImpresionDto> cajas,@com.fasterxml.jackson.annotation.JsonAlias({"showPdfOnPrint","mostrarPdfAlImprimir"}) boolean printOnScreen){
    public AgenteImpresionDto{cajas=List.copyOf(cajas);}
}
