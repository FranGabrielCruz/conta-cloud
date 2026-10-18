package com.citacloud.springboot.contacloud.app.services;
import java.math.*;
public final class VentaCalculo {
    private static final int SCALE=4;private VentaCalculo(){}
    public static Resultado linea(BigDecimal precio,BigDecimal cantidad,BigDecimal tasa){
        if(precio==null||precio.signum()<0)throw new IllegalArgumentException("Precio inválido.");
        if(cantidad==null||cantidad.signum()<=0)throw new IllegalArgumentException("Cantidad inválida.");
        BigDecimal rate=tasa==null?BigDecimal.ZERO:tasa;if(rate.signum()<0)throw new IllegalArgumentException("Impuesto inválido.");
        BigDecimal base=precio.setScale(SCALE,RoundingMode.HALF_UP).multiply(cantidad.setScale(SCALE,RoundingMode.HALF_UP)).setScale(SCALE,RoundingMode.HALF_UP);
        BigDecimal tax=base.multiply(rate).divide(new BigDecimal("100"),SCALE,RoundingMode.HALF_UP);
        return new Resultado(base,tax,base.add(tax).setScale(SCALE,RoundingMode.HALF_UP));
    }
    public static BigDecimal cambio(BigDecimal total,BigDecimal recibido){
        if(total==null||recibido==null||recibido.compareTo(total)<0)throw new IllegalArgumentException("El efectivo recibido no cubre el total.");
        return recibido.subtract(total).setScale(SCALE,RoundingMode.HALF_UP);
    }
    public record Resultado(BigDecimal subtotal,BigDecimal impuesto,BigDecimal total){}
}
