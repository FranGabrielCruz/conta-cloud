package com.citacloud.springboot.contacloud.app.dto;
import java.util.Set;
public record NuevaEmpresaDto(String codigo,String nombreComercial,String razonSocial,String identificacionFiscal,String telefono,String correo,String direccion,Set<String> moduleKeys){}
