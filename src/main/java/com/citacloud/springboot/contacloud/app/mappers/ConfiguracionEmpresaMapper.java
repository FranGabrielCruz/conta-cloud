package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionEmpresaDto;
import com.citacloud.springboot.contacloud.app.models.DatosEmpresa;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.models.Moneda;
import org.springframework.stereotype.Component;

@Component
public class ConfiguracionEmpresaMapper {
    public ConfiguracionEmpresaDto toDto(Empresa empresa, DatosEmpresa datos, Moneda monedaBase) {
        return new ConfiguracionEmpresaDto(datos.getNombreComercial(), datos.getRazonSocial(),
            empresa.getIdentificacionFiscal(), datos.getTelefono(), datos.getCorreo(),
            monedaBase == null ? null : monedaBase.getId(), datos.getDireccion());
    }
    public void update(Empresa empresa, DatosEmpresa datos, ConfiguracionEmpresaDto dto,
                       String rnc, String telefono, String correo) {
        empresa.setNombre(dto.nombreComercial().trim());
        empresa.setIdentificacionFiscal(rnc);
        datos.setNombreComercial(dto.nombreComercial().trim());
        datos.setRazonSocial(trimToNull(dto.razonSocial()));
        datos.setTelefono(telefono);
        datos.setCorreo(correo);
        datos.setDireccion(trimToNull(dto.direccion()));
    }
    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
