package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;
public record ImpuestoFiscalInput(String nombre,BigDecimal tasa,String tipo,String descripcion,boolean activo,Long version){
    public ImpuestoFiscalInput(String nombre,BigDecimal tasa,String descripcion){this(nombre,tasa,"PERCENTAGE",descripcion,true,null);}
}
