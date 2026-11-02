package com.citacloud.springboot.contacloud.app.repositories;
import java.math.BigDecimal;
public interface FormaPagoSesionProjection {
    String getCodigo();
    String getNombre();
    BigDecimal getImporte();
}
