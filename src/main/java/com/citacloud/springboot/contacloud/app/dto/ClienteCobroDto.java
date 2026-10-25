package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record ClienteCobroDto(UUID id,String codigo,String nombre,String identificacion,UUID monedaId) {}
