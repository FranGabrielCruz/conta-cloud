package com.citacloud.springboot.contacloud.app.mappers;
import com.citacloud.springboot.contacloud.app.dto.UsuarioDto;
import com.citacloud.springboot.contacloud.app.models.UsuarioEmpresa;
import org.springframework.stereotype.Component;
import java.util.*;
@Component public class UsuarioMapper {
    public UsuarioDto toDto(UsuarioEmpresa acceso,Set<UUID> sucursales,List<String> nombres){
        var u=acceso.getUsuario(); String resumen;
        if(acceso.isAccesoTodasSucursales()) resumen="Todas las sucursales";
        else if(nombres.isEmpty()) resumen="Sin sucursales";
        else if(nombres.size()<=2) resumen=String.join(", ",nombres);
        else resumen=nombres.size()+" sucursales";
        return new UsuarioDto(acceso.getId(),u.getId(),u.getUsuario(),u.getNombre(),u.getApellido(),u.getCorreo(),u.getTelefono(),
            acceso.getRol().getId(),acceso.getRol().getNombre(),acceso.isAccesoTodasSucursales(),Set.copyOf(sucursales),resumen,acceso.isActivo());
    }
}
