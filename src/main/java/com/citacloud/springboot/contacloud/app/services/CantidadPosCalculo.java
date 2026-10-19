package com.citacloud.springboot.contacloud.app.services;

import java.math.BigDecimal;

public final class CantidadPosCalculo {
    private CantidadPosCalculo() {}

    public static BigDecimal incrementar(BigDecimal actual, BigDecimal paso) {
        validar(actual, paso);
        return actual.add(paso);
    }

    public static BigDecimal disminuir(BigDecimal actual, BigDecimal paso) {
        validar(actual, paso);
        BigDecimal resultado = actual.subtract(paso);
        return resultado.signum() > 0 ? resultado : actual;
    }

    public static boolean valida(BigDecimal cantidad) {
        return cantidad != null && cantidad.compareTo(BigDecimal.ONE) >= 0;
    }

    private static void validar(BigDecimal actual, BigDecimal paso) {
        if (!valida(actual) || paso == null || paso.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser al menos uno y el paso debe ser mayor que cero.");
        }
    }
}
