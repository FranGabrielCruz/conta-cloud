package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record AplicacionCobroInput(UUID metodoPagoId,BigDecimal monto,BigDecimal recibido,UUID cuentaBancariaId,
        String referencia,String banco){}
