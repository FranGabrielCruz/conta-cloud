package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record SecuenciaFiscalInput(UUID comprobanteId,long numeroInicial,Long numeroFinal){}
