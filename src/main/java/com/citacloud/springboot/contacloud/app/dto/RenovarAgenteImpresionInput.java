package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.NotBlank;
public record RenovarAgenteImpresionInput(@NotBlank String refreshToken){}
