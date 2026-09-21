package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.MonedaDto;
import com.citacloud.springboot.contacloud.app.models.Moneda;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class MonedaMapper {
    public MonedaDto toDto(Moneda m) {
        return new MonedaDto(m.getId(), m.getCodigoIso(), m.getNombre(), m.getSimbolo(), m.getDecimales(),
            m.isMonedaBase(), m.isActivo());
    }
    public Moneda toEntity(UUID empresaId, MonedaDto dto) {
        return new Moneda(empresaId, dto.codigoIso().trim().toUpperCase(), dto.nombre().trim(),
            dto.simbolo().trim(), dto.decimales());
    }
    public void update(Moneda m, MonedaDto dto) {
        m.setNombre(dto.nombre().trim()); m.setSimbolo(dto.simbolo().trim()); m.setDecimales(dto.decimales());
    }
}
