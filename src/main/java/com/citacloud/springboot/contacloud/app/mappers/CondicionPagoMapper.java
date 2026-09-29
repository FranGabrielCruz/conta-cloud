package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.CondicionPagoDto;
import com.citacloud.springboot.contacloud.app.dto.CondicionPagoInput;
import com.citacloud.springboot.contacloud.app.models.CondicionPago;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class CondicionPagoMapper {
    public CondicionPagoDto toDto(CondicionPago entity) {
        return new CondicionPagoDto(entity.getId(), entity.getNombre(), entity.getTipo(), entity.getDias(),
            entity.getDescripcion(), entity.isActivo());
    }

    public CondicionPago toEntity(CondicionPagoInput input, UUID tenantId, UUID empresaId,
                                  String codigo, UUID usuarioId) {
        return new CondicionPago(tenantId, empresaId, codigo, input.nombre(), input.tipo(), input.dias(),
            input.descripcion(), input.activo(), usuarioId);
    }
}
