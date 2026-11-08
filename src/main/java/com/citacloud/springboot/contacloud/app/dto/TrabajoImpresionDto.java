package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
import com.citacloud.springboot.contacloud.app.models.TipoDocumentoImpresion;
public record TrabajoImpresionDto(UUID id,UUID claimId,byte[] pdf,TipoDocumentoImpresion documentType,Integer snapshotVersion,String snapshot,String traceId,Integer queueVersion,Integer attempt,Integer leaseSeconds){
    public TrabajoImpresionDto(UUID id,UUID claimId,byte[] pdf,TipoDocumentoImpresion type,Integer version,String snapshot,String trace){this(id,claimId,pdf,type,version,snapshot,trace,null,null,null);}
    public TrabajoImpresionDto(UUID id,UUID claimId,byte[] pdf,TipoDocumentoImpresion type){this(id,claimId,pdf,type,null,null,null);}
    public TrabajoImpresionDto(UUID id,UUID claimId,byte[] pdf){this(id,claimId,pdf,TipoDocumentoImpresion.SALES_INVOICE);}
    @Override public String toString(){return "TrabajoImpresionDto[id="+id+", documentType="+documentType+"]";}
    public TrabajoImpresionDto{if(documentType==null)documentType=TipoDocumentoImpresion.SALES_INVOICE;}
}
