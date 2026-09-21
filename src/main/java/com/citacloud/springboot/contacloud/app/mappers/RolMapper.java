package com.citacloud.springboot.contacloud.app.mappers;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;
@Component public class RolMapper {
    public RolDto toSummaryDto(Rol rol,long usuarios){ return new RolDto(rol.getId(),rol.getNombre(),rol.getDescripcion(),usuarios,rol.isActivo(),rol.isProtegido(),java.util.Set.of()); }
    public RolDto toDto(Rol rol,long usuarios){ return new RolDto(rol.getId(),rol.getNombre(),rol.getDescripcion(),usuarios,rol.isActivo(),rol.isProtegido(),
        rol.getPermisos().stream().map(Permiso::getId).collect(Collectors.toUnmodifiableSet())); }
    public PermisoDto toDto(Permiso p){ return new PermisoDto(p.getId(),p.getCodigo(),p.getNombre(),p.getDescripcion(),p.getModulo(),p.getRecurso(),p.getAccion()); }
}
