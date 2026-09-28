package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.PeriodoFiscalDto;
import com.citacloud.springboot.contacloud.app.dto.PeriodoFiscalInput;
import com.citacloud.springboot.contacloud.app.models.PeriodoFiscal;
import org.springframework.stereotype.Component;

@Component
public class PeriodoFiscalMapper {
    public PeriodoFiscalDto toDto(PeriodoFiscal entity) {
        return new PeriodoFiscalDto(entity.getId(), entity.getNombre(), entity.getFechaInicial(),
            entity.getFechaFinal(), entity.getEstado().name(), entity.getFechaCierre(),
            entity.getUsuarioCierreId(), entity.getFechaReapertura(), entity.getUsuarioReaperturaId());
    }


    public PeriodoFiscal toEntity(PeriodoFiscalInput input, java.util.UUID tenantId, java.util.UUID empresaId) {
        return new PeriodoFiscal(tenantId, empresaId, input.nombre().trim(), input.fechaInicial(), input.fechaFinal());
    }

    public void updateEntity(PeriodoFiscalInput input, PeriodoFiscal entity) {
        entity.setNombre(input.nombre().trim());
        entity.setFechaInicial(input.fechaInicial());
        entity.setFechaFinal(input.fechaFinal());
    }
}
