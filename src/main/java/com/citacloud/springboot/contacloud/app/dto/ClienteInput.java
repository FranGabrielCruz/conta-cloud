package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ClienteInput(TipoPersonaCliente tipoPersona,String nombres,String apellidos,String razonSocial,
    String nombreComercial,TipoIdentificacionCliente tipoIdentificacion,String identificacion,String telefono,
    String telefonoAlternativo,String correo,UUID condicionPagoId,UUID monedaId,Integer diasCredito,
    BigDecimal limiteCredito,ControlLimiteCredito controlLimite,UUID tipoComprobantePreferidoId,
    String tratamientoFiscal,String observacion,DireccionClienteInput direccionPrincipal,long version) {}
