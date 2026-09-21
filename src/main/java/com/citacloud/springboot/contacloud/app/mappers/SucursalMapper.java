package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.SucursalDto;
import com.citacloud.springboot.contacloud.app.models.Sucursal;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class SucursalMapper {
    public SucursalDto toDto(Sucursal s) {
        return new SucursalDto(s.getId(), s.getNombre(), s.getDireccion(), s.getTelefono(), s.isPrincipal(), s.isActivo());
    }
    public Sucursal toEntity(UUID empresaId, String codigo, SucursalDto dto) {
        Sucursal s = new Sucursal(empresaId, codigo, dto.nombre().trim()); update(s, dto); return s;
    }
    public void update(Sucursal s, SucursalDto dto) {
        s.setNombre(dto.nombre().trim()); s.setDireccion(trimToNull(dto.direccion()));
        s.setTelefono(trimToNull(dto.telefono()));
    }
    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null; return value.trim();
    }
}
