package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionContableDto;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionContableInput;
import com.citacloud.springboot.contacloud.app.models.ConfiguracionContable;
import org.springframework.stereotype.Component;

@Component
public class ConfiguracionContableMapper {
    public ConfiguracionContableDto toDto(ConfiguracionContable entity) {
        return new ConfiguracionContableDto(entity.getId(), entity.getMetodoContable().name(),
            entity.isContabilizacionAutomatica(), entity.isPermitirPeriodosCerrados(), false);
    }

    public void updateEntity(ConfiguracionContableInput input, ConfiguracionContable entity) {
        entity.setMetodoContable(ConfiguracionContable.MetodoContable.valueOf(input.metodoContable()));
        entity.setContabilizacionAutomatica(input.contabilizacionAutomatica());
        entity.setPermitirPeriodosCerrados(input.permitirPeriodosCerrados());
    }
}
