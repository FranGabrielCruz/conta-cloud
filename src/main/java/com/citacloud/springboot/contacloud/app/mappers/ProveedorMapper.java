package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.Proveedor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ProveedorMapper {
    public ProveedorDto toDto(Proveedor p){return new ProveedorDto(p.getId(),p.getNombreComercial(),p.getRazonSocial(),
        p.getIdentificacionFiscal(),p.getTipo(),formatearTelefono(p.getTelefono()),p.getCorreo(),p.getCondicionPagoId(),
        p.getCondicionPago()==null?null:nombreCondicion(p.getCondicionPago()),p.getMonedaId(),
        p.getMoneda()==null?null:p.getMoneda().getCodigoIso(),p.getDireccion(),p.getContacto(),
        formatearTelefono(p.getTelefonoContacto()),p.getNotas(),p.isActivo());}
    public Proveedor toEntity(ProveedorInput i,UUID tenant,UUID empresa,String codigo,String identificacion,
            String normalizada,String telefono,String telefonoContacto,UUID usuario){
        return new Proveedor(tenant,empresa,codigo,i.nombreComercial(),i.razonSocial(),identificacion,normalizada,
            i.tipo(),telefono,i.correo(),i.condicionPagoId(),i.monedaId(),i.direccion(),i.contacto(),
            telefonoContacto,i.notas(),i.activo(),usuario);
    }
    public static String formatearTelefono(String valor){if(valor==null||valor.isBlank())return null;
        String digitos=valor.replaceAll("\\D","");if(digitos.length()==10)return "("+digitos.substring(0,3)+") "+digitos.substring(3,6)+"-"+digitos.substring(6);return valor;}
    private static String nombreCondicion(com.citacloud.springboot.contacloud.app.models.CondicionPago condicion){String nombre=condicion.getNombre().trim();
        return condicion.getTipo()==com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CREDIT
            &&java.util.Set.of("fiao","fíao","fia","fiado").contains(nombre.toLowerCase(java.util.Locale.ROOT))?"Crédito":nombre;}
}
