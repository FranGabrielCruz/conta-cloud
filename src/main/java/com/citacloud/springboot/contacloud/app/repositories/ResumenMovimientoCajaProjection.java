package com.citacloud.springboot.contacloud.app.repositories;
import java.math.BigDecimal;
public interface ResumenMovimientoCajaProjection {
    String getOrigen();
    BigDecimal getEntradas();
    BigDecimal getSalidas();
}
