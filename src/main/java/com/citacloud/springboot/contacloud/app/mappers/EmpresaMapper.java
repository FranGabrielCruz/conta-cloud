package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.EmpresaDto;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import org.springframework.stereotype.Component;

@Component
public class EmpresaMapper {
    public EmpresaDto toDto(Empresa e) {
        return new EmpresaDto(e.getId(), e.getCodigo(), e.getNombre(), e.getIdentificacionFiscal(),
            e.getPaisCodigo(), e.getZonaHoraria(), e.isActivo());
    }
    public Empresa toEntity(EmpresaDto dto) {
        Empresa e = new Empresa(dto.codigo(), dto.nombre());
        actualizar(e, dto);
        return e;
    }
    public void actualizar(Empresa e, EmpresaDto dto) {
        e.setCodigo(dto.codigo().trim().toUpperCase()); e.setNombre(dto.nombre().trim());
        e.setIdentificacionFiscal(dto.identificacionFiscal()); e.setPaisCodigo(dto.paisCodigo());
        e.setZonaHoraria(dto.zonaHoraria()); e.setActivo(dto.activo());
    }
}
