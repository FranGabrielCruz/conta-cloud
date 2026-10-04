package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.CategoriaDto;
import com.citacloud.springboot.contacloud.app.models.ProductoCategoria;
import org.springframework.stereotype.Component;

@Component
public class CategoriaMapper {
    public CategoriaDto toDto(ProductoCategoria entity,long products){return new CategoriaDto(entity.getId(),entity.getNombre(),entity.getDescripcion(),entity.isActivo(),products,entity.getVersion());}
}
