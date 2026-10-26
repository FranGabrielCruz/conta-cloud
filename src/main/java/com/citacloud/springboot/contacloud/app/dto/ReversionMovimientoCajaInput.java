package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record ReversionMovimientoCajaInput(UUID sesionId,UUID movimientoId,String motivo,UUID idempotencia){}
