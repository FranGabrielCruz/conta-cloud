package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
import java.time.LocalDate;
public record TerminalPosDto(UUID id,String nombre,UUID sucursalId,String sucursal,UUID cajaId,String caja,
                             String monedaCodigo,boolean cajaAbierta,UUID sesionCajaId,LocalDate fechaOperativa){}
