package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record TrabajoImpresionDto(UUID id,UUID claimId,byte[] pdf){}
