package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.ImpuestoFiscalDto;
import com.citacloud.springboot.contacloud.app.models.Impuesto;
import org.springframework.stereotype.Component;

@Component
public class ImpuestoMapper {
    public ImpuestoFiscalDto toDto(Impuesto entity){return new ImpuestoFiscalDto(entity.getId(),entity.getNombre(),entity.getPorcentaje(),entity.getTipo().name(),entity.getDescripcion(),entity.isActivo(),entity.getVersion());}
}
