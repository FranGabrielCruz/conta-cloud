package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record EmpresaResumenDto(UUID id,String codigo,String nombre,String identificacionFiscal,long modulos,String alojamiento,boolean activo){}
