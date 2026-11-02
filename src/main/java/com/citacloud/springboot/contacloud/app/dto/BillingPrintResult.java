package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.BillingPrintOutputMode;
public record BillingPrintResult(SolicitudImpresionResultadoDto output,ArchivoPdfDto screenDocument){
 public BillingPrintResult{if(output==null||(output.outputMode()==BillingPrintOutputMode.SCREEN)!=(screenDocument!=null))throw new IllegalArgumentException("La salida debe ser exclusivamente pantalla o impresora.");}
 public BillingPrintOutputMode outputMode(){return output.outputMode();}
}
