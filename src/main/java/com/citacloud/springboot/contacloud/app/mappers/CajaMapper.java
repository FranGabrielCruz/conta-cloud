package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.CajaDto;
import com.citacloud.springboot.contacloud.app.dto.CajaInput;
import com.citacloud.springboot.contacloud.app.models.Caja;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class CajaMapper {
    public CajaDto toDto(Caja entity) {
        return new CajaDto(entity.getId(), entity.getNombre(), entity.getSucursalId(),
            entity.getSucursal().getNombre(), entity.getMonedaId(), entity.getMoneda().getCodigoIso(),
            entity.getMoneda().getNombre(), entity.getDescripcion(), entity.isActivo());
    }

    public Caja toEntity(CajaInput input, UUID tenantId, UUID empresaId, String codigo, UUID usuarioId) {
        return new Caja(tenantId, empresaId, input.sucursalId(), input.monedaId(), codigo,
            input.nombre(), input.descripcion(), input.activa(), usuarioId);
    }
}

