package com.citacloud.springboot.contacloud.app.services;
import java.math.*;
import java.util.*;
public final class CobroVentaCalculo {
    private CobroVentaCalculo(){}
    public static BigDecimal totalAplicado(Collection<BigDecimal> montos){return montos.stream().filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO,BigDecimal::add).setScale(4,RoundingMode.HALF_UP);}
    public static BigDecimal pendiente(BigDecimal total,BigDecimal aplicado){return total.subtract(aplicado).setScale(4,RoundingMode.HALF_UP);}
    public static BigDecimal cambio(BigDecimal aplicado,BigDecimal recibido){return recibido.subtract(aplicado).setScale(4,RoundingMode.HALF_UP);}
    public static BigDecimal cambioNoNegativo(BigDecimal aplicado, BigDecimal recibido) {
        return cambio(aplicado, recibido).max(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
    }
    public static boolean mixto(int aplicaciones){return aplicaciones>1;}
}
