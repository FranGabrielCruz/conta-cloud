package com.citacloud.springboot.contacloud.app.repositories;

import java.math.BigDecimal;
import java.util.UUID;

public interface SaldoCuentaProjection {
    UUID getCuentaId();String getCuenta();UUID getMonedaId();String getMoneda();String getSimbolo();
    UUID getSucursalId();String getSucursal();Boolean getActivo();BigDecimal getSaldoInicial();
    BigDecimal getEntradas();BigDecimal getSalidas();BigDecimal getSaldoActual();
}
