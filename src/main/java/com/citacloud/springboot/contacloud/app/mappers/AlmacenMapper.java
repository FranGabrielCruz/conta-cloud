package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.AlmacenDto;
import com.citacloud.springboot.contacloud.app.models.Almacen;
import org.springframework.stereotype.Component;

@Component
public class AlmacenMapper {
    public AlmacenDto toDto(Almacen e,boolean predeterminado,long productos){
        String responsable=e.getResponsable()==null?null:(e.getResponsable().getNombre()+" "+e.getResponsable().getApellido()).trim();
        return new AlmacenDto(e.getId(),e.getCodigo(),e.getNombre(),e.getSucursalId(),e.getSucursal().getNombre(),e.getTipo(),
            e.getResponsableId(),responsable,e.getDireccion(),e.getObservacion(),e.isActivo(),predeterminado,productos,e.getVersion());
    }
}
