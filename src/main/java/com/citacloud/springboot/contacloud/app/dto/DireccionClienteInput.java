package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoDireccionCliente;

public record DireccionClienteInput(TipoDireccionCliente tipo,String pais,String provincia,String ciudad,String sector,
    String direccion,String codigoPostal,boolean principal,long version) {}
