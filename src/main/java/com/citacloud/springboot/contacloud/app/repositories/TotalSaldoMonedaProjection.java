package com.citacloud.springboot.contacloud.app.repositories;
import java.math.BigDecimal;import java.util.UUID;
public interface TotalSaldoMonedaProjection {UUID getMonedaId();String getMoneda();BigDecimal getTotal();}
