package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.SecuenciaFiscalDto;
import com.citacloud.springboot.contacloud.app.dto.SecuenciaFiscalInput;
import com.citacloud.springboot.contacloud.app.models.SecuenciaFiscal;
import com.citacloud.springboot.contacloud.app.models.TipoComprobanteFiscal;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecuenciaFiscalMapper {
    public SecuenciaFiscal toEntity(SecuenciaFiscalInput input,UUID tenantId,UUID empresaId,String codigo){
        return new SecuenciaFiscal(tenantId,empresaId,input.comprobanteId(),codigo,input.numeroInicial(),input.numeroFinal());
    }
    public SecuenciaFiscalDto toDto(SecuenciaFiscal entity,TipoComprobanteFiscal comprobante){
        String estado=entity.agotada()?"Agotada":entity.isActivo()?"Activa":"Inactiva";
        String nombre=comprobante==null?"No disponible":comprobante.getCodigo()+" – "+comprobante.getNombre();
        return new SecuenciaFiscalDto(entity.getId(),entity.getComprobanteId(),nombre,entity.getNumeroInicial(),
            entity.getNumeroActual(),entity.siguiente(),entity.getNumeroFinal(),estado);
    }
}
