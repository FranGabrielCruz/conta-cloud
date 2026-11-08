package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record AnularNotaCreditoVentaInput(UUID notaId,long version,String motivo,UUID idempotencia){}
