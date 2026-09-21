package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record OpcionSeguridadDto(UUID id,String nombre,boolean activo) { @Override public String toString(){ return nombre+(activo?"":" (Inactiva)"); } }
