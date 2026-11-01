package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
/** Exactly one output: normal PDF without a job, or a physical print job. */
public record SolicitudImpresionResultadoDto(UUID trabajoId,boolean mostrarPdfAlImprimir) {
    public SolicitudImpresionResultadoDto {
        if(mostrarPdfAlImprimir != (trabajoId == null))
            throw new IllegalArgumentException("La salida debe ser PDF o trabajo de impresión.");
    }
}
