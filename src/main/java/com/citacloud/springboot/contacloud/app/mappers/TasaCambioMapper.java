package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.TasaCambioDto;
import com.citacloud.springboot.contacloud.app.dto.TasaCambioInput;
import com.citacloud.springboot.contacloud.app.models.TasaCambio;
import com.citacloud.springboot.contacloud.app.models.Moneda;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class TasaCambioMapper {
    public TasaCambioDto toDto(TasaCambio entity) {
        return new TasaCambioDto(entity.getId(), entity.getMonedaOrigenId(),
            entity.getMonedaOrigen().getCodigoIso(), entity.getMonedaOrigen().getNombre(),
            entity.getMonedaDestinoId(), entity.getMonedaDestino().getCodigoIso(),
            entity.getMonedaDestino().getNombre(), entity.getTasa(), entity.getFecha(), entity.isActivo());
    }

    public TasaCambioDto toDto(TasaCambio entity, Moneda origen, Moneda destino) {
        return new TasaCambioDto(entity.getId(), origen.getId(), origen.getCodigoIso(), origen.getNombre(),
            destino.getId(), destino.getCodigoIso(), destino.getNombre(), entity.getTasa(),
            entity.getFecha(), entity.isActivo());
    }

    public TasaCambio toEntity(TasaCambioInput input, UUID tenantId, UUID empresaId, UUID usuarioId) {
        return new TasaCambio(tenantId, empresaId, input.monedaOrigenId(), input.monedaDestinoId(),
            input.tasa(), input.fecha(), input.activo(), usuarioId);
    }
}
