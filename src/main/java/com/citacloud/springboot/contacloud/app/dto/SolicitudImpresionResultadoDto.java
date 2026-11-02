package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
/** Exactly one output: normal PDF without a job, or a physical print job. */
public record SolicitudImpresionResultadoDto(UUID trabajoId,@com.fasterxml.jackson.annotation.JsonAlias({"showPdfOnPrint","mostrarPdfAlImprimir"}) boolean printOnScreen) {
    public com.citacloud.springboot.contacloud.app.models.BillingPrintOutputMode outputMode(){
        return printOnScreen?com.citacloud.springboot.contacloud.app.models.BillingPrintOutputMode.SCREEN:
            com.citacloud.springboot.contacloud.app.models.BillingPrintOutputMode.PRINT_AGENT;
    }
    public SolicitudImpresionResultadoDto {
        if(printOnScreen != (trabajoId == null))
            throw new IllegalArgumentException("La salida debe ser PDF o trabajo de impresión.");
    }
}
