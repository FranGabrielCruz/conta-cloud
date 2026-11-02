package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
import com.citacloud.springboot.contacloud.app.models.TipoDocumentoImpresion;
public record TrabajoImpresionDto(UUID id,UUID claimId,byte[] pdf,TipoDocumentoImpresion documentType){
    public TrabajoImpresionDto(UUID id,UUID claimId,byte[] pdf){this(id,claimId,pdf,TipoDocumentoImpresion.SALES_INVOICE);}
    public TrabajoImpresionDto{if(documentType==null)documentType=TipoDocumentoImpresion.SALES_INVOICE;}
}
