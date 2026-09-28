package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record ComprobanteFiscalDto(UUID id,String codigo,String nombre,String prefijo,String descripcion,boolean activo){}
