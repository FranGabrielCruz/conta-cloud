package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoDireccionCliente;
import java.util.UUID;

public record DireccionClienteDto(UUID id,TipoDireccionCliente tipo,String pais,String provincia,String ciudad,String sector,
    String direccion,String codigoPostal,boolean principal,boolean activo,long version) {}
