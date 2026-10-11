package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ClienteMapper {
    public ClienteDto toDto(Cliente c,DireccionCliente principal){return new ClienteDto(c.getId(),c.getCodigo(),c.getTipoPersona(),
        c.getNombres(),c.getApellidos(),c.getRazonSocial(),c.getNombreComercial(),c.getTipoIdentificacion(),c.getIdentificacion(),
        formatPhone(c.getTelefono()),formatPhone(c.getTelefonoAlternativo()),c.getCorreo(),c.getCondicionPagoId(),
        c.getCondicionPago()==null?null:paymentName(c.getCondicionPago()),c.getCondicionPago()==null?null:c.getCondicionPago().getTipo(),
        c.getMonedaId(),c.getMoneda()==null?null:c.getMoneda().getCodigoIso(),c.getMoneda()==null?null:c.getMoneda().getSimbolo(),
        c.getDiasCredito(),c.getLimiteCredito(),c.getControlLimite(),c.getTipoComprobantePreferidoId(),
        c.getTipoComprobantePreferido()==null?null:c.getTipoComprobantePreferido().getNombre(),c.getTratamientoFiscal(),
        c.getObservacion(),c.isActivo(),c.getVersion(),principal==null?null:toDto(principal));}
    public Cliente toEntity(ClienteInput i,UUID tenant,UUID empresa,String codigo,String identificacion,String normalizada,
            String telefono,String alternativo,String correo,int dias,java.math.BigDecimal limite,ControlLimiteCredito control,UUID usuario){
        return new Cliente(tenant,empresa,codigo,i.tipoPersona(),i.nombres(),i.apellidos(),i.razonSocial(),i.nombreComercial(),
            i.tipoIdentificacion(),identificacion,normalizada,telefono,alternativo,correo,i.condicionPagoId(),i.monedaId(),dias,
            limite,control,i.tipoComprobantePreferidoId(),i.tratamientoFiscal(),i.observacion(),usuario);}
    public DireccionClienteDto toDto(DireccionCliente d){return new DireccionClienteDto(d.getId(),d.getTipo(),d.getPais(),d.getProvincia(),
        d.getCiudad(),d.getSector(),d.getDireccion(),d.getCodigoPostal(),d.isPrincipal(),d.isActivo(),d.getVersion());}
    public ContactoClienteDto toDto(ContactoCliente c){return new ContactoClienteDto(c.getId(),c.getNombre(),c.getCargo(),
        formatPhone(c.getTelefono()),c.getCorreo(),c.isPrincipal(),c.isActivo(),c.getVersion());}
    public static String formatPhone(String value){if(value==null||value.isBlank())return null;String digits=value.replaceAll("\\D","");
        if(digits.length()==10&&!value.startsWith("+"))return digits.substring(0,3)+"-"+digits.substring(3,6)+"-"+digits.substring(6);return value;}
    private static String paymentName(CondicionPago c){String n=c.getNombre().trim();return c.getTipo()==TipoCondicionPago.CREDIT
        &&java.util.Set.of("fiao","fíao","fia","fiado").contains(n.toLowerCase(java.util.Locale.ROOT))?"Crédito":n;}
}
