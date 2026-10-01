package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record CuentaBancariaConciliacionDto(UUID id,String etiqueta,UUID monedaId,String monedaCodigo,short decimales) {}
