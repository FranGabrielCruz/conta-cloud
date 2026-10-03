package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.UnidadMedidaDto;
import com.citacloud.springboot.contacloud.app.models.UnidadMedida;
import org.springframework.stereotype.Component;

@Component
public class UnidadMedidaMapper {
    public UnidadMedidaDto toDto(UnidadMedida entity){return new UnidadMedidaDto(entity.getId(),entity.getNombre(),entity.getAbreviatura(),entity.getDescripcion(),entity.isActivo(),entity.getVersion());}
}
