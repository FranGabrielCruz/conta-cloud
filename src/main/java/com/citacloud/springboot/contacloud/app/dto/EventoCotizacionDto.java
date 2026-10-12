package com.citacloud.springboot.contacloud.app.dto;

import java.time.OffsetDateTime;

public record EventoCotizacionDto(String tipo,String detalle,OffsetDateTime ocurridoEn) {}
