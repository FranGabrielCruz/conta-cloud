package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.ComprobanteFiscalDto;
import com.citacloud.springboot.contacloud.app.dto.ComprobanteFiscalInput;
import com.citacloud.springboot.contacloud.app.models.TipoComprobanteFiscal;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ComprobanteFiscalMapper {
    public ComprobanteFiscalDto toDto(TipoComprobanteFiscal entity){
        return new ComprobanteFiscalDto(entity.getId(),entity.getCodigo(),entity.getNombre(),entity.getPrefijo(),
            entity.getDescripcion(),entity.isActivo());
    }
    public TipoComprobanteFiscal toEntity(ComprobanteFiscalInput input,UUID tenantId,UUID empresaId){
        return new TipoComprobanteFiscal(tenantId,empresaId,input.codigo(),input.nombre(),input.prefijo(),input.descripcion());
    }
    public void update(TipoComprobanteFiscal entity,ComprobanteFiscalInput input){
        entity.setCodigo(input.codigo());entity.setNombre(input.nombre());entity.setPrefijo(input.prefijo());entity.setDescripcion(input.descripcion());
    }
}
