package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
import java.time.LocalDateTime;
public record NotaCreditoVentaCicloDto(UUID facturaId,UUID devolucionId,String estadoOrigen,long version,String estado,
        boolean anulable,String motivo,String anuladaPor,LocalDateTime anuladaEn){}
