package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record SecuenciaFiscalDto(UUID id,UUID comprobanteId,String comprobante,long numeroInicial,long numeroActual,long siguiente,Long numeroFinal,String estado){}
