package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.EstadoSesionCaja;
import java.util.UUID;
public record OperacionCajaDto(UUID cajaId,String cajaNombre,UUID sucursalId,String sucursalNombre,
    String monedaCodigo,boolean activa,EstadoSesionCaja estadoOperativo,UUID sesionId,String turnoCodigo){}
