package com.citacloud.springboot.contacloud.app.dto;

public record ConfiguracionContableInput(
    String metodoContable,
    boolean contabilizacionAutomatica,
    boolean permitirPeriodosCerrados
) {}
