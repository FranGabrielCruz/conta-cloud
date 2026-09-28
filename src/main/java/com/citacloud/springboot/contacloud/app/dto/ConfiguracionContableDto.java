package com.citacloud.springboot.contacloud.app.dto;

import java.util.UUID;

public record ConfiguracionContableDto(
    UUID id,
    String metodoContable,
    boolean contabilizacionAutomatica,
    boolean permitirPeriodosCerrados,
    boolean catalogoCuentasDisponible
) {}
