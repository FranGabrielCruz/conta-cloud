package com.citacloud.springboot.contacloud.app.dto;
import java.math.BigDecimal;import java.util.UUID;
public record ImpuestoFiscalDto(UUID id,String nombre,BigDecimal tasa,String tipo,String descripcion,boolean activo,long version){
    public ImpuestoFiscalDto(UUID id,String nombre,BigDecimal tasa,String tipo,String descripcion,boolean activo){this(id,nombre,tasa,tipo,descripcion,activo,0L);}
}
