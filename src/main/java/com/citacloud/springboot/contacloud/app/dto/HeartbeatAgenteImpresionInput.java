package com.citacloud.springboot.contacloud.app.dto;

import jakarta.validation.constraints.Size;
public record HeartbeatAgenteImpresionInput(@Size(max=60) String applicationVersion){}
