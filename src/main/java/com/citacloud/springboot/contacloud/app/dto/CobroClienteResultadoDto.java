package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CobroClienteResultadoDto(UUID id,String numero,BigDecimal recibido,BigDecimal aplicado,BigDecimal disponible) {}
