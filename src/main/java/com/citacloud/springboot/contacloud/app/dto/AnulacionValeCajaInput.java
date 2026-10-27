package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record AnulacionValeCajaInput(UUID valeId,String motivo,UUID idempotencia){}
