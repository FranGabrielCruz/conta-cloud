package com.citacloud.springboot.contacloud.app.mappers;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class AgenteImpresionMapper {
    public AgenteImpresionDto toDto(AgenteImpresion a,String sucursal,List<CajaAgenteImpresionDto> cajas,Duration tolerancia,OffsetDateTime ahora){
        boolean conectado=a.getEstado()==EstadoAgenteImpresion.ACTIVE&&a.getUltimaConexion()!=null&&a.getUltimaConexion().isAfter(ahora.minus(tolerancia));
        return new AgenteImpresionDto(a.getId(),a.getCodigoVisible(),a.getNombre(),a.getSucursalId(),sucursal,a.getEstado(),a.getUltimaConexion(),conectado,a.getVersionAplicacion(),a.getNombreMaquina(),a.getSistemaOperativo(),a.getVersion(),cajas,a.isMostrarPdfAlImprimir());
    }
    public AgenteImpresion toEntity(CrearAgenteImpresionInput input,UUID tenant,UUID empresa,String codigo,UUID usuario,Set<Caja> cajas){
        var a=new AgenteImpresion(tenant,empresa,input.sucursalId(),codigo,input.nombre().trim(),usuario);
        a.configurar(input.nombre().trim(),input.sucursalId(),input.mostrarPdfAlImprimir());a.reemplazarCajas(cajas);return a;
    }
}
