package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ClienteDto(UUID id,String codigo,TipoPersonaCliente tipoPersona,String nombres,String apellidos,
    String razonSocial,String nombreComercial,TipoIdentificacionCliente tipoIdentificacion,String identificacion,
    String telefono,String telefonoAlternativo,String correo,UUID condicionPagoId,String condicionPagoNombre,
    TipoCondicionPago tipoCondicionPago,UUID monedaId,String monedaCodigo,String monedaSimbolo,Integer diasCredito,
    BigDecimal limiteCredito,ControlLimiteCredito controlLimite,UUID tipoComprobantePreferidoId,
    String tipoComprobantePreferido,String tratamientoFiscal,String observacion,boolean activo,long version,
    DireccionClienteDto direccionPrincipal) {
    public String nombreVisible(){return tipoPersona==TipoPersonaCliente.FISICA
        ?String.join(" ",nombres==null?"":nombres,apellidos==null?"":apellidos).trim()
        :nombreComercial!=null&&!nombreComercial.isBlank()?nombreComercial:razonSocial;}
}
