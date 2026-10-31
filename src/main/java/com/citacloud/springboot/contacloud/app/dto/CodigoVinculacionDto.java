package com.citacloud.springboot.contacloud.app.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CodigoVinculacionDto(UUID agenteId,String codigo,OffsetDateTime expiraEn){}
