package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record SesionCajaDto(UUID id,UUID cajaId,String cajaNombre,UUID sucursalId,String sucursalNombre,
    String monedaCodigo,LocalDate fechaOperativa,int numeroTurno,String codigoVisible,OffsetDateTime abiertoEn,
    String cajeroNombre,BigDecimal fondoInicial,String observacionApertura,OffsetDateTime cerradoEn,
    String cerradoPorNombre,BigDecimal efectivoEsperado,BigDecimal efectivoContado,BigDecimal diferencia,
    String observacionCierre,EstadoSesionCaja estado,EstadoRevisionCaja estadoRevision,OffsetDateTime revisadoEn,
    String revisadoPorNombre,String observacionRevision){}
