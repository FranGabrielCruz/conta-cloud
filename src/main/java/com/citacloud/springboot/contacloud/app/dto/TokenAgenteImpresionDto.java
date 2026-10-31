package com.citacloud.springboot.contacloud.app.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TokenAgenteImpresionDto(UUID agentId,String displayCode,String tokenType,String accessToken,
    OffsetDateTime accessExpiresAt,String refreshToken,OffsetDateTime refreshExpiresAt){}
